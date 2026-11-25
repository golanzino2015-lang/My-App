// Tiny AI engine. A GPT-style transformer with hand-written training (backpropagation + AdamW).
// Training is data-parallel: every thread learns from its own sequences, then gradients are summed.
#include "tinyai.h"

#include <math.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <unistd.h>

enum { LN1W, LN1B, QKVW, QKVB, PROJW, PROJB, LN2W, LN2B, FCW, FCB, FCPROJW, FCPROJB, NLT };
enum { PH_TRAIN = 1, PH_REDUCE, PH_ADAM };

typedef struct { long off, size; int decay; float init; } Tensor;

typedef struct {
    float *ln1, *ln1m, *ln1r, *qkv, *att, *atty, *attproj, *res2, *ln2, *ln2m, *ln2r, *fch, *fchg, *fcproj, *res3;
} LayerActs;

typedef struct {
    float *mem;
    long size;
    float *encoded, *lnf, *lnfm, *lnfr;
    LayerActs *layers;
} Acts;

typedef struct Worker {
    struct Model *m;
    int id;
    pthread_t th;
    Acts a, d;
    float *probs, *grads, *datt;
    int32_t *inp, *tgt;
    uint64_t rng;
    double loss;
    double norm;
} Worker;

struct Model {
    Config cfg;
    int nt;
    Tensor *t;
    long nparams;
    float *params, *grads, *mo, *vo;
    long step;

    int nthreads;
    Worker *w;
    pthread_mutex_t mu;
    pthread_cond_t go, done_cv;
    int gen, done, quit, phase;
    atomic_int next;
    int nseq;
    float lr, wd, clip_scale;

    const int32_t *stream;
    long nstream;
    const int32_t *starts;
    long nstarts;

    Acts ga;           // buffers for chatting, separate from training
    float *gprobs;
    pthread_mutex_t gen_mu;
};

/* ------------------------------------------------------------------ helpers */

static uint64_t rng_next(uint64_t *s) {
    uint64_t x = *s;
    x ^= x >> 12; x ^= x << 25; x ^= x >> 27;
    *s = x;
    return x * 0x2545F4914F6CDD1DULL;
}
static float rng_unif(uint64_t *s) { return (rng_next(s) >> 40) * (1.0f / 16777216.0f); }
static float rng_norm(uint64_t *s) {
    float u = 0;
    while (u == 0) u = rng_unif(s);
    return sqrtf(-2.0f * logf(u)) * cosf(6.2831853f * rng_unif(s));
}

static inline float *PT(const Model *m, float *base, int idx) { return base + m->t[idx].off; }
static inline int LI(int l, int k) { return 2 + l * NLT + k; }
#define WTE 0
#define WPE 1
#define LNFW(m) (2 + (m)->cfg.L * NLT)
#define LNFB(m) (3 + (m)->cfg.L * NLT)

static void build_tensors(Model *m) {
    Config c = m->cfg;
    m->nt = 4 + c.L * NLT;
    m->t = calloc(m->nt, sizeof(Tensor));
    float proj = 0.02f / sqrtf(2.0f * c.L);
    long sz[NLT] = {c.C, c.C, 3L * c.C * c.C, 3L * c.C, (long) c.C * c.C, c.C, c.C, c.C, 4L * c.C * c.C, 4L * c.C, 4L * c.C * c.C, c.C};
    int dec[NLT] = {0, 0, 1, 0, 1, 0, 0, 0, 1, 0, 1, 0};
    float ini[NLT] = {-1, 0, 0.02f, 0, proj, 0, -1, 0, 0.02f, 0, proj, 0};
    m->t[WTE] = (Tensor){0, (long) c.V * c.C, 1, 0.02f};
    m->t[WPE] = (Tensor){0, (long) c.T * c.C, 0, 0.01f};
    for (int l = 0; l < c.L; l++)
        for (int k = 0; k < NLT; k++) m->t[LI(l, k)] = (Tensor){0, sz[k], dec[k], ini[k]};
    m->t[LNFW(m)] = (Tensor){0, c.C, 0, -1};
    m->t[LNFB(m)] = (Tensor){0, c.C, 0, 0};
    long off = 0;
    for (int i = 0; i < m->nt; i++) { m->t[i].off = off; off += m->t[i].size; }
    m->nparams = off;
}

static int acts_alloc(Acts *a, Config c) {
    long N = c.T, C = c.C;
    // per layer: 10*N*C plain buffers, 8*N*C for the MLP, 4*N norm stats, H*N*N attention
    long per_layer = N * C * 18 + 4 * N + (long) c.H * N * N;
    a->size = N * C * 2 + 2 * N + per_layer * c.L;
    a->mem = calloc(a->size, sizeof(float));
    a->layers = calloc(c.L, sizeof(LayerActs));
    if (!a->mem || !a->layers) return 0;
    float *p = a->mem;
#define TAKE(n) (p += (n), p - (n))
    a->encoded = TAKE(N * C);
    for (int l = 0; l < c.L; l++) {
        LayerActs *L = &a->layers[l];
        L->ln1 = TAKE(N * C); L->ln1m = TAKE(N); L->ln1r = TAKE(N); L->qkv = TAKE(3 * N * C);
        L->att = TAKE((long) c.H * N * N); L->atty = TAKE(N * C); L->attproj = TAKE(N * C); L->res2 = TAKE(N * C);
        L->ln2 = TAKE(N * C); L->ln2m = TAKE(N); L->ln2r = TAKE(N); L->fch = TAKE(4 * N * C);
        L->fchg = TAKE(4 * N * C); L->fcproj = TAKE(N * C); L->res3 = TAKE(N * C);
    }
    a->lnf = TAKE(N * C);
#undef TAKE
    // lnf stats live after the main block
    a->lnfm = calloc(N, sizeof(float));
    a->lnfr = calloc(N, sizeof(float));
    return a->lnfm && a->lnfr;
}
static void acts_free(Acts *a) { free(a->mem); free(a->layers); free(a->lnfm); free(a->lnfr); memset(a, 0, sizeof(*a)); }

/* ------------------------------------------------------------------ layers */

static void encoder_fwd(float *out, const int32_t *inp, const float *wte, const float *wpe, int T, int C) {
    for (int t = 0; t < T; t++) {
        const float *e = wte + (long) inp[t] * C, *p = wpe + (long) t * C;
        float *o = out + (long) t * C;
        for (int i = 0; i < C; i++) o[i] = e[i] + p[i];
    }
}
static void encoder_bwd(float *dwte, float *dwpe, const float *dout, const int32_t *inp, int T, int C) {
    for (int t = 0; t < T; t++) {
        float *e = dwte + (long) inp[t] * C, *p = dwpe + (long) t * C;
        const float *d = dout + (long) t * C;
        for (int i = 0; i < C; i++) { e[i] += d[i]; p[i] += d[i]; }
    }
}
static void ln_fwd(float *out, float *mean, float *rstd, const float *inp, const float *w, const float *b, int N, int C) {
    for (int n = 0; n < N; n++) {
        const float *x = inp + (long) n * C;
        float m = 0, v = 0;
        for (int i = 0; i < C; i++) m += x[i];
        m /= C;
        for (int i = 0; i < C; i++) { float d = x[i] - m; v += d * d; }
        v /= C;
        float s = 1.0f / sqrtf(v + 1e-5f);
        float *o = out + (long) n * C;
        for (int i = 0; i < C; i++) o[i] = (x[i] - m) * s * w[i] + b[i];
        mean[n] = m; rstd[n] = s;
    }
}
static void ln_bwd(float *dinp, float *dw, float *db, const float *dout, const float *inp, const float *w,
                   const float *mean, const float *rstd, int N, int C) {
    for (int n = 0; n < N; n++) {
        const float *x = inp + (long) n * C, *d = dout + (long) n * C;
        float *dx = dinp + (long) n * C;
        float m = mean[n], s = rstd[n], dm = 0, dnm = 0;
        for (int i = 0; i < C; i++) {
            float norm = (x[i] - m) * s, dn = w[i] * d[i];
            dm += dn; dnm += dn * norm;
        }
        dm /= C; dnm /= C;
        for (int i = 0; i < C; i++) {
            float norm = (x[i] - m) * s, dn = w[i] * d[i];
            db[i] += d[i]; dw[i] += norm * d[i];
            dx[i] += (dn - dm - norm * dnm) * s;
        }
    }
}
// out[N,OC] = inp[N,C] * w[OC,C]^T + bias. Four rows at a time so each weight row is read once per block.
static void mm_fwd(float *out, const float *inp, const float *w, const float *bias, int N, int C, int OC) {
    int n = 0;
    for (; n + 4 <= N; n += 4) {
        const float *x0 = inp + (long) n * C, *x1 = x0 + C, *x2 = x1 + C, *x3 = x2 + C;
        for (int o = 0; o < OC; o++) {
            const float *wr = w + (long) o * C;
            float s0 = 0, s1 = 0, s2 = 0, s3 = 0;
            for (int i = 0; i < C; i++) {
                float wi = wr[i];
                s0 += x0[i] * wi; s1 += x1[i] * wi; s2 += x2[i] * wi; s3 += x3[i] * wi;
            }
            float b = bias ? bias[o] : 0;
            out[(long) n * OC + o] = s0 + b; out[(long) (n + 1) * OC + o] = s1 + b;
            out[(long) (n + 2) * OC + o] = s2 + b; out[(long) (n + 3) * OC + o] = s3 + b;
        }
    }
    for (; n < N; n++) {
        const float *x = inp + (long) n * C;
        for (int o = 0; o < OC; o++) {
            const float *wr = w + (long) o * C;
            float s = bias ? bias[o] : 0;
            for (int i = 0; i < C; i++) s += x[i] * wr[i];
            out[(long) n * OC + o] = s;
        }
    }
}
static void mm_bwd(float *dinp, float *dw, float *db, const float *dout, const float *inp, const float *w,
                   int N, int C, int OC) {
    for (int n = 0; n < N; n++) {
        const float *d = dout + (long) n * OC;
        float *dx = dinp + (long) n * C;
        for (int o = 0; o < OC; o++) {
            float g = d[o];
            if (g == 0) continue;
            const float *wr = w + (long) o * C;
            for (int i = 0; i < C; i++) dx[i] += g * wr[i];
        }
    }
    for (int o = 0; o < OC; o++) {
        float *dwr = dw + (long) o * C, bsum = 0;
        for (int n = 0; n < N; n++) {
            float g = dout[(long) n * OC + o];
            if (g == 0) continue;
            bsum += g;
            const float *x = inp + (long) n * C;
            for (int i = 0; i < C; i++) dwr[i] += g * x[i];
        }
        if (db) db[o] += bsum;
    }
}
static void attn_fwd(float *out, float *att, const float *qkv, int T, int C, int H) {
    int C3 = 3 * C, hs = C / H;
    float scale = 1.0f / sqrtf((float) hs);
    for (int t = 0; t < T; t++)
        for (int h = 0; h < H; h++) {
            const float *q = qkv + (long) t * C3 + h * hs;
            float *a = att + ((long) h * T + t) * T, mx = -1e30f;
            for (int t2 = 0; t2 <= t; t2++) {
                const float *k = qkv + (long) t2 * C3 + h * hs + C;
                float s = 0;
                for (int i = 0; i < hs; i++) s += q[i] * k[i];
                s *= scale; a[t2] = s;
                if (s > mx) mx = s;
            }
            float sum = 0;
            for (int t2 = 0; t2 <= t; t2++) { float e = expf(a[t2] - mx); a[t2] = e; sum += e; }
            float inv = 1.0f / sum;
            for (int t2 = 0; t2 <= t; t2++) a[t2] *= inv;
            for (int t2 = t + 1; t2 < T; t2++) a[t2] = 0;
            float *o = out + (long) t * C + h * hs;
            for (int i = 0; i < hs; i++) o[i] = 0;
            for (int t2 = 0; t2 <= t; t2++) {
                const float *v = qkv + (long) t2 * C3 + h * hs + 2 * C;
                float p = a[t2];
                for (int i = 0; i < hs; i++) o[i] += p * v[i];
            }
        }
}
static void attn_bwd(float *dqkv, float *datt, const float *dout, const float *qkv, const float *att, int T, int C, int H) {
    int C3 = 3 * C, hs = C / H;
    float scale = 1.0f / sqrtf((float) hs);
    for (int t = 0; t < T; t++)
        for (int h = 0; h < H; h++) {
            const float *q = qkv + (long) t * C3 + h * hs, *a = att + ((long) h * T + t) * T;
            const float *d = dout + (long) t * C + h * hs;
            float *dq = dqkv + (long) t * C3 + h * hs, dot = 0;
            for (int t2 = 0; t2 <= t; t2++) {
                const float *v = qkv + (long) t2 * C3 + h * hs + 2 * C;
                float *dv = dqkv + (long) t2 * C3 + h * hs + 2 * C, p = a[t2], s = 0;
                for (int i = 0; i < hs; i++) { s += v[i] * d[i]; dv[i] += p * d[i]; }
                datt[t2] = s; dot += p * s;
            }
            for (int t2 = 0; t2 <= t; t2++) {
                float g = a[t2] * (datt[t2] - dot) * scale;
                if (g == 0) continue;
                const float *k = qkv + (long) t2 * C3 + h * hs + C;
                float *dk = dqkv + (long) t2 * C3 + h * hs + C;
                for (int i = 0; i < hs; i++) { dq[i] += g * k[i]; dk[i] += g * q[i]; }
            }
        }
}
#define GS 0.7978845608f
static void gelu_fwd(float *out, const float *x, long n) {
    for (long i = 0; i < n; i++) out[i] = 0.5f * x[i] * (1.0f + tanhf(GS * (x[i] + 0.044715f * x[i] * x[i] * x[i])));
}
static void gelu_bwd(float *dx, const float *x, const float *d, long n) {
    for (long i = 0; i < n; i++) {
        float xi = x[i], th = tanhf(GS * (xi + 0.044715f * xi * xi * xi));
        dx[i] += (0.5f * (1 + th) + 0.5f * xi * (1 - th * th) * GS * (1 + 3 * 0.044715f * xi * xi)) * d[i];
    }
}
static void vadd(float *o, const float *a, const float *b, long n) { for (long i = 0; i < n; i++) o[i] = a[i] + b[i]; }
static void vacc(float *o, const float *a, long n) { for (long i = 0; i < n; i++) o[i] += a[i]; }

/* ------------------------------------------------------------------ model passes */

// Runs the network on T tokens. If probs_out is set, writes softmax for every position and returns
// the mean loss against tgt. If last_logits is set, writes only the last position's logits.
static float forward(Model *m, const float *P, Acts *a, const int32_t *inp, const int32_t *tgt, int T,
                     float *probs, float *last_logits) {
    Config c = m->cfg;
    int C = c.C, V = c.V;
    encoder_fwd(a->encoded, inp, PT(m, (float *) P, WTE), PT(m, (float *) P, WPE), T, C);
    float *res = a->encoded;
    for (int l = 0; l < c.L; l++) {
        LayerActs *A = &a->layers[l];
#define W(k) PT(m, (float *) P, LI(l, k))
        ln_fwd(A->ln1, A->ln1m, A->ln1r, res, W(LN1W), W(LN1B), T, C);
        mm_fwd(A->qkv, A->ln1, W(QKVW), W(QKVB), T, C, 3 * C);
        attn_fwd(A->atty, A->att, A->qkv, T, C, c.H);
        mm_fwd(A->attproj, A->atty, W(PROJW), W(PROJB), T, C, C);
        vadd(A->res2, res, A->attproj, (long) T * C);
        ln_fwd(A->ln2, A->ln2m, A->ln2r, A->res2, W(LN2W), W(LN2B), T, C);
        mm_fwd(A->fch, A->ln2, W(FCW), W(FCB), T, C, 4 * C);
        gelu_fwd(A->fchg, A->fch, (long) T * 4 * C);
        mm_fwd(A->fcproj, A->fchg, W(FCPROJW), W(FCPROJB), T, 4 * C, C);
        vadd(A->res3, A->res2, A->fcproj, (long) T * C);
#undef W
        res = A->res3;
    }
    ln_fwd(a->lnf, a->lnfm, a->lnfr, res, PT(m, (float *) P, LNFW(m)), PT(m, (float *) P, LNFB(m)), T, C);
    if (last_logits) {
        mm_fwd(last_logits, a->lnf + (long) (T - 1) * C, PT(m, (float *) P, WTE), NULL, 1, C, V);
        return 0;
    }
    mm_fwd(probs, a->lnf, PT(m, (float *) P, WTE), NULL, T, C, V);
    double loss = 0;
    for (int n = 0; n < T; n++) {
        float *row = probs + (long) n * V, mx = -1e30f, sum = 0;
        for (int v = 0; v < V; v++) if (row[v] > mx) mx = row[v];
        for (int v = 0; v < V; v++) { row[v] = expf(row[v] - mx); sum += row[v]; }
        float inv = 1.0f / sum;
        for (int v = 0; v < V; v++) row[v] *= inv;
        if (tgt) loss -= logf(row[tgt[n]] + 1e-12f);
    }
    return (float) (loss / T);
}

static void backward(Model *m, const float *P, float *G, Acts *a, Acts *d, float *probs, float *datt,
                     const int32_t *inp, const int32_t *tgt, int T, float scale) {
    Config c = m->cfg;
    int C = c.C, V = c.V, L = c.L;
    memset(d->mem, 0, d->size * sizeof(float));
    float k = scale / T;
    for (int n = 0; n < T; n++) {
        float *row = probs + (long) n * V;
        for (int v = 0; v < V; v++) row[v] *= k;
        row[tgt[n]] -= k;
    }
    mm_bwd(d->lnf, PT(m, G, WTE), NULL, probs, a->lnf, PT(m, (float *) P, WTE), T, C, V);
    float *last = L ? a->layers[L - 1].res3 : a->encoded, *dlast = L ? d->layers[L - 1].res3 : d->encoded;
    ln_bwd(dlast, PT(m, G, LNFW(m)), PT(m, G, LNFB(m)), d->lnf, last, PT(m, (float *) P, LNFW(m)), a->lnfm, a->lnfr, T, C);
    for (int l = L - 1; l >= 0; l--) {
        LayerActs *A = &a->layers[l], *D = &d->layers[l];
        float *res = l ? a->layers[l - 1].res3 : a->encoded, *dres = l ? d->layers[l - 1].res3 : d->encoded;
#define W(k) PT(m, (float *) P, LI(l, k))
#define GW(k) PT(m, G, LI(l, k))
        long NC = (long) T * C;
        vacc(D->res2, D->res3, NC);
        vacc(D->fcproj, D->res3, NC);
        mm_bwd(D->fchg, GW(FCPROJW), GW(FCPROJB), D->fcproj, A->fchg, W(FCPROJW), T, 4 * C, C);
        gelu_bwd(D->fch, A->fch, D->fchg, 4 * NC);
        mm_bwd(D->ln2, GW(FCW), GW(FCB), D->fch, A->ln2, W(FCW), T, C, 4 * C);
        ln_bwd(D->res2, GW(LN2W), GW(LN2B), D->ln2, A->res2, W(LN2W), A->ln2m, A->ln2r, T, C);
        vacc(dres, D->res2, NC);
        vacc(D->attproj, D->res2, NC);
        mm_bwd(D->atty, GW(PROJW), GW(PROJB), D->attproj, A->atty, W(PROJW), T, C, C);
        attn_bwd(D->qkv, datt, D->atty, A->qkv, A->att, T, C, c.H);
        mm_bwd(D->ln1, GW(QKVW), GW(QKVB), D->qkv, A->ln1, W(QKVW), T, C, 3 * C);
        ln_bwd(dres, GW(LN1W), GW(LN1B), D->ln1, res, W(LN1W), A->ln1m, A->ln1r, T, C);
#undef W
#undef GW
    }
    encoder_bwd(PT(m, G, WTE), PT(m, G, WPE), d->encoded, inp, T, C);
}

/* ------------------------------------------------------------------ threads */

static void range_of(long n, int id, int k, long *lo, long *hi) {
    long chunk = (n + k - 1) / k;
    *lo = chunk * id; *hi = *lo + chunk; if (*hi > n) *hi = n; if (*lo > n) *lo = n;
}

static void do_phase(Worker *w, int phase) {
    Model *m = w->m;
    int T = m->cfg.T;
    if (phase == PH_TRAIN) {
        w->loss = 0;
        int i;
        while ((i = atomic_fetch_add(&m->next, 1)) < m->nseq) {
            long start = m->starts[rng_next(&w->rng) % (uint64_t) m->nstarts];
            if (start + T + 1 > m->nstream) start = m->nstream - T - 1;
            if (start < 0) start = 0;
            memcpy(w->inp, m->stream + start, T * sizeof(int32_t));
            memcpy(w->tgt, m->stream + start + 1, T * sizeof(int32_t));
            w->loss += forward(m, m->params, &w->a, w->inp, w->tgt, T, w->probs, NULL);
            backward(m, m->params, w->grads, &w->a, &w->d, w->probs, w->datt, w->inp, w->tgt, T, 1.0f / m->nseq);
        }
    } else if (phase == PH_REDUCE) {
        long lo, hi;
        range_of(m->nparams, w->id, m->nthreads, &lo, &hi);
        double norm = 0;
        for (long j = lo; j < hi; j++) {
            float s = 0;
            for (int k = 0; k < m->nthreads; k++) { s += m->w[k].grads[j]; m->w[k].grads[j] = 0; }
            m->grads[j] = s;
            norm += (double) s * s;
        }
        w->norm = norm;
    } else if (phase == PH_ADAM) {
        long lo, hi;
        range_of(m->nparams, w->id, m->nthreads, &lo, &hi);
        const float b1 = 0.9f, b2 = 0.99f, eps = 1e-8f;
        float c1 = 1.0f - powf(b1, (float) m->step), c2 = 1.0f - powf(b2, (float) m->step);
        for (int ti = 0; ti < m->nt; ti++) {
            Tensor t = m->t[ti];
            long a = t.off > lo ? t.off : lo, b = t.off + t.size < hi ? t.off + t.size : hi;
            float dec = t.decay ? m->wd : 0;
            for (long j = a; j < b; j++) {
                float g = m->grads[j] * m->clip_scale;
                m->mo[j] = b1 * m->mo[j] + (1 - b1) * g;
                m->vo[j] = b2 * m->vo[j] + (1 - b2) * g * g;
                m->params[j] -= m->lr * ((m->mo[j] / c1) / (sqrtf(m->vo[j] / c2) + eps) + dec * m->params[j]);
            }
        }
    }
}

static void *worker_main(void *arg) {
    Worker *w = arg;
    Model *m = w->m;
    int seen = 0;
    // Low priority: whatever you are doing on the phone always goes first.
    setpriority(PRIO_PROCESS, (id_t) syscall(SYS_gettid), 10);
    for (;;) {
        pthread_mutex_lock(&m->mu);
        while (m->gen == seen && !m->quit) pthread_cond_wait(&m->go, &m->mu);
        if (m->quit) { pthread_mutex_unlock(&m->mu); break; }
        seen = m->gen;
        int phase = m->phase;
        pthread_mutex_unlock(&m->mu);
        do_phase(w, phase);
        pthread_mutex_lock(&m->mu);
        if (++m->done == m->nthreads) pthread_cond_signal(&m->done_cv);
        pthread_mutex_unlock(&m->mu);
    }
    return NULL;
}

static void run_phase(Model *m, int phase) {
    pthread_mutex_lock(&m->mu);
    m->phase = phase; m->done = 0; m->gen++;
    pthread_cond_broadcast(&m->go);
    while (m->done < m->nthreads) pthread_cond_wait(&m->done_cv, &m->mu);
    pthread_mutex_unlock(&m->mu);
}

static int worker_buffers(Model *m, Worker *w) {
    Config c = m->cfg;
    free(w->probs); free(w->grads);
    w->probs = malloc((long) c.T * c.V * sizeof(float));
    w->grads = calloc(m->nparams, sizeof(float));
    return w->probs && w->grads;
}

static int start_threads(Model *m, int threads) {
    if (threads < 1) threads = 1;
    if (threads > 16) threads = 16;
    m->nthreads = threads;
    m->w = calloc(threads, sizeof(Worker));
    pthread_mutex_init(&m->mu, NULL);
    pthread_cond_init(&m->go, NULL);
    pthread_cond_init(&m->done_cv, NULL);
    pthread_mutex_init(&m->gen_mu, NULL);
    for (int i = 0; i < threads; i++) {
        Worker *w = &m->w[i];
        w->m = m; w->id = i; w->rng = 0x9E3779B97F4A7C15ULL * (i + 1) ^ (uint64_t) (m->step + 1);
        if (!acts_alloc(&w->a, m->cfg) || !acts_alloc(&w->d, m->cfg) || !worker_buffers(m, w)) return 0;
        w->datt = malloc(m->cfg.T * sizeof(float));
        w->inp = malloc(m->cfg.T * sizeof(int32_t));
        w->tgt = malloc(m->cfg.T * sizeof(int32_t));
        pthread_create(&w->th, NULL, worker_main, w);
    }
    if (!acts_alloc(&m->ga, m->cfg)) return 0;
    m->gprobs = malloc(m->cfg.V * sizeof(float));
    return m->gprobs != NULL;
}

static Model *alloc_model(Config cfg) {
    Model *m = calloc(1, sizeof(Model));
    m->cfg = cfg;
    build_tensors(m);
    m->params = calloc(m->nparams, sizeof(float));
    m->grads = calloc(m->nparams, sizeof(float));
    m->mo = calloc(m->nparams, sizeof(float));
    m->vo = calloc(m->nparams, sizeof(float));
    if (!m->params || !m->grads || !m->mo || !m->vo) { ta_free(m); return NULL; }
    return m;
}

/* ------------------------------------------------------------------ public API */

Model *ta_create(Config cfg, uint32_t seed, int threads) {
    if (cfg.C % cfg.H) return NULL;
    Model *m = alloc_model(cfg);
    if (!m) return NULL;
    uint64_t r = 0x853C49E6748FEA9BULL ^ ((uint64_t) seed << 1 | 1);
    for (int i = 0; i < m->nt; i++) {
        Tensor t = m->t[i];
        float *p = m->params + t.off;
        if (t.init < 0) for (long j = 0; j < t.size; j++) p[j] = 1;
        else if (t.init > 0) for (long j = 0; j < t.size; j++) p[j] = rng_norm(&r) * t.init;
    }
    if (!start_threads(m, threads)) { ta_free(m); return NULL; }
    return m;
}

void ta_free(Model *m) {
    if (!m) return;
    if (m->w) {
        pthread_mutex_lock(&m->mu);
        m->quit = 1;
        pthread_cond_broadcast(&m->go);
        pthread_mutex_unlock(&m->mu);
        for (int i = 0; i < m->nthreads; i++) {
            Worker *w = &m->w[i];
            if (w->th) pthread_join(w->th, NULL);
            acts_free(&w->a); acts_free(&w->d);
            free(w->probs); free(w->grads); free(w->datt); free(w->inp); free(w->tgt);
        }
        free(m->w);
    }
    acts_free(&m->ga);
    free(m->gprobs); free(m->t); free(m->params); free(m->grads); free(m->mo); free(m->vo);
    free(m);
}

Config ta_config(const Model *m) { return m->cfg; }
long ta_num_params(const Model *m) { return m->nparams; }
long ta_step(const Model *m) { return m->step; }
int ta_threads(const Model *m) { return m->nthreads; }

void ta_set_data(Model *m, const int32_t *stream, long n, const int32_t *starts, long nstarts) {
    m->stream = stream; m->nstream = n; m->starts = starts; m->nstarts = nstarts;
}

float ta_train_step(Model *m, int seqs, float lr, float wd) {
    if (!m->stream || m->nstream <= m->cfg.T + 1 || m->nstarts == 0) return -1;
    atomic_store(&m->next, 0);
    m->nseq = seqs;
    run_phase(m, PH_TRAIN);
    double loss = 0;
    for (int i = 0; i < m->nthreads; i++) loss += m->w[i].loss;
    run_phase(m, PH_REDUCE);
    double norm = 0;
    for (int i = 0; i < m->nthreads; i++) norm += m->w[i].norm;
    norm = sqrt(norm);
    m->clip_scale = norm > 1.0 ? (float) (1.0 / norm) : 1.0f;
    m->lr = lr; m->wd = wd;
    m->step++;
    run_phase(m, PH_ADAM);
    return (float) (loss / seqs);
}

int ta_grow_vocab(Model *m, int newV) {
    if (newV <= m->cfg.V) return 1;
    Config nc = m->cfg;
    nc.V = newV;
    Model *n = alloc_model(nc);
    if (!n) return 0;
    uint64_t r = 0xA5A5A5A5ULL + newV;
    for (int i = 0; i < m->nt; i++) {
        Tensor a = m->t[i], b = n->t[i];
        memcpy(n->params + b.off, m->params + a.off, a.size * sizeof(float));
        memcpy(n->mo + b.off, m->mo + a.off, a.size * sizeof(float));
        memcpy(n->vo + b.off, m->vo + a.off, a.size * sizeof(float));
        for (long j = a.size; j < b.size; j++) n->params[b.off + j] = rng_norm(&r) * 0.02f;
    }
    free(m->t); free(m->params); free(m->grads); free(m->mo); free(m->vo);
    m->cfg = nc; m->t = n->t; m->nt = n->nt; m->nparams = n->nparams;
    m->params = n->params; m->grads = n->grads; m->mo = n->mo; m->vo = n->vo;
    free(n);
    for (int i = 0; i < m->nthreads; i++) if (!worker_buffers(m, &m->w[i])) return 0;
    free(m->gprobs);
    m->gprobs = malloc(nc.V * sizeof(float));
    return m->gprobs != NULL;
}

int ta_generate(Model *m, const int32_t *ctx, int n, int32_t *out, int max_new, int stop_id,
                int vocab_used, const int32_t *banned, int nbanned, float temperature, int top_k,
                uint64_t seed) {
    pthread_mutex_lock(&m->gen_mu);
    int T = m->cfg.T, V = m->cfg.V, len = n < T ? n : T, count = 0;
    int32_t *seq = malloc(T * sizeof(int32_t));
    memcpy(seq, ctx + (n - len), len * sizeof(int32_t));
    uint64_t r = seed | 1;
    if (vocab_used > V || vocab_used <= 0) vocab_used = V;
    if (top_k < 1) top_k = 1;
    int *idx = malloc(top_k * sizeof(int));
    float *val = malloc(top_k * sizeof(float));
    for (; count < max_new && len > 0; count++) {
        float *lg = m->gprobs;
        forward(m, m->params, &m->ga, seq, NULL, len, NULL, lg);
        for (int v = vocab_used; v < V; v++) lg[v] = -1e30f;
        for (int b = 0; b < nbanned; b++) if (banned[b] >= 0 && banned[b] < V) lg[banned[b]] = -1e30f;
        int k = 0;
        for (int v = 0; v < vocab_used; v++) {
            float x = lg[v];
            if (k < top_k) { int j = k++; while (j > 0 && val[j - 1] < x) { val[j] = val[j - 1]; idx[j] = idx[j - 1]; j--; } val[j] = x; idx[j] = v; }
            else if (x > val[k - 1]) { int j = k - 1; while (j > 0 && val[j - 1] < x) { val[j] = val[j - 1]; idx[j] = idx[j - 1]; j--; } val[j] = x; idx[j] = v; }
        }
        int next = idx[0];
        if (temperature > 0 && k > 1) {
            double sum = 0, p[64];
            int kk = k < 64 ? k : 64;
            for (int j = 0; j < kk; j++) { p[j] = exp((val[j] - val[0]) / temperature); sum += p[j]; }
            double x = rng_unif(&r) * sum;
            for (int j = 0; j < kk; j++) { if ((x -= p[j]) <= 0) { next = idx[j]; break; } }
        }
        if (next == stop_id) break;
        out[count] = next;
        if (len == T) { memmove(seq, seq + 1, (T - 1) * sizeof(int32_t)); len--; }
        seq[len++] = next;
    }
    free(seq); free(idx); free(val);
    pthread_mutex_unlock(&m->gen_mu);
    return count;
}

#define MAGIC 0x54414931 /* "TAI1" */
int ta_save(const Model *m, const char *path) {
    char tmp[1024];
    snprintf(tmp, sizeof tmp, "%s.tmp", path);
    FILE *f = fopen(tmp, "wb");
    if (!f) return 0;
    int32_t head[8] = {MAGIC, m->cfg.V, m->cfg.C, m->cfg.L, m->cfg.H, m->cfg.T, 0, 0};
    int64_t step = m->step;
    int ok = fwrite(head, sizeof head, 1, f) == 1 && fwrite(&step, sizeof step, 1, f) == 1
             && fwrite(m->params, sizeof(float), m->nparams, f) == (size_t) m->nparams
             && fwrite(m->mo, sizeof(float), m->nparams, f) == (size_t) m->nparams
             && fwrite(m->vo, sizeof(float), m->nparams, f) == (size_t) m->nparams;
    ok = (fclose(f) == 0) && ok;
    if (!ok) { remove(tmp); return 0; }
    return rename(tmp, path) == 0;
}

Model *ta_load(const char *path, int threads) {
    FILE *f = fopen(path, "rb");
    if (!f) return NULL;
    int32_t head[8];
    int64_t step;
    if (fread(head, sizeof head, 1, f) != 1 || head[0] != MAGIC || fread(&step, sizeof step, 1, f) != 1) { fclose(f); return NULL; }
    Config c = {head[1], head[2], head[3], head[4], head[5]};
    Model *m = alloc_model(c);
    if (!m) { fclose(f); return NULL; }
    int ok = fread(m->params, sizeof(float), m->nparams, f) == (size_t) m->nparams
             && fread(m->mo, sizeof(float), m->nparams, f) == (size_t) m->nparams
             && fread(m->vo, sizeof(float), m->nparams, f) == (size_t) m->nparams;
    fclose(f);
    m->step = step;
    if (!ok || !start_threads(m, threads)) { ta_free(m); return NULL; }
    return m;
}

float ta_debug_loss(Model *m, const int32_t *inp, const int32_t *tgt, int T, int with_backward) {
    Worker *w = &m->w[0];
    float loss = forward(m, m->params, &w->a, inp, tgt, T, w->probs, NULL);
    if (with_backward) {
        memset(m->grads, 0, m->nparams * sizeof(float));
        backward(m, m->params, m->grads, &w->a, &w->d, w->probs, w->datt, inp, tgt, T, 1.0f);
    }
    return loss;
}
float *ta_debug_params(Model *m) { return m->params; }
float *ta_debug_grads(Model *m) { return m->grads; }
