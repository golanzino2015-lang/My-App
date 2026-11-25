/* Tiny AI engine: a GPT-style transformer written from scratch.
 * No libraries. Runs in a Web Worker, on the page's main thread, or in Node.
 * Exposes self.TinyAI = { handle(msg, post), ...internals for tests }.
 */
'use strict';
(function (root) {

/* ======================= Random numbers ======================= */
function mulberry32(a) {
  return function () {
    a |= 0; a = (a + 0x6D2B79F5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
function randn(r) {
  let u = 0; while (u === 0) u = r();
  return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * r());
}

/* ======================= Words <-> numbers ======================= */
const SPECIAL = ['<pad>', '<unk>', '<user>', '<ai>', '<end>'];
const UNK = 1, USER = 2, AI = 3, END = 4;
const UNK_MARK = 'zzunk';

const SHORT = {
  whats: 'what is', 'what\'s': 'what is', whos: 'who is', 'who\'s': 'who is', wheres: 'where is',
  'where\'s': 'where is', hows: 'how is', 'how\'s': 'how is', 'it\'s': 'it is', 'i\'m': 'i am', im: 'i am',
  'you\'re': 'you are', youre: 'you are', ur: 'your', u: 'you', r: 'are', 'don\'t': 'do not', dont: 'do not',
  'doesn\'t': 'does not', doesnt: 'does not', 'can\'t': 'cannot', cant: 'cannot', 'won\'t': 'will not',
  'isn\'t': 'is not', isnt: 'is not', 'aren\'t': 'are not', 'i\'ve': 'i have', 'i\'ll': 'i will',
  'that\'s': 'that is', thats: 'that is', 'there\'s': 'there is', 'let\'s': 'let us', wanna: 'want to',
  gonna: 'going to', gotta: 'got to', thx: 'thanks', ty: 'thank you', pls: 'please', plz: 'please',
  wat: 'what', wut: 'what', sup: 'what is up', wassup: 'what is up', yeah: 'yes', yea: 'yes', yep: 'yes',
  yup: 'yes', nope: 'no', nah: 'no', cuz: 'because', idk: 'i do not know', doin: 'doing', goin: 'going',
  'didn\'t': 'did not', didnt: 'did not', 'wasn\'t': 'was not', 'haven\'t': 'have not', 'couldn\'t': 'could not',
  'what\'re': 'what are', 'how\'re': 'how are', 'you\'ve': 'you have', 'we\'re': 'we are', 'they\'re': 'they are',
  'he\'s': 'he is', 'she\'s': 'she is', ok: 'okay', k: 'okay', kk: 'okay',
};

/** Text -> list of lowercase words and punctuation, with slang and contractions expanded. */
function normalize(text) {
  const s = String(text).toLowerCase().replace(/[’‘`]/g, '\'')
    .replace(/(\d)\s*\+\s*(?=\d)/g, '$1 plus ').replace(/(\d)\s*-\s*(?=\d)/g, '$1 minus ')
    .replace(/<unk>/g, ` ${UNK_MARK} `);
  const out = [];
  for (let w of s.match(/[a-z0-9']+|[.,!?]/g) || []) {
    w = w.replace(/^'+|'+$/g, '');
    if (!w) continue;
    if (/^hi+$/.test(w)) w = 'hi';
    else if (/^hey+$/.test(w)) w = 'hey';
    else if (/^(ha){2,}h?$/.test(w) || /^(he){2,}$/.test(w)) w = 'haha';
    else if (/^hel+o+$/.test(w)) w = 'hello';
    else if (/^no+$/.test(w)) w = 'no';
    else if (/^yes+$/.test(w)) w = 'yes';
    else if (/^(lol)+$/.test(w)) w = 'lol';
    else if (/^okay+$/.test(w)) w = 'okay';
    const exp = SHORT[w];
    if (exp) { out.push(...exp.split(' ')); continue; }
    w = w.replace(/'/g, '');
    if (w) out.push(w);
  }
  return out.filter((w, i) => !(/^[.,!?]$/.test(w) && /^[.,!?]$/.test(out[i - 1] || '')));
}
const isPunct = (w) => /^[.,!?]$/.test(w);
/** What the user typed: words only, punctuation dropped. */
const userWords = (text) => normalize(text).filter((w) => !isPunct(w));

class Vocab {
  constructor(words) { this.words = []; this.map = new Map(); for (const w of words) this.add(w); }
  add(w) { if (!this.map.has(w)) { this.map.set(w, this.words.length); this.words.push(w); } }
  id(w) { return w === UNK_MARK ? UNK : (this.map.has(w) ? this.map.get(w) : UNK); }
  get size() { return this.words.length; }
}

function encodeExchange(vocab, u, a) {
  const ids = [USER];
  for (const w of userWords(u)) ids.push(vocab.id(w));
  ids.push(AI);
  if (a != null) { for (const w of normalize(a)) ids.push(vocab.id(w)); ids.push(END); }
  return ids;
}

let PROPER = [];        // multi-word proper names, filled by the lesson generator
const WORD_FIX = { i: 'I', ai: 'AI', dc: 'DC', tv: 'TV' };
function detokenize(words) {
  let s = '';
  for (const w of words) s += isPunct(w) ? w : (s ? ' ' : '') + w;
  for (const p of PROPER) {
    s = s.replace(new RegExp('\\b' + p + '\\b', 'g'), p.replace(/\b[a-z]/g, (c) => c.toUpperCase()));
  }
  s = s.replace(/\b[a-zA-Z]+\b/g, (w) => WORD_FIX[w.toLowerCase()] || w);
  return s.replace(/(^|[.!?]\s+)([a-z])/g, (m, p, c) => p + c.toUpperCase());
}

/* ======================= The transformer ======================= */
const SIZES = {
  tiny: { C: 48, L: 2, H: 4, T: 64, label: 'Tiny' },
  small: { C: 64, L: 4, H: 4, T: 64, label: 'Small' },
  big: { C: 96, L: 6, H: 6, T: 64, label: 'Big' },
};

function tensorList(cfg) {
  const { V, C, L, T } = cfg, list = [];
  const add = (name, size, decay, init) => list.push({ name, size, decay, init });
  const proj = 0.02 / Math.sqrt(2 * L);
  add('wte', V * C, true, 0.02);
  add('wpe', T * C, false, 0.01);
  for (let l = 0; l < L; l++) {
    add(`${l}.ln1w`, C, false, 'one'); add(`${l}.ln1b`, C, false, 0);
    add(`${l}.qkvw`, 3 * C * C, true, 0.02); add(`${l}.qkvb`, 3 * C, false, 0);
    add(`${l}.projw`, C * C, true, proj); add(`${l}.projb`, C, false, 0);
    add(`${l}.ln2w`, C, false, 'one'); add(`${l}.ln2b`, C, false, 0);
    add(`${l}.fcw`, 4 * C * C, true, 0.02); add(`${l}.fcb`, 4 * C, false, 0);
    add(`${l}.fcprojw`, C * 4 * C, true, proj); add(`${l}.fcprojb`, C, false, 0);
  }
  add('lnfw', C, false, 'one'); add('lnfb', C, false, 0);
  let off = 0;
  for (const t of list) { t.off = off; off += t.size; }
  return { list, total: off };
}

class GPT {
  constructor(cfg, seed = 1) {
    this.cfg = { ...cfg };
    const { list, total } = tensorList(this.cfg);
    this.tensors = list; this.numParams = total;
    this.params = new Float32Array(total);
    this.grads = new Float32Array(total);
    this.m = new Float32Array(total);
    this.v = new Float32Array(total);
    this.step = 0;
    const r = mulberry32(seed);
    for (const t of list) {
      const p = this.params.subarray(t.off, t.off + t.size);
      if (t.init === 'one') p.fill(1);
      else if (t.init) for (let i = 0; i < t.size; i++) p[i] = randn(r) * t.init;
    }
    this._views();
    this.acts = null; this.actsKey = '';
  }

  _views() {
    const P = {}, G = {};
    for (const t of this.tensors) {
      P[t.name] = this.params.subarray(t.off, t.off + t.size);
      G[t.name] = this.grads.subarray(t.off, t.off + t.size);
    }
    this.P = P; this.G = G;
  }

  /** Grow the word table when new words are taught. Keeps everything already learned. */
  growVocab(newV, seed = 7) {
    if (newV <= this.cfg.V) return;
    const old = this;
    const next = new GPT({ ...this.cfg, V: newV }, seed);
    for (const t of next.tensors) {
      const o = old.tensors.find((x) => x.name === t.name);
      const n = Math.min(o.size, t.size);
      for (const k of ['params', 'm', 'v']) next[k].set(old[k].subarray(o.off, o.off + n), t.off);
    }
    next.step = old.step;
    Object.assign(this, next);
    this._views();
    this.acts = null; this.actsKey = '';
  }

  // Buffers are sized for the longest sequence; shorter ones use the front part.
  _ensureActs(B) {
    const key = String(B);
    if (this.actsKey === key) return;
    const { C, L, H, V } = this.cfg, T = this.cfg.T, N = B * T;
    const f = (n) => new Float32Array(n);
    const layer = () => ({
      ln1: f(N * C), ln1m: f(N), ln1r: f(N), qkv: f(N * 3 * C), att: f(B * H * T * T), atty: f(N * C),
      attproj: f(N * C), res2: f(N * C), ln2: f(N * C), ln2m: f(N), ln2r: f(N), fch: f(N * 4 * C),
      fchg: f(N * 4 * C), fcproj: f(N * C), res3: f(N * C),
    });
    const set = () => ({ encoded: f(N * C), layers: Array.from({ length: L }, layer), lnf: f(N * C), lnfm: f(N), lnfr: f(N) });
    this.acts = set();
    this.dacts = set();
    this.probs = f(N * V);
    this.actsKey = key;
  }

  /** Runs the network. Returns mean loss if targets are given. lastOnly: logits for the final position only. */
  forward(inputs, targets, B, T, lastOnly = false) {
    const { C, L, H, V } = this.cfg, N = B * T, P = this.P;
    this._ensureActs(B);
    const a = this.acts;
    encoderForward(a.encoded, inputs, P.wte, P.wpe, B, T, C);
    let res = a.encoded;
    for (let l = 0; l < L; l++) {
      const A = a.layers[l];
      layernormForward(A.ln1, A.ln1m, A.ln1r, res, P[`${l}.ln1w`], P[`${l}.ln1b`], N, C);
      matmulForward(A.qkv, A.ln1, P[`${l}.qkvw`], P[`${l}.qkvb`], N, C, 3 * C);
      attentionForward(A.atty, A.att, A.qkv, B, T, C, H);
      matmulForward(A.attproj, A.atty, P[`${l}.projw`], P[`${l}.projb`], N, C, C);
      add(A.res2, res, A.attproj);
      layernormForward(A.ln2, A.ln2m, A.ln2r, A.res2, P[`${l}.ln2w`], P[`${l}.ln2b`], N, C);
      matmulForward(A.fch, A.ln2, P[`${l}.fcw`], P[`${l}.fcb`], N, C, 4 * C);
      geluForward(A.fchg, A.fch);
      matmulForward(A.fcproj, A.fchg, P[`${l}.fcprojw`], P[`${l}.fcprojb`], N, 4 * C, C);
      add(A.res3, A.res2, A.fcproj);
      res = A.res3;
    }
    layernormForward(a.lnf, a.lnfm, a.lnfr, res, P.lnfw, P.lnfb, N, C);
    if (lastOnly) {
      const row = this.probs.subarray(0, V);
      matmulForward(row, a.lnf.subarray((N - 1) * C, N * C), P.wte, null, 1, C, V);
      return row; // raw logits for the last position
    }
    matmulForward(this.probs, a.lnf, P.wte, null, N, C, V);
    let loss = 0;
    for (let n = 0; n < N; n++) {
      const row = this.probs.subarray(n * V, (n + 1) * V);
      let max = -Infinity;
      for (let v = 0; v < V; v++) if (row[v] > max) max = row[v];
      let sum = 0;
      for (let v = 0; v < V; v++) { row[v] = Math.exp(row[v] - max); sum += row[v]; }
      for (let v = 0; v < V; v++) row[v] /= sum;
      if (targets) loss -= Math.log(row[targets[n]] + 1e-12);
    }
    return targets ? loss / N : 0;
  }

  /** Adds this batch's gradients into this.grads. Call after forward(). scale: weight of this batch. */
  backward(inputs, targets, B, T, scale = 1) {
    const { C, L, H, V } = this.cfg, N = B * T, P = this.P, G = this.G, a = this.acts, d = this.dacts;
    zeroActs(d);
    const dlogits = this.probs; // reuse in place
    const k = scale / N;
    for (let n = 0; n < N; n++) {
      const row = dlogits.subarray(n * V, (n + 1) * V);
      for (let v = 0; v < V; v++) row[v] *= k;
      row[targets[n]] -= k;
    }
    matmulBackward(d.lnf, G.wte, null, dlogits, a.lnf, P.wte, N, C, V);
    const last = L > 0 ? a.layers[L - 1].res3 : a.encoded;
    const dlast = L > 0 ? d.layers[L - 1].res3 : d.encoded;
    layernormBackward(dlast, G.lnfw, G.lnfb, d.lnf, last, P.lnfw, a.lnfm, a.lnfr, N, C);
    for (let l = L - 1; l >= 0; l--) {
      const A = a.layers[l], D = d.layers[l];
      const res = l === 0 ? a.encoded : a.layers[l - 1].res3;
      const dres = l === 0 ? d.encoded : d.layers[l - 1].res3;
      accum(D.res2, D.res3); accum(D.fcproj, D.res3);
      matmulBackward(D.fchg, G[`${l}.fcprojw`], G[`${l}.fcprojb`], D.fcproj, A.fchg, P[`${l}.fcprojw`], N, 4 * C, C);
      geluBackward(D.fch, A.fch, D.fchg);
      matmulBackward(D.ln2, G[`${l}.fcw`], G[`${l}.fcb`], D.fch, A.ln2, P[`${l}.fcw`], N, C, 4 * C);
      layernormBackward(D.res2, G[`${l}.ln2w`], G[`${l}.ln2b`], D.ln2, A.res2, P[`${l}.ln2w`], A.ln2m, A.ln2r, N, C);
      accum(dres, D.res2); accum(D.attproj, D.res2);
      matmulBackward(D.atty, G[`${l}.projw`], G[`${l}.projb`], D.attproj, A.atty, P[`${l}.projw`], N, C, C);
      attentionBackward(D.qkv, D.atty, A.qkv, A.att, B, T, C, H);
      matmulBackward(D.ln1, G[`${l}.qkvw`], G[`${l}.qkvb`], D.qkv, A.ln1, P[`${l}.qkvw`], N, C, 3 * C);
      layernormBackward(dres, G[`${l}.ln1w`], G[`${l}.ln1b`], D.ln1, res, P[`${l}.ln1w`], A.ln1m, A.ln1r, N, C);
    }
    encoderBackward(G.wte, G.wpe, d.encoded, inputs, B, T, C);
  }

  /** AdamW update with gradient clipping, then clears gradients. */
  update(lr, wd = 0.01, b1 = 0.9, b2 = 0.99, eps = 1e-8, clip = 1) {
    const g = this.grads;
    let norm = 0;
    for (let i = 0; i < g.length; i++) norm += g[i] * g[i];
    norm = Math.sqrt(norm);
    const gs = norm > clip ? clip / norm : 1;
    this.step++;
    const c1 = 1 - Math.pow(b1, this.step), c2 = 1 - Math.pow(b2, this.step);
    const p = this.params, m = this.m, v = this.v;
    for (const t of this.tensors) {
      const dec = t.decay ? wd : 0;
      for (let i = t.off, e = t.off + t.size; i < e; i++) {
        const gi = g[i] * gs;
        m[i] = b1 * m[i] + (1 - b1) * gi;
        v[i] = b2 * v[i] + (1 - b2) * gi * gi;
        p[i] -= lr * ((m[i] / c1) / (Math.sqrt(v[i] / c2) + eps) + dec * p[i]);
      }
    }
    g.fill(0);
    return norm;
  }
}

function zeroActs(s) {
  s.encoded.fill(0); s.lnf.fill(0);
  for (const l of s.layers) for (const k in l) l[k].fill(0);
}
function add(out, a, b) { for (let i = 0; i < out.length; i++) out[i] = a[i] + b[i]; }
function accum(out, a) { for (let i = 0; i < out.length; i++) out[i] += a[i]; }

function encoderForward(out, inp, wte, wpe, B, T, C) {
  for (let b = 0; b < B; b++) for (let t = 0; t < T; t++) {
    const o = (b * T + t) * C, e = inp[b * T + t] * C, p = t * C;
    for (let i = 0; i < C; i++) out[o + i] = wte[e + i] + wpe[p + i];
  }
}
function encoderBackward(dwte, dwpe, dout, inp, B, T, C) {
  for (let b = 0; b < B; b++) for (let t = 0; t < T; t++) {
    const o = (b * T + t) * C, e = inp[b * T + t] * C, p = t * C;
    for (let i = 0; i < C; i++) { dwte[e + i] += dout[o + i]; dwpe[p + i] += dout[o + i]; }
  }
}
function layernormForward(out, mean, rstd, inp, w, b, N, C) {
  for (let n = 0; n < N; n++) {
    const x = n * C;
    let m = 0; for (let i = 0; i < C; i++) m += inp[x + i]; m /= C;
    let v = 0; for (let i = 0; i < C; i++) { const d = inp[x + i] - m; v += d * d; } v /= C;
    const s = 1 / Math.sqrt(v + 1e-5);
    for (let i = 0; i < C; i++) out[x + i] = (inp[x + i] - m) * s * w[i] + b[i];
    mean[n] = m; rstd[n] = s;
  }
}
function layernormBackward(dinp, dw, db, dout, inp, w, mean, rstd, N, C) {
  for (let n = 0; n < N; n++) {
    const x = n * C, m = mean[n], s = rstd[n];
    let dm = 0, dnm = 0;
    for (let i = 0; i < C; i++) {
      const norm = (inp[x + i] - m) * s, dn = w[i] * dout[x + i];
      dm += dn; dnm += dn * norm;
    }
    dm /= C; dnm /= C;
    for (let i = 0; i < C; i++) {
      const norm = (inp[x + i] - m) * s, dn = w[i] * dout[x + i];
      db[i] += dout[x + i]; dw[i] += norm * dout[x + i];
      dinp[x + i] += (dn - dm - norm * dnm) * s;
    }
  }
}
/** out[N,OC] = inp[N,C] · w[OC,C]ᵀ + bias */
function matmulForward(out, inp, w, bias, N, C, OC) {
  for (let n = 0; n < N; n++) {
    const x = n * C, o = n * OC;
    for (let j = 0; j < OC; j++) {
      const r = j * C;
      let s = bias ? bias[j] : 0;
      for (let i = 0; i < C; i++) s += inp[x + i] * w[r + i];
      out[o + j] = s;
    }
  }
}
function matmulBackward(dinp, dw, db, dout, inp, w, N, C, OC) {
  for (let n = 0; n < N; n++) {
    const x = n * C, o = n * OC;
    for (let j = 0; j < OC; j++) {
      const g = dout[o + j];
      if (g === 0) continue;
      const r = j * C;
      for (let i = 0; i < C; i++) { dinp[x + i] += g * w[r + i]; dw[r + i] += g * inp[x + i]; }
      if (db) db[j] += g;
    }
  }
}
function attentionForward(out, att, qkv, B, T, C, H) {
  const C3 = 3 * C, hs = C / H, scale = 1 / Math.sqrt(hs);
  for (let b = 0; b < B; b++) for (let t = 0; t < T; t++) for (let h = 0; h < H; h++) {
    const q = b * T * C3 + t * C3 + h * hs, a = ((b * H + h) * T + t) * T;
    let max = -Infinity;
    for (let t2 = 0; t2 <= t; t2++) {
      const k = b * T * C3 + t2 * C3 + h * hs + C;
      let s = 0; for (let i = 0; i < hs; i++) s += qkv[q + i] * qkv[k + i];
      s *= scale; att[a + t2] = s; if (s > max) max = s;
    }
    let sum = 0;
    for (let t2 = 0; t2 <= t; t2++) { const e = Math.exp(att[a + t2] - max); att[a + t2] = e; sum += e; }
    for (let t2 = 0; t2 <= t; t2++) att[a + t2] /= sum;
    for (let t2 = t + 1; t2 < T; t2++) att[a + t2] = 0;
    const o = (b * T + t) * C + h * hs;
    for (let i = 0; i < hs; i++) out[o + i] = 0;
    for (let t2 = 0; t2 <= t; t2++) {
      const v = b * T * C3 + t2 * C3 + h * hs + 2 * C, p = att[a + t2];
      for (let i = 0; i < hs; i++) out[o + i] += p * qkv[v + i];
    }
  }
}
function attentionBackward(dqkv, dout, qkv, att, B, T, C, H) {
  const C3 = 3 * C, hs = C / H, scale = 1 / Math.sqrt(hs);
  const datt = new Float32Array(T);
  for (let b = 0; b < B; b++) for (let t = 0; t < T; t++) for (let h = 0; h < H; h++) {
    const q = b * T * C3 + t * C3 + h * hs, a = ((b * H + h) * T + t) * T, o = (b * T + t) * C + h * hs;
    let dot = 0;
    for (let t2 = 0; t2 <= t; t2++) {
      const v = b * T * C3 + t2 * C3 + h * hs + 2 * C, p = att[a + t2];
      let s = 0;
      for (let i = 0; i < hs; i++) { s += qkv[v + i] * dout[o + i]; dqkv[v + i] += p * dout[o + i]; }
      datt[t2] = s; dot += p * s;
    }
    for (let t2 = 0; t2 <= t; t2++) {
      const g = att[a + t2] * (datt[t2] - dot) * scale;
      if (g === 0) continue;
      const k = b * T * C3 + t2 * C3 + h * hs + C;
      for (let i = 0; i < hs; i++) { dqkv[q + i] += g * qkv[k + i]; dqkv[k + i] += g * qkv[q + i]; }
    }
  }
}
const GS = Math.sqrt(2 / Math.PI);
function geluForward(out, inp) {
  for (let i = 0; i < inp.length; i++) {
    const x = inp[i];
    out[i] = 0.5 * x * (1 + Math.tanh(GS * (x + 0.044715 * x * x * x)));
  }
}
function geluBackward(dinp, inp, dout) {
  for (let i = 0; i < inp.length; i++) {
    const x = inp[i], u = GS * (x + 0.044715 * x * x * x), th = Math.tanh(u);
    const sech2 = 1 - th * th;
    dinp[i] += (0.5 * (1 + th) + 0.5 * x * sech2 * GS * (1 + 3 * 0.044715 * x * x)) * dout[i];
  }
}

root.TinyAICore = {
  mulberry32, randn, normalize, userWords, Vocab, encodeExchange, detokenize, SPECIAL, UNK, USER, AI, END,
  SIZES, GPT, setProper(list) { PROPER = list.slice().sort((a, b) => b.length - a.length); },
};
})(typeof self !== 'undefined' ? self : globalThis);
