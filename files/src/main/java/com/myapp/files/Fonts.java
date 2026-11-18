package com.myapp.files;

import android.content.Context;
import android.graphics.Typeface;
import android.util.SparseArray;

/** Inter, bundled in res/font (same faces as My App). */
final class Fonts {
    private static final SparseArray<Typeface> cache = new SparseArray<>();

    static Typeface display(Context c) {
        return load(c, R.font.inter_display_semibold);
    }

    static Typeface weight(Context c, int w) {
        int res;
        if (w >= 700) res = R.font.inter_bold;
        else if (w >= 600) res = R.font.inter_semibold;
        else if (w >= 500) res = R.font.inter_medium;
        else res = R.font.inter_regular;
        return load(c, res);
    }

    private static Typeface load(Context c, int res) {
        Typeface t = cache.get(res);
        if (t == null) {
            try {
                t = c.getResources().getFont(res);
            } catch (Exception e) {
                t = Typeface.DEFAULT;
            }
            cache.put(res, t);
        }
        return t;
    }

    private Fonts() {}
}
