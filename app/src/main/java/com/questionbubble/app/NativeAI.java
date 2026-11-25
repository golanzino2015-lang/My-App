package com.questionbubble.app;

/** The C training engine (app/src/main/cpp). Handles are pointers; 0 means failure. */
final class NativeAI {
    static {
        System.loadLibrary("tinyai");
    }

    static native long create(int V, int C, int L, int H, int T, int seed, int threads);
    static native long load(String path, int threads);
    static native boolean save(long h, String path);
    static native void free(long h);
    static native void setData(long h, int[] stream, int[] starts);
    static native float trainStep(long h, int seqs, float lr, float weightDecay);
    static native boolean growVocab(long h, int newV);
    static native int[] generate(long h, int[] ctx, int maxNew, int stopId, int vocabUsed, int[] banned,
                                 float temperature, int topK, long seed);
    static native long step(long h);
    static native long numParams(long h);
    static native int threads(long h);
    /** {V, C, L, H, T} */
    static native int[] config(long h);

    private NativeAI() {}
}
