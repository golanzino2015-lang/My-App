package com.questionbubble.app;

import android.animation.TimeInterpolator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.PathInterpolator;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared look: monochrome palette, type scale, controls and motion. */
final class Ui {

    // Palette
    static final int WIN = 0xFF0A0A0A;
    static final int TOP = 0xFF1A1A1A;
    static final int CARD_TOP = 0xFF151515;
    static final int CARD_BOTTOM = 0xFF0D0D0D;
    static final int ELEM = 0xFF222222;
    static final int ELEM_PRESSED = 0xFF2E2E2E;
    static final int SEP = 0xFF303030;
    static final int FAINT = 0xFF5A5A5A;
    static final int DIM = 0xFF969696;
    static final int TEXT = 0xFFF0F0F0;
    static final int WHITE = 0xFFFFFFFF;

    // Motion
    static final TimeInterpolator EMPHASIZED = new PathInterpolator(0.2f, 0f, 0f, 1f);
    static final TimeInterpolator EXIT = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static Typeface font(int weight) {
        return Typeface.create(Typeface.SANS_SERIF, weight, false);
    }

    static TextView text(Context c, CharSequence s, float sp, int weight, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(font(weight));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Small all-caps section label, e.g. "API KEY". */
    static TextView label(Context c, String s) {
        TextView t = text(c, s.toUpperCase(), 10.5f, 700, FAINT);
        t.setLetterSpacing(0.14f);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    static GradientDrawable rounded(int color, float radiusPx, int strokeColor, int strokePx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        if (strokePx > 0) g.setStroke(strokePx, strokeColor);
        return g;
    }

    static GradientDrawable card(Context c) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{CARD_TOP, CARD_BOTTOM});
        g.setCornerRadius(dp(c, 16));
        g.setStroke(dp(c, 1), SEP);
        return g;
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(SEP);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1)));
        return v;
    }

    /** Subtle press-down scale; keeps normal click handling. */
    @SuppressLint("ClickableViewAccessibility")
    static void pressEffect(View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90)
                            .setInterpolator(EMPHASIZED).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(180)
                            .setInterpolator(EMPHASIZED).start();
                    break;
            }
            return false;
        });
    }

    private static TextView baseButton(Context c, String s) {
        TextView b = text(c, s, 14, 600, WHITE);
        b.setGravity(Gravity.CENTER);
        b.setLetterSpacing(0.02f);
        b.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        b.setMinHeight(dp(c, 46));
        b.setClickable(true);
        b.setFocusable(true);
        pressEffect(b);
        return b;
    }

    /** White button with black text. */
    static TextView primaryButton(Context c, String s) {
        TextView b = baseButton(c, s);
        b.setTextColor(WIN);
        b.setBackground(rounded(WHITE, dp(c, 12), 0, 0));
        return b;
    }

    /** Outlined button with white text. */
    static TextView secondaryButton(Context c, String s) {
        TextView b = baseButton(c, s);
        b.setBackground(rounded(0x00000000, dp(c, 12), SEP, dp(c, 1)));
        return b;
    }

    static EditText input(Context c, String hint, boolean password) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(FAINT);
        e.setTextColor(TEXT);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        e.setTypeface(font(500));
        e.setPadding(dp(c, 14), dp(c, 13), dp(c, 14), dp(c, 13));
        GradientDrawable bg = rounded(ELEM, dp(c, 12), SEP, dp(c, 1));
        e.setBackground(bg);
        GradientDrawable cursor = new GradientDrawable();
        cursor.setColor(WHITE);
        cursor.setSize(dp(c, 2), dp(c, 18));
        e.setTextCursorDrawable(cursor);
        e.setHighlightColor(0x55FFFFFF);
        e.setOnFocusChangeListener((v, has) -> bg.setStroke(dp(c, 1), has ? WHITE : SEP));
        if (password) {
            e.setSingleLine(true);
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        return e;
    }

    static LinearLayout.LayoutParams lp(int w, int h, int topMarginPx) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = topMarginPx;
        return p;
    }

    /** Fade + rise in. */
    static void enter(View v, long delay) {
        v.setAlpha(0f);
        v.setTranslationY(dp(v.getContext(), 12));
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(420)
                .setInterpolator(EMPHASIZED).start();
    }

    /** Scale + fade in, for panels and dialogs. */
    static void popIn(View v) {
        v.setAlpha(0f);
        v.setScaleX(0.92f);
        v.setScaleY(0.92f);
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320)
                .setInterpolator(EMPHASIZED).start();
    }

    /** Glass panel with the animated shine outline. */
    static ShineDrawable panel(Context c, float radiusDp) {
        return new ShineDrawable(dp(c, radiusDp), dp(c, 1.3f), false, 0xF51A1A1A, 0xF50A0A0A, WHITE);
    }

    /** Styled confirm dialog. */
    static void dialog(Activity a, String title, String message,
                       String positive, Runnable onPositive, String negative) {
        Dialog d = new Dialog(a, R.style.DialogTheme);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(a, 20);
        panel.setPadding(pad, pad, pad, dp(a, 16));
        ShineDrawable bg = panel(a, 18);
        panel.setBackground(bg);
        ShineDrawable.animateWhileAttached(panel, bg, 4000);

        panel.addView(text(a, title, 18, 700, TEXT));
        TextView msg = text(a, message, 14, 400, DIM);
        msg.setLineSpacing(dp(a, 4), 1f);
        panel.addView(msg, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, dp(a, 10)));

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        if (negative != null) {
            TextView no = secondaryButton(a, negative);
            no.setOnClickListener(v -> d.dismiss());
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            p.rightMargin = dp(a, 10);
            row.addView(no, p);
        }
        TextView yes = primaryButton(a, positive);
        yes.setOnClickListener(v -> {
            d.dismiss();
            if (onPositive != null) onPositive.run();
        });
        row.addView(yes, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        panel.addView(row, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, dp(a, 20)));

        d.setContentView(panel);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            int width = Math.min(dp(a, 380), a.getResources().getDisplayMetrics().widthPixels - dp(a, 40));
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.show();
        popIn(panel);
    }

    private Ui() {}
}
