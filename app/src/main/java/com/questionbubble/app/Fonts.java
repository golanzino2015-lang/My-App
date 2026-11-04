package com.questionbubble.app;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;

/** Inter, bundled in res/font. Falls back to the system sans-serif if a file fails to load. */
final class Fonts {

    private static Typeface regular, medium, semibold, bold, display;

    static synchronized void init(Context c) {
        if (regular != null) return;
        Resources r = c.getApplicationContext().getResources();
        regular = load(r, R.font.inter_regular, 400);
        medium = load(r, R.font.inter_medium, 500);
        semibold = load(r, R.font.inter_semibold, 600);
        bold = load(r, R.font.inter_bold, 700);
        display = load(r, R.font.inter_display_semibold, 600);
    }

    private static Typeface load(Resources r, int id, int weight) {
        try {
            Typeface t = r.getFont(id);
            if (t != null) return t;
        } catch (Exception ignored) {
        }
        return Typeface.create(Typeface.SANS_SERIF, weight, false);
    }

    /** 400 regular, 500 medium, 600 semibold, 700 bold. */
    static Typeface weight(Context c, int w) {
        init(c);
        if (w >= 700) return bold;
        if (w >= 600) return semibold;
        if (w >= 500) return medium;
        return regular;
    }

    /** Inter Display: tuned for large headlines. */
    static Typeface display(Context c) {
        init(c);
        return display;
    }

    private Fonts() {}
}
