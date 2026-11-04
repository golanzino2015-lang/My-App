package com.questionbubble.app;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** "Drop here to hide" target that appears at the bottom while the bubble is dragged. */
final class DismissTarget {

    private final Context c;
    private final WindowManager wm;
    private FrameLayout root;
    private View scrim;
    private FrameLayout circle;
    private ImageView icon;
    private GradientDrawable circleBg;
    private boolean active;
    private int bottomOffset;

    DismissTarget(Context c, WindowManager wm) {
        this.c = c;
        this.wm = wm;
    }

    /** Circle centre in screen pixels. */
    float[] center() {
        android.graphics.Rect b = wm.getCurrentWindowMetrics().getBounds();
        return new float[]{b.centerX(), b.bottom - bottomOffset - Theme.dp(c, 34)};
    }

    void show() {
        if (root != null) return;
        WindowInsets wi = wm.getCurrentWindowMetrics().getWindowInsets();
        int nav = wi.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()).bottom;
        bottomOffset = nav + Theme.dp(c, 36);

        root = new FrameLayout(c);
        scrim = new View(c);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x8C000000});
        scrim.setBackground(g);
        root.addView(scrim, new FrameLayout.LayoutParams(Theme.MATCH, Theme.MATCH));

        circle = new FrameLayout(c);
        circleBg = Theme.rounded(0xCC1A1A1A, Theme.dp(c, 34), Theme.HAIRLINE_STRONG, Math.max(1, Theme.dp(c, 1)));
        circle.setBackground(circleBg);
        icon = Theme.icon(c, R.drawable.ic_x, Theme.TEXT, 24);
        circle.addView(icon);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(Theme.dp(c, 68), Theme.dp(c, 68),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        clp.bottomMargin = bottomOffset;
        root.addView(circle, clp);

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                Theme.MATCH, Theme.dp(c, 240) + nav,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.BOTTOM;
        p.setFitInsetsTypes(0);
        try {
            wm.addView(root, p);
        } catch (Exception e) {
            root = null;
            return;
        }
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(260).start();
        circle.setAlpha(0f);
        circle.setTranslationY(Theme.dp(c, 60));
        circle.animate().alpha(1f).translationY(0).setDuration(420).setInterpolator(Theme.DECELERATE).start();
    }

    void setActive(boolean on) {
        if (circle == null || active == on) return;
        active = on;
        circle.animate().scaleX(on ? 1.28f : 1f).scaleY(on ? 1.28f : 1f).setDuration(320)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
        circleBg.setColor(on ? Theme.WHITE : 0xCC1A1A1A);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(on ? Theme.BLACK : Theme.TEXT));
    }

    /** The bubble was dropped in: little pulse, then go away. */
    void consume() {
        if (circle == null) return;
        circle.animate().scaleX(1.45f).scaleY(1.45f).setDuration(140).withEndAction(this::hide).start();
    }

    void hide() {
        if (root == null) return;
        View r = root;
        View cir = circle;
        root = null;
        circle = null;
        active = false;
        r.animate().alpha(0f).setDuration(240).withEndAction(() -> {
            try {
                wm.removeView(r);
            } catch (Exception ignored) {
            }
        }).start();
        if (cir != null) cir.animate().translationY(Theme.dp(c, 40)).setDuration(240).start();
    }

    void hideNow() {
        if (root == null) return;
        try {
            wm.removeView(root);
        } catch (Exception ignored) {
        }
        root = null;
        circle = null;
        active = false;
    }
}
