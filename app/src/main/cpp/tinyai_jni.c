// Java bridge for the Tiny AI engine (class com.questionbubble.app.NativeAI).
#include <jni.h>
#include <stdlib.h>
#include <string.h>

#include "tinyai.h"

typedef struct {
    Model *m;
    int32_t *stream, *starts;
} Handle;

#define H(h) ((Handle *) (intptr_t) (h))
#define FN(name) Java_com_questionbubble_app_NativeAI_##name

static jlong wrap(Model *m) {
    if (!m) return 0;
    Handle *h = calloc(1, sizeof(Handle));
    h->m = m;
    return (jlong) (intptr_t) h;
}

JNIEXPORT jlong JNICALL FN(create)(JNIEnv *e, jclass c, jint V, jint C, jint L, jint Hd, jint T, jint seed, jint threads) {
    Config cfg = {V, C, L, Hd, T};
    return wrap(ta_create(cfg, (uint32_t) seed, threads));
}

JNIEXPORT jlong JNICALL FN(load)(JNIEnv *e, jclass c, jstring path, jint threads) {
    const char *p = (*e)->GetStringUTFChars(e, path, NULL);
    Model *m = ta_load(p, threads);
    (*e)->ReleaseStringUTFChars(e, path, p);
    return wrap(m);
}

JNIEXPORT jboolean JNICALL FN(save)(JNIEnv *e, jclass c, jlong h, jstring path) {
    const char *p = (*e)->GetStringUTFChars(e, path, NULL);
    int ok = ta_save(H(h)->m, p);
    (*e)->ReleaseStringUTFChars(e, path, p);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL FN(free)(JNIEnv *e, jclass c, jlong h) {
    if (!h) return;
    ta_free(H(h)->m);
    free(H(h)->stream);
    free(H(h)->starts);
    free(H(h));
}

JNIEXPORT void JNICALL FN(setData)(JNIEnv *e, jclass c, jlong h, jintArray stream, jintArray starts) {
    Handle *hd = H(h);
    jsize n = (*e)->GetArrayLength(e, stream), ns = (*e)->GetArrayLength(e, starts);
    int32_t *s = malloc(n * sizeof(int32_t)), *st = malloc(ns * sizeof(int32_t));
    (*e)->GetIntArrayRegion(e, stream, 0, n, s);
    (*e)->GetIntArrayRegion(e, starts, 0, ns, st);
    ta_set_data(hd->m, s, n, st, ns);
    free(hd->stream);
    free(hd->starts);
    hd->stream = s;
    hd->starts = st;
}

JNIEXPORT jfloat JNICALL FN(trainStep)(JNIEnv *e, jclass c, jlong h, jint seqs, jfloat lr, jfloat wd) {
    return ta_train_step(H(h)->m, seqs, lr, wd);
}

JNIEXPORT jboolean JNICALL FN(growVocab)(JNIEnv *e, jclass c, jlong h, jint newV) {
    return ta_grow_vocab(H(h)->m, newV) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jintArray JNICALL FN(generate)(JNIEnv *e, jclass c, jlong h, jintArray ctx, jint maxNew, jint stopId,
                                         jint vocabUsed, jintArray banned, jfloat temperature, jint topK, jlong seed) {
    jsize n = (*e)->GetArrayLength(e, ctx), nb = (*e)->GetArrayLength(e, banned);
    int32_t *in = malloc((n ? n : 1) * sizeof(int32_t)), *b = malloc((nb ? nb : 1) * sizeof(int32_t));
    int32_t *out = malloc((maxNew > 0 ? maxNew : 1) * sizeof(int32_t));
    (*e)->GetIntArrayRegion(e, ctx, 0, n, in);
    (*e)->GetIntArrayRegion(e, banned, 0, nb, b);
    int k = ta_generate(H(h)->m, in, n, out, maxNew, stopId, vocabUsed, b, nb, temperature, topK, (uint64_t) seed);
    jintArray res = (*e)->NewIntArray(e, k);
    (*e)->SetIntArrayRegion(e, res, 0, k, out);
    free(in);
    free(b);
    free(out);
    return res;
}

JNIEXPORT jlong JNICALL FN(step)(JNIEnv *e, jclass c, jlong h) { return ta_step(H(h)->m); }
JNIEXPORT jlong JNICALL FN(numParams)(JNIEnv *e, jclass c, jlong h) { return ta_num_params(H(h)->m); }
JNIEXPORT jint JNICALL FN(threads)(JNIEnv *e, jclass c, jlong h) { return ta_threads(H(h)->m); }
JNIEXPORT jintArray JNICALL FN(config)(JNIEnv *e, jclass c, jlong h) {
    Config cfg = ta_config(H(h)->m);
    jint v[5] = {cfg.V, cfg.C, cfg.L, cfg.H, cfg.T};
    jintArray r = (*e)->NewIntArray(e, 5);
    (*e)->SetIntArrayRegion(e, r, 0, 5, v);
    return r;
}
