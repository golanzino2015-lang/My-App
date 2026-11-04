package com.questionbubble.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Grouped list building blocks (settings-style rows inside a rounded card). */
final class Rows {

    static LinearLayout group(Context c) {
        LinearLayout g = new LinearLayout(c);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setBackground(Theme.card(c));
        Theme.elevate(g, 0, 24);
        return g;
    }

    /** Adds a row, with an inset hairline above it when it isn't the first. */
    static void add(LinearLayout group, View row) {
        Context c = group.getContext();
        if (group.getChildCount() > 0) {
            View div = new View(c);
            div.setBackgroundColor(Theme.HAIRLINE);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Theme.MATCH, Math.max(1, Theme.dp(c, 0.8f)));
            p.leftMargin = Theme.dp(c, 66);
            group.addView(div, p);
        }
        group.addView(row, Theme.lp(Theme.MATCH, Theme.WRAP));
    }

    static LinearLayout row(Context c, int icon, String title, String subtitle, View trailing, Runnable onClick) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Theme.dp(c, 64));
        r.setPadding(Theme.dp(c, 16), Theme.dp(c, 12), Theme.dp(c, 14), Theme.dp(c, 12));
        if (icon != 0) {
            FrameLayout tile = Theme.iconTile(c, icon, 36);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(Theme.dp(c, 36), Theme.dp(c, 36));
            tp.rightMargin = Theme.dp(c, 14);
            r.addView(tile, tp);
        }
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = Theme.text(c, title, Theme.Type.BODY_M);
        col.addView(t);
        if (subtitle != null) {
            TextView s = Theme.text(c, subtitle, Theme.Type.CAPTION);
            col.addView(s, Theme.lp(Theme.WRAP, Theme.WRAP, Theme.dp(c, 4)));
        }
        r.addView(col, new LinearLayout.LayoutParams(0, Theme.WRAP, 1));
        if (trailing != null) {
            int tw = trailing.getLayoutParams() != null && trailing.getLayoutParams().width > 0
                    ? trailing.getLayoutParams().width : Theme.WRAP;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tw, Theme.WRAP);
            lp.leftMargin = Theme.dp(c, 12);
            r.addView(trailing, lp);
        }
        if (onClick != null) {
            r.setBackground(new RippleDrawable(ColorStateList.valueOf(0x14FFFFFF), null, null));
            r.setClickable(true);
            r.setOnClickListener(v -> {
                Theme.tap(v);
                onClick.run();
            });
        }
        return r;
    }

    /** Trailing "value  >" pair. */
    static View value(Context c, String text) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        if (text != null) {
            TextView v = Theme.oneLine(Theme.text(c, text, Theme.Type.CALLOUT));
            v.setTextColor(Theme.TEXT_2);
            v.setTypeface(Fonts.weight(c, 400));
            v.setMaxWidth(Theme.dp(c, 150));
            l.addView(v);
        }
        FrameLayout chev = new FrameLayout(c);
        chev.addView(Theme.icon(c, R.drawable.ic_chevron_right, Theme.TEXT_3, 18));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Theme.dp(c, 18), Theme.dp(c, 18));
        p.leftMargin = Theme.dp(c, 4);
        l.addView(chev, p);
        return l;
    }

    static TextView section(Context c, String text) {
        TextView t = Theme.text(c, text, Theme.Type.OVERLINE);
        t.setPadding(Theme.dp(c, 6), 0, 0, 0);
        return t;
    }

    /** Round icon-only button. */
    static FrameLayout iconButton(Context c, int icon, Runnable onClick) {
        FrameLayout b = new FrameLayout(c);
        b.setBackground(Theme.ripple(Theme.rounded(Theme.FILL, Theme.dp(c, 21), Theme.HAIRLINE, Math.max(1, Theme.dp(c, 0.8f))), Theme.dp(c, 21)));
        b.addView(Theme.icon(c, icon, Theme.TEXT, 19));
        b.setClickable(true);
        b.setOnClickListener(v -> {
            Theme.tap(v);
            onClick.run();
        });
        Theme.pressable(b, 0.9f);
        b.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(c, 42), Theme.dp(c, 42)));
        return b;
    }

    private Rows() {}
}
