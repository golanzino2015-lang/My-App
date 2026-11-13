package com.questionbubble.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Black pill at the top of the screen that morphs in to show progress and results. Tap to dismiss. */
final class Island {

    enum Kind { LOADING, SUCCESS, ERROR, INFO }

    private final Context c;
    private final WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable hideRunnable = this::hide;

    private FrameLayout root;
    private LinearLayout pill, textCol;
    private FrameLayout iconBox;
    private TextView title, body;

    Island(Context c, WindowManager wm) {
        this.c = c;
        this.wm = wm;
    }

    void show(Kind kind, String t, String b, long autoHideMs) {
        main.removeCallbacks(hideRunnable);
        boolean fresh = root == null;
        if (fresh && !build()) return;

        Runnable apply = () -> {
            iconBox.removeAllViews();
            FrameLayout.LayoutParams full = new FrameLayout.LayoutParams(Theme.MATCH, Theme.MATCH);
            if (kind == Kind.SUCCESS) {
                CheckView cv = new CheckView(c);
                iconBox.setBackground(null);
                iconBox.addView(cv, full);
                cv.play();
            } else {
                iconBox.setBackground(Theme.rounded(0x1FFFFFFF, Theme.dp(c, 18)));
                if (kind == Kind.LOADING) {
                    iconBox.addView(new Spinner(c, Theme.ACCENT),
                            new FrameLayout.LayoutParams(Theme.dp(c, 18), Theme.dp(c, 18), Gravity.CENTER));
                } else {
                    iconBox.addView(Theme.icon(c, kind == Kind.ERROR ? R.drawable.ic_alert : R.drawable.ic_sparkles,
                            Theme.ACCENT, 18));
                }
            }
            title.setText(t);
            body.setText(b == null ? "" : b);
            body.setVisibility(b == null || b.isEmpty() ? View.GONE : View.VISIBLE);
        };

        if (fresh) {
            apply.run();
            pill.setAlpha(0f);
            pill.setScaleX(0.45f);
            pill.setScaleY(0.6f);
            pill.setTranslationY(-Theme.dp(c, 14));
            pill.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0).setDuration(560)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(0.8f)).start();
            textCol.setAlpha(0f);
            textCol.animate().alpha(1f).setStartDelay(160).setDuration(300).start();
        } else {
            textCol.animate().alpha(0f).setStartDelay(0).setDuration(110).withEndAction(() -> {
                apply.run();
                textCol.animate().alpha(1f).setDuration(220).start();
            }).start();
            pill.setScaleX(0.96f);
            pill.setScaleY(0.96f);
            pill.animate().scaleX(1f).scaleY(1f).setDuration(420)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
            if (kind == Kind.ERROR) Theme.shake(pill);
        }
        if (autoHideMs > 0) main.postDelayed(hideRunnable, autoHideMs);
    }

    @SuppressLint("ClickableViewAccessibility")
    private boolean build() {
        int maxW = c.getResources().getDisplayMetrics().widthPixels - Theme.dp(c, 32);
        int shadow = Theme.dp(c, 20);

        root = new FrameLayout(c);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setPadding(shadow, shadow / 2, shadow, shadow);

        pill = new LinearLayout(c);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(Theme.dp(c, 8), Theme.dp(c, 8), Theme.dp(c, 22), Theme.dp(c, 8));
        pill.setBackground(Theme.rounded(Theme.PANEL, Theme.dp(c, 30), Theme.HAIRLINE_STRONG, Math.max(1, Theme.dp(c, 0.8f))));
        Theme.elevate(pill, 18, 30);
        pill.setLayoutTransition(new android.animation.LayoutTransition());
        pill.setMinimumWidth(Theme.dp(c, 200));

        iconBox = new FrameLayout(c);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Theme.dp(c, 36), Theme.dp(c, 36));
        ip.rightMargin = Theme.dp(c, 12);
        pill.addView(iconBox, ip);

        textCol = new LinearLayout(c);
        textCol.setOrientation(LinearLayout.VERTICAL);
        title = Theme.oneLine(Theme.text(c, "", Theme.Type.CALLOUT));
        title.setTypeface(Fonts.weight(c, 600));
        title.setMaxWidth(maxW - Theme.dp(c, 110));
        body = Theme.text(c, "", Theme.Type.LABEL);
        body.setMaxLines(3);
        body.setEllipsize(TextUtils.TruncateAt.END);
        body.setLineSpacing(Theme.dp(c, 2), 1f);
        body.setMaxWidth(maxW - Theme.dp(c, 110));
        textCol.addView(title);
        textCol.addView(body, Theme.lp(Theme.WRAP, Theme.WRAP, Theme.dp(c, 3)));
        pill.addView(textCol);

        root.addView(pill, new FrameLayout.LayoutParams(Theme.WRAP, Theme.WRAP, Gravity.CENTER_HORIZONTAL));
        pill.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) hide();
            return true;
        });

        WindowInsets wi = wm.getCurrentWindowMetrics().getWindowInsets();
        int top = wi.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top;

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        p.y = top + Theme.dp(c, 6) - shadow / 2;
        try {
            wm.addView(root, p);
            return true;
        } catch (Exception e) {
            root = null;
            return false;
        }
    }

    void hide() {
        main.removeCallbacks(hideRunnable);
        if (root == null) return;
        View r = root;
        View p = pill;
        root = null;
        p.animate().alpha(0f).scaleX(0.55f).scaleY(0.7f).translationY(-Theme.dp(c, 10)).setStartDelay(0)
                .setDuration(240).setInterpolator(Theme.ACCELERATE).withEndAction(() -> {
                    try {
                        wm.removeView(r);
                    } catch (Exception ignored) {
                    }
                }).start();
    }

    void hideNow() {
        main.removeCallbacks(hideRunnable);
        if (root == null) return;
        try {
            wm.removeView(root);
        } catch (Exception ignored) {
        }
        root = null;
    }
}
