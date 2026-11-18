package com.myapp.files;

import android.animation.TimeInterpolator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Design tokens and view helpers. Same language as My App (Inter, soft gradients, rounded
 * cards, springy presses) but in a light theme: white surfaces, black strokes and black text.
 */
final class Ui {

    // ---------------------------------------------------------------- color
    static final int WHITE = 0xFFFFFFFF;
    static final int BLACK = 0xFF000000;
    static final int INK = 0xFF0A0A0A;        // text, strokes, icons
    static final int INK_2 = 0xFF5A5A62;      // secondary text
    static final int INK_3 = 0xFF9B9BA3;      // tertiary text
    static final int LINE = 0xFF0A0A0A;       // card and control strokes
    static final int LINE_SOFT = 0x1F000000;  // dividers
    static final int FILL = 0xFFF2F2F5;       // pressed / selected rows, chips
    static final int DANGER = 0xFFD92D20;

    // ---------------------------------------------------------------- motion
    static final TimeInterpolator EMPHASIZED = new PathInterpolator(0.2f, 0f, 0f, 1f);
    static final TimeInterpolator DECELERATE = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    static final TimeInterpolator ACCELERATE = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);

    // ---------------------------------------------------------------- type
    enum Type {
        DISPLAY(34, 0, -0.03f, INK),
        HEADLINE(26, 0, -0.025f, INK),
        TITLE(20, 600, -0.018f, INK),
        TITLE_S(16, 600, -0.012f, INK),
        BODY(15, 400, -0.006f, INK_2),
        BODY_M(15, 500, -0.008f, INK),
        CALLOUT(14, 500, -0.006f, INK),
        CAPTION(12.5f, 400, -0.002f, INK_2),
        LABEL(12.5f, 600, 0f, INK),
        OVERLINE(11.5f, 600, 0.08f, INK_3);

        final float sp;
        final int weight; // 0 = display face
        final float tracking;
        final int color;

        Type(float sp, int weight, float tracking, int color) {
            this.sp = sp;
            this.weight = weight;
            this.tracking = tracking;
            this.color = color;
        }
    }

    static TextView text(Context c, CharSequence s, Type t) {
        TextView v = new TextView(c);
        style(v, t);
        v.setText(t == Type.OVERLINE && s != null ? s.toString().toUpperCase() : s);
        return v;
    }

    static void style(TextView v, Type t) {
        Context c = v.getContext();
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, t.sp);
        v.setTypeface(t.weight == 0 ? Fonts.display(c) : Fonts.weight(c, t.weight));
        v.setLetterSpacing(t.tracking);
        v.setTextColor(t.color);
        v.setIncludeFontPadding(false);
        if (t == Type.BODY || t == Type.CAPTION) v.setLineSpacing(dp(c, t == Type.BODY ? 5 : 3), 1f);
    }

    static TextView oneLine(TextView v) {
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        return v;
    }

    static TextView middle(TextView v) {
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        return v;
    }

    // ---------------------------------------------------------------- metrics
    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static int stroke(Context c) {
        return Math.max(1, dp(c, 1f));
    }

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams lp(int w, int h, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = top;
        return p;
    }

    static LinearLayout.LayoutParams weight(int h) {
        return new LinearLayout.LayoutParams(0, h, 1f);
    }

    static FrameLayout.LayoutParams flp(int w, int h, int gravity) {
        return new FrameLayout.LayoutParams(w, h, gravity);
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static View space(Context c, int wDp, int hDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, wDp), dp(c, hDp)));
        return v;
    }

    static View grow(Context c) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        return v;
    }

    // ---------------------------------------------------------------- surfaces
    static GradientDrawable rounded(int fill, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radiusPx);
        return g;
    }

    static GradientDrawable rounded(int fill, float radiusPx, int stroke, int strokePx) {
        GradientDrawable g = rounded(fill, radiusPx);
        if (strokePx > 0) g.setStroke(strokePx, stroke);
        return g;
    }

    /** Standard card: a white to pearl diagonal gradient with a thin black edge. */
    static GradientDrawable card(Context c, float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFFFFFFF, 0xFFFAFAFB, 0xFFF1F1F4});
        g.setCornerRadius(dp(c, radiusDp));
        g.setStroke(stroke(c), LINE);
        return g;
    }

    /** Premium black: deep graphite to black, used for the primary button and the hero card. */
    static GradientDrawable ink(Context c, float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF45454B, 0xFF16161A, 0xFF000000});
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    /** The hero card: ink gradient with a soft silver sheen in the top right corner. */
    static Drawable hero(Context c, float radiusDp) {
        GradientDrawable base = ink(c, radiusDp);
        GradientDrawable sheen = new GradientDrawable();
        sheen.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        sheen.setColors(new int[]{0x55FFFFFF, 0x14FFFFFF, 0x00FFFFFF});
        sheen.setGradientCenter(0.95f, 0.0f);
        sheen.setGradientRadius(dp(c, 260));
        sheen.setCornerRadius(dp(c, radiusDp));
        return new LayerDrawable(new Drawable[]{base, sheen});
    }

    /** Icon tile: a small pearl gradient square with a black edge. */
    static GradientDrawable tile(Context c, float sizeDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFFFFFFF, 0xFFF0F0F3, 0xFFE3E3E8});
        g.setCornerRadius(dp(c, sizeDp * 0.32f));
        g.setStroke(stroke(c), LINE);
        return g;
    }

    /** Full-screen background: white, fading to pearl, with two faint silver glows. */
    static Drawable background(Context c) {
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFFFFFFF, 0xFFFBFBFC, 0xFFF2F2F5});
        GradientDrawable glow1 = new GradientDrawable();
        glow1.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow1.setColors(new int[]{0x30B8B8C8, 0x00FFFFFF});
        glow1.setGradientCenter(1.0f, 0.0f);
        glow1.setGradientRadius(dp(c, 380));
        GradientDrawable glow2 = new GradientDrawable();
        glow2.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow2.setColors(new int[]{0x22C8CCD8, 0x00FFFFFF});
        glow2.setGradientCenter(0.0f, 0.55f);
        glow2.setGradientRadius(dp(c, 320));
        return new LayerDrawable(new Drawable[]{base, glow1, glow2});
    }

    static Drawable plain() {
        return new ColorDrawable(WHITE);
    }

    /** Rounded clip + elevation shadow for a view with a rounded background. */
    static void elevate(View v, float elevationDp, float radiusDp) {
        float r = dp(v.getContext(), radiusDp);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        v.setClipToOutline(true);
        v.setElevation(dp(v.getContext(), elevationDp));
        v.setOutlineSpotShadowColor(0x55000000);
        v.setOutlineAmbientShadowColor(0x33000000);
    }

    static void clip(View v, float radiusDp) {
        float r = dp(v.getContext(), radiusDp);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        v.setClipToOutline(true);
    }

    /** Light ripple for white surfaces. */
    static RippleDrawable ripple(Drawable content, float radiusPx) {
        return new RippleDrawable(ColorStateList.valueOf(0x14000000), content, rounded(WHITE, radiusPx));
    }

    static RippleDrawable rippleDark(Drawable content, float radiusPx) {
        return new RippleDrawable(ColorStateList.valueOf(0x30FFFFFF), content, rounded(WHITE, radiusPx));
    }

    // ---------------------------------------------------------------- icons
    static ImageView icon(Context c, int res, int color, int sizeDp) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setImageTintList(ColorStateList.valueOf(color));
        iv.setLayoutParams(new FrameLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp), Gravity.CENTER));
        return iv;
    }

    static FrameLayout iconTile(Context c, int res, int sizeDp) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(tile(c, sizeDp));
        f.addView(icon(c, res, INK, Math.round(sizeDp * 0.48f)));
        return f;
    }

    /** Round outlined icon button (back, search, more...). */
    static FrameLayout circleButton(Context c, int res, int sizeDp, View.OnClickListener l) {
        FrameLayout f = new FrameLayout(c);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFFFFFFF, 0xFFF1F1F4});
        g.setShape(GradientDrawable.OVAL);
        g.setStroke(stroke(c), LINE);
        f.setBackground(ripple(g, dp(c, sizeDp)));
        f.addView(icon(c, res, INK, Math.round(sizeDp * 0.46f)));
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        f.setOnClickListener(v -> {
            tap(v);
            l.onClick(v);
        });
        pressable(f, 0.9f);
        return f;
    }

    /** Pill button: black gradient (primary) or white with a black stroke (secondary). */
    static TextView pill(Context c, String label, boolean primary, View.OnClickListener l) {
        TextView t = text(c, label, Type.CALLOUT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 20), 0, dp(c, 20), 0);
        t.setMinHeight(dp(c, 48));
        if (primary) {
            t.setTextColor(WHITE);
            t.setBackground(rippleDark(ink(c, 24), dp(c, 24)));
        } else {
            t.setBackground(ripple(card(c, 24), dp(c, 24)));
        }
        t.setOnClickListener(v -> {
            tap(v);
            l.onClick(v);
        });
        pressable(t, 0.96f);
        return t;
    }

    static TextView chip(Context c, String label, boolean active) {
        TextView t = text(c, label, Type.LABEL);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        t.setMinHeight(dp(c, 36));
        setChip(t, active);
        pressable(t, 0.95f);
        return t;
    }

    static void setChip(TextView t, boolean active) {
        Context c = t.getContext();
        if (active) {
            t.setTextColor(WHITE);
            t.setBackground(rippleDark(ink(c, 18), dp(c, 18)));
        } else {
            t.setTextColor(INK);
            t.setBackground(ripple(rounded(WHITE, dp(c, 18), LINE, stroke(c)), dp(c, 18)));
        }
    }

    // ---------------------------------------------------------------- interaction
    @SuppressLint("ClickableViewAccessibility")
    static void pressable(View v, float scale) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(scale).scaleY(scale).setDuration(110)
                            .setInterpolator(EMPHASIZED).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(340)
                            .setInterpolator(new OvershootInterpolator(2.2f)).start();
                    break;
            }
            return false;
        });
    }

    static void tap(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    static void tick(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    static void longPress(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    static void confirm(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
    }

    // ---------------------------------------------------------------- motion helpers
    static void enter(View v, long delay) {
        v.setAlpha(0f);
        v.setTranslationY(dp(v.getContext(), 16));
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(560)
                .setInterpolator(DECELERATE).start();
    }

    static void shake(View v) {
        float d = dp(v.getContext(), 7);
        android.animation.ObjectAnimator a = android.animation.ObjectAnimator.ofFloat(v,
                View.TRANSLATION_X, 0, d, -d, d * 0.6f, -d * 0.6f, d * 0.25f, 0);
        a.setDuration(420);
        a.start();
    }

    /** Edge-to-edge: pad a view by the system bars (and keyboard when ime) on top of its padding. */
    static void insets(View v, boolean top, boolean bottom, boolean ime) {
        final int l = v.getPaddingLeft(), t = v.getPaddingTop(), r = v.getPaddingRight(), b = v.getPaddingBottom();
        v.setOnApplyWindowInsetsListener((view, in) -> {
            android.graphics.Insets bars = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            int bottomInset = bars.bottom;
            if (ime) bottomInset = Math.max(bottomInset, in.getInsets(WindowInsets.Type.ime()).bottom);
            view.setPadding(l + bars.left, t + (top ? bars.top : 0), r + bars.right, b + (bottom ? bottomInset : 0));
            return in;
        });
        v.requestApplyInsets();
    }

    static Typeface bold(Context c) {
        return Fonts.weight(c, 700);
    }

    private Ui() {}
}
