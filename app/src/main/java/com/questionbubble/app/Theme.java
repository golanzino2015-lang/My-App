package com.questionbubble.app;

import android.animation.TimeInterpolator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
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
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Design tokens (color, type, motion) and small helpers shared by every screen. */
final class Theme {

    // ---------------------------------------------------------------- color
    static final int BG = 0xFF000000;
    static final int SURFACE = 0xFF0E0E0E;
    static final int SURFACE_2 = 0xFF151515;
    static final int SURFACE_3 = 0xFF1C1C1C;
    static final int HAIRLINE = 0x17FFFFFF;
    static final int HAIRLINE_STRONG = 0x2BFFFFFF;
    static final int FILL = 0x12FFFFFF;
    static final int FILL_PRESSED = 0x24FFFFFF;
    static final int TEXT = 0xFFF5F5F5;
    static final int TEXT_2 = 0x9EFFFFFF;
    static final int TEXT_3 = 0x5CFFFFFF;
    static final int WHITE = 0xFFFFFFFF;
    static final int BLACK = 0xFF000000;
    static final int SCRIM = 0x99000000;

    // ---------------------------------------------------------------- motion
    static final TimeInterpolator EMPHASIZED = new PathInterpolator(0.2f, 0f, 0f, 1f);
    static final TimeInterpolator DECELERATE = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    static final TimeInterpolator ACCELERATE = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);

    // ---------------------------------------------------------------- type
    enum Type {
        DISPLAY(34, 0, -0.025f, TEXT),       // Inter Display
        HEADLINE(27, 0, -0.022f, TEXT),      // Inter Display
        TITLE(20, 600, -0.015f, TEXT),
        TITLE_S(17, 600, -0.012f, TEXT),
        BODY(15, 400, -0.006f, TEXT_2),
        BODY_M(15, 500, -0.008f, TEXT),
        CALLOUT(14, 500, -0.006f, TEXT),
        CAPTION(13, 400, -0.002f, TEXT_2),
        LABEL(12.5f, 500, 0f, TEXT_2),
        OVERLINE(11.5f, 600, 0.07f, TEXT_3);

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
        if (t == Type.DISPLAY || t == Type.HEADLINE) v.setLineSpacing(dp(c, 2), 1f);
    }

    static TextView oneLine(TextView v) {
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        return v;
    }

    // ---------------------------------------------------------------- metrics
    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams lp(int w, int h, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = top;
        return p;
    }

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

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

    /** Standard card: near-black surface, hairline border, 24dp corners. */
    static GradientDrawable card(Context c) {
        return rounded(SURFACE, dp(c, 24), HAIRLINE, Math.max(1, dp(c, 0.8f)));
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
    }

    /** Pressed-state ripple tuned for dark surfaces. */
    static RippleDrawable ripple(GradientDrawable content, float radiusPx) {
        return new RippleDrawable(ColorStateList.valueOf(0x1FFFFFFF), content, rounded(WHITE, radiusPx));
    }

    // ---------------------------------------------------------------- icons
    static ImageView icon(Context c, int res, int color, int sizeDp) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setImageTintList(ColorStateList.valueOf(color));
        iv.setLayoutParams(new FrameLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp), Gravity.CENTER));
        return iv;
    }

    /** Rounded square tile holding an icon, as used in settings rows. */
    static FrameLayout iconTile(Context c, int res, int sizeDp) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(rounded(FILL, dp(c, sizeDp * 0.3f), HAIRLINE, Math.max(1, dp(c, 0.8f))));
        f.addView(icon(c, res, TEXT, Math.round(sizeDp * 0.5f)));
        return f;
    }

    // ---------------------------------------------------------------- interaction
    /** Scale-down on press, spring back on release. Keeps normal click handling. */
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
                            .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f)).start();
                    break;
            }
            return false;
        });
    }

    static void tick(View v) {
        if (Prefs.haptics(v.getContext())) v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    static void tap(View v) {
        if (Prefs.haptics(v.getContext())) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    static void confirm(View v) {
        if (Prefs.haptics(v.getContext())) v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
    }

    static void reject(View v) {
        if (Prefs.haptics(v.getContext())) v.performHapticFeedback(HapticFeedbackConstants.REJECT);
    }

    // ---------------------------------------------------------------- motion helpers
    /** Rise + fade in. */
    static void enter(View v, long delay) {
        v.setAlpha(0f);
        v.setTranslationY(dp(v.getContext(), 18));
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(620)
                .setInterpolator(DECELERATE).start();
    }

    static void shake(View v) {
        float d = dp(v.getContext(), 7);
        android.animation.ObjectAnimator a = android.animation.ObjectAnimator.ofFloat(v,
                View.TRANSLATION_X, 0, d, -d, d * 0.6f, -d * 0.6f, d * 0.25f, 0);
        a.setDuration(420);
        a.start();
    }

    /**
     * Edge-to-edge: pad a view by the system bars (and keyboard when {@code ime}) on top of the
     * padding it already has.
     */
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

    private Theme() {}
}
