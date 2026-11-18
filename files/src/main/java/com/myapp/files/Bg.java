package com.myapp.files;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Background work and posting back to the main thread. */
final class Bg {
    static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);

    interface Job<T> {
        T run() throws Exception;
    }

    interface Done<T> {
        void done(T value, Exception error);
    }

    static <T> void run(Job<T> job, Done<T> done) {
        IO.execute(() -> {
            T v = null;
            Exception err = null;
            try {
                v = job.run();
            } catch (Exception e) {
                err = e;
            }
            final T fv = v;
            final Exception fe = err;
            MAIN.post(() -> done.done(fv, fe));
        });
    }

    static void io(Runnable r) {
        IO.execute(r);
    }

    static void main(Runnable r) {
        MAIN.post(r);
    }

    private Bg() {}
}
