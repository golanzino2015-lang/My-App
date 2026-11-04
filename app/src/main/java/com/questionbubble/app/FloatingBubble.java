package com.questionbubble.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

/**
 * The floating orb window: drag with spring physics, fling to either side, snap to edges and
 * corners, drag onto the bottom target to hide, dims when idle.
 */
final class FloatingBubble {

    interface Callbacks {
        void onTap();
        void onLongPress();
        void onDragStart();
        void onDismissed();
    }

    private static final long DIM_AFTER_MS = 3500;
    private static final float DIM_ALPHA = 0.55f;

    private final Context c;
    private final WindowManager wm;
    private final Callbacks cb;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final DismissTarget target;

    private OrbView orb;
    private WindowManager.LayoutParams lp;
    private int orbD, pad, win;
    private boolean busy, menuOpen;

    // spring state (window x/y)
    private float x, y, vx, vy, tx, ty, k = 420f, zeta = 0.72f;
    private boolean springing;
    private long lastFrame;
    private final Choreographer.FrameCallback springFrame = this::stepSpring;

    private final Runnable dimRunnable = () -> {
        if (orb != null && !busy && !menuOpen) {
            orb.animate().alpha(DIM_ALPHA).setDuration(700).setInterpolator(Theme.EMPHASIZED).start();
        }
    };

    FloatingBubble(Context c, WindowManager wm, Callbacks cb) {
        this.c = c;
        this.wm = wm;
        this.cb = cb;
        this.target = new DismissTarget(c, wm);
    }

    boolean isShowing() {
        return orb != null;
    }

    // ---------------------------------------------------------------- geometry

    Rect safeArea() {
        WindowMetrics m = wm.getCurrentWindowMetrics();
        Rect b = new Rect(m.getBounds());
        Insets in = m.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        b.left += in.left;
        b.top += in.top;
        b.right -= in.right;
        b.bottom -= in.bottom;
        return b;
    }

    /** The orb's visible circle bounds, in screen pixels. */
    Rect orbBounds() {
        int l = Math.round(x) + pad, t = Math.round(y) + pad;
        return new Rect(l, t, l + orbD, t + orbD);
    }

    boolean onLeftSide() {
        return orbBounds().centerX() < safeArea().centerX();
    }

    // ---------------------------------------------------------------- show / hide

    @SuppressLint("ClickableViewAccessibility")
    void show(int sizeIndex) {
        if (orb != null) return;
        orbD = Theme.dp(c, sizeIndex == 0 ? 44 : sizeIndex == 2 ? 62 : 52);
        pad = Theme.dp(c, 12);
        win = orbD + pad * 2;

        orb = new OrbView(c, 12);
        orb.setSleepAfter(6000);
        lp = new WindowManager.LayoutParams(win, win,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;

        Rect a = safeArea();
        int[] saved = Prefs.bubblePos(c);
        if (saved[0] != Integer.MIN_VALUE) {
            x = saved[0];
            y = saved[1];
        } else {
            x = a.right - Theme.dp(c, 8) - orbD - pad;
            y = a.top + a.height() * 0.32f;
        }
        float[] t = snapTarget(x, y);
        x = t[0];
        y = t[1];
        lp.x = Math.round(x);
        lp.y = Math.round(y);

        orb.setOnTouchListener(new TouchHandler());
        wm.addView(orb, lp);

        orb.setAlpha(0f);
        orb.setScaleX(0.3f);
        orb.setScaleY(0.3f);
        orb.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(560)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.8f)).start();
        scheduleDim();
    }

    void remove() {
        main.removeCallbacks(dimRunnable);
        Choreographer.getInstance().removeFrameCallback(springFrame);
        springing = false;
        target.hide();
        if (orb != null) {
            View v = orb;
            orb = null;
            v.animate().alpha(0f).scaleX(0.4f).scaleY(0.4f).setDuration(220)
                    .setInterpolator(Theme.ACCELERATE).withEndAction(() -> safeRemove(v)).start();
        }
    }

    void removeNow() {
        main.removeCallbacks(dimRunnable);
        Choreographer.getInstance().removeFrameCallback(springFrame);
        springing = false;
        target.hideNow();
        if (orb != null) {
            safeRemove(orb);
            orb = null;
        }
    }

    /** Temporarily hide (e.g. while capturing the screen). */
    void setHidden(boolean hidden) {
        if (orb == null) return;
        orb.setVisibility(hidden ? View.INVISIBLE : View.VISIBLE);
        if (!hidden) {
            orb.setAlpha(0f);
            orb.animate().alpha(1f).setDuration(260).start();
            wake();
        }
    }

    void setBusy(boolean b) {
        busy = b;
        if (orb == null) return;
        orb.setEnergy(b ? 1f : 0f);
        orb.setGlow(b ? 0.8f : 0f);
        wake();
    }

    void setMenuOpen(boolean open) {
        menuOpen = open;
        if (orb == null) return;
        orb.setGlow(open ? 0.6f : (busy ? 0.8f : 0f));
        wake();
    }

    void celebrate() {
        if (orb == null) return;
        orb.ripple();
        haptic(HapticFeedbackConstants.CONFIRM);
        wake();
    }

    void errorShake() {
        if (orb == null) return;
        haptic(HapticFeedbackConstants.REJECT);
        float d = Theme.dp(c, 5);
        android.animation.ObjectAnimator.ofFloat(orb, View.TRANSLATION_X, 0, d, -d, d * 0.6f, -d * 0.6f, 0)
                .setDuration(380).start();
        wake();
    }

    private void wake() {
        if (orb == null) return;
        main.removeCallbacks(dimRunnable);
        if (orb.getAlpha() < 1f && orb.getVisibility() == View.VISIBLE && orb.getScaleX() >= 0.99f) {
            orb.animate().alpha(1f).setDuration(180).start();
        }
        orb.wake();
        scheduleDim();
    }

    private void scheduleDim() {
        main.removeCallbacks(dimRunnable);
        main.postDelayed(dimRunnable, DIM_AFTER_MS);
    }

    void haptic(int constant) {
        if (orb != null && Prefs.haptics(c)) orb.performHapticFeedback(constant);
    }

    // ---------------------------------------------------------------- snapping + spring

    /** Nearest side edge; inside the top/bottom band it locks into that corner. */
    private float[] snapTarget(float wx, float wy) {
        Rect a = safeArea();
        int gap = Theme.dp(c, 8);
        float cx = wx + win / 2f, cy = wy + win / 2f;
        float leftX = a.left + gap - pad;
        float rightX = a.right - gap - orbD - pad;
        float minY = a.top + gap - pad;
        float maxY = a.bottom - gap - orbD - pad;
        float zone = a.height() * 0.16f;
        float nx = cx < a.centerX() ? leftX : rightX;
        float ny;
        if (cy < a.top + zone) ny = minY;
        else if (cy > a.bottom - zone) ny = maxY;
        else ny = Math.max(minY, Math.min(maxY, wy));
        return new float[]{nx, ny};
    }

    private void springTo(float nx, float ny, float stiffness, float damping) {
        tx = nx;
        ty = ny;
        k = stiffness;
        zeta = damping;
        if (!springing) {
            springing = true;
            lastFrame = 0;
            Choreographer.getInstance().postFrameCallback(springFrame);
        }
    }

    private void stepSpring(long now) {
        if (!springing || orb == null) {
            springing = false;
            return;
        }
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.032f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        int steps = 4;
        float h = dt / steps;
        float cdamp = 2f * zeta * (float) Math.sqrt(k);
        for (int i = 0; i < steps; i++) {
            vx += (-k * (x - tx) - cdamp * vx) * h;
            vy += (-k * (y - ty) - cdamp * vy) * h;
            x += vx * h;
            y += vy * h;
        }
        lp.x = Math.round(x);
        lp.y = Math.round(y);
        safeUpdate();
        boolean settled = Math.abs(x - tx) < 0.5f && Math.abs(y - ty) < 0.5f
                && Math.abs(vx) < 20f && Math.abs(vy) < 20f;
        if (settled && !dragging) {
            x = tx;
            y = ty;
            vx = vy = 0;
            lp.x = Math.round(x);
            lp.y = Math.round(y);
            safeUpdate();
            springing = false;
            Prefs.saveBubblePos(c, lp.x, lp.y);
            return;
        }
        Choreographer.getInstance().postFrameCallback(springFrame);
    }

    /** Re-snap after rotation or a size change. */
    void resnap() {
        if (orb == null) return;
        float[] t = snapTarget(x, y);
        springTo(t[0], t[1], 300f, 0.8f);
    }

    // ---------------------------------------------------------------- touch

    private boolean dragging;

    private final class TouchHandler implements View.OnTouchListener {
        float downX, downY, startX, startY;
        boolean longPressed, captured;
        VelocityTracker vt;
        final int slop = ViewConfiguration.get(c).getScaledTouchSlop();
        final Runnable longPress = () -> {
            if (dragging) return;
            longPressed = true;
            orb.squeeze(false);
            haptic(HapticFeedbackConstants.LONG_PRESS);
            cb.onLongPress();
        };

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    wake();
                    springing = false;
                    Choreographer.getInstance().removeFrameCallback(springFrame);
                    vx = vy = 0;
                    downX = e.getRawX();
                    downY = e.getRawY();
                    startX = x;
                    startY = y;
                    dragging = false;
                    longPressed = false;
                    captured = false;
                    vt = VelocityTracker.obtain();
                    vt.addMovement(e);
                    orb.squeeze(true);
                    main.postDelayed(longPress, 480);
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (vt != null) vt.addMovement(e);
                    if (longPressed) return true;
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    if (!dragging && Math.hypot(dx, dy) > slop) {
                        dragging = true;
                        main.removeCallbacks(longPress);
                        orb.squeeze(false);
                        cb.onDragStart();
                        target.show();
                    }
                    if (dragging) {
                        float wantX = startX + dx, wantY = startY + dy;
                        float ocx = wantX + win / 2f, ocy = wantY + win / 2f;
                        float[] tc = target.center();
                        double dist = Math.hypot(ocx - tc[0], ocy - tc[1]);
                        if (!captured && dist < Theme.dp(c, 84)) {
                            captured = true;
                            target.setActive(true);
                            haptic(HapticFeedbackConstants.CLOCK_TICK);
                        } else if (captured && dist > Theme.dp(c, 110)) {
                            captured = false;
                            target.setActive(false);
                        }
                        if (captured) springTo(tc[0] - win / 2f, tc[1] - win / 2f, 700f, 0.75f);
                        else springTo(wantX, wantY, 1600f, 0.95f);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    main.removeCallbacks(longPress);
                    orb.squeeze(false);
                    float fvx = 0, fvy = 0;
                    if (vt != null) {
                        vt.addMovement(e);
                        vt.computeCurrentVelocity(1000);
                        fvx = vt.getXVelocity();
                        fvy = vt.getYVelocity();
                        vt.recycle();
                        vt = null;
                    }
                    if (longPressed) {
                        scheduleDim();
                        return true;
                    }
                    if (!dragging) {
                        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                            haptic(HapticFeedbackConstants.VIRTUAL_KEY);
                            cb.onTap();
                        }
                        scheduleDim();
                        return true;
                    }
                    dragging = false;
                    if (captured) {
                        target.consume();
                        View o = orb;
                        haptic(HapticFeedbackConstants.CONFIRM);
                        o.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(260)
                                .setInterpolator(Theme.ACCELERATE).withEndAction(() -> {
                                    removeNow();
                                    cb.onDismissed();
                                }).start();
                        return true;
                    }
                    target.hide();
                    // fling: project where the throw would land, then spring there
                    float projX = x + fvx * 0.16f, projY = y + fvy * 0.16f;
                    float[] t = snapTarget(projX, projY);
                    vx = fvx;
                    vy = fvy;
                    springTo(t[0], t[1], 380f, 0.68f);
                    scheduleDim();
                    return true;
                }
            }
            return false;
        }
    }

    private void safeUpdate() {
        try {
            if (orb != null && orb.isAttachedToWindow()) wm.updateViewLayout(orb, lp);
        } catch (Exception ignored) {
        }
    }

    private void safeRemove(View v) {
        try {
            wm.removeView(v);
        } catch (Exception ignored) {
        }
    }
}
