package com.questionbubble.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Choreographer;
import android.view.View;

/**
 * Animated orb. In the app it animates continuously; as the floating bubble it can
 * {@link #setSleepAfter go to sleep} when idle (to save battery) and {@link #wake} on touch.
 */
final class OrbView extends View implements Choreographer.FrameCallback {

    private final OrbRenderer renderer;
    private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float margin;

    private float t = (float) (Math.random() * 100);
    private float energy, energyTarget;
    private float glow, glowTarget;
    private float scale = 1f, scaleTarget = 1f, scaleVel;
    private float ripple = -1f;
    private float speed = 1f;
    private boolean drawShadow = true;
    private boolean continuous = true;
    private long sleepAfterMs;
    private long lastWake;
    private long lastFrame;
    private boolean running;

    OrbView(Context c, float marginDp) {
        super(c);
        renderer = new OrbRenderer(c.getResources().getDisplayMetrics().density);
        margin = Theme.dp(c, marginDp);
        ripplePaint.setStyle(Paint.Style.STROKE);
        ripplePaint.setColor(Theme.WHITE);
        lastWake = System.currentTimeMillis();
    }

    void setDrawShadow(boolean s) {
        drawShadow = s;
        invalidate();
    }

    /** Overlay mode: stop animating after this long without {@link #wake()}. 0 = never sleep. */
    void setSleepAfter(long ms) {
        sleepAfterMs = ms;
        continuous = ms <= 0;
    }

    void wake() {
        lastWake = System.currentTimeMillis();
        start();
    }

    void setEnergy(float e) {
        energyTarget = e;
        wake();
    }

    void setGlow(float g) {
        glowTarget = g;
        wake();
    }

    void squeeze(boolean pressed) {
        scaleTarget = pressed ? 0.88f : 1f;
        wake();
    }

    /** Expanding ring, used to celebrate a finished answer. */
    void ripple() {
        ripple = 0f;
        wake();
    }

    float orbRadius() {
        return Math.min(getWidth(), getHeight()) / 2f - margin;
    }

    private void start() {
        if (running || !isAttachedToWindow()) return;
        running = true;
        lastFrame = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastWake = System.currentTimeMillis();
        start();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void doFrame(long now) {
        if (!running) return;
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1e9f);
        lastFrame = now;

        boolean sleeping = !continuous && energyTarget < 0.01f
                && System.currentTimeMillis() - lastWake > sleepAfterMs;
        float speedTarget = sleeping ? 0f : 1f;
        speed += (speedTarget - speed) * Math.min(1f, dt * 2.5f);

        energy += (energyTarget - energy) * Math.min(1f, dt * 5f);
        glow += (glowTarget - glow) * Math.min(1f, dt * 6f);

        // springy press scale
        float k = 520f, damp = 2f * 0.62f * (float) Math.sqrt(k);
        scaleVel += (-k * (scale - scaleTarget) - damp * scaleVel) * dt;
        scale += scaleVel * dt;

        t += dt * speed * (1f + 2.6f * energy);
        if (ripple >= 0f) {
            ripple += dt / 0.75f;
            if (ripple > 1f) ripple = -1f;
        }
        invalidate();

        boolean settled = Math.abs(scale - scaleTarget) < 0.001f && Math.abs(scaleVel) < 0.01f
                && Math.abs(energy - energyTarget) < 0.005f && Math.abs(glow - glowTarget) < 0.005f
                && ripple < 0f;
        if (sleeping && speed < 0.02f && settled) {
            running = false;
            return;
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = orbRadius();
        canvas.save();
        canvas.scale(scale, scale, cx, cy);
        renderer.draw(canvas, cx, cy, r, t, energy, glow, drawShadow);
        canvas.restore();
        if (ripple >= 0f) {
            float p = ripple;
            ripplePaint.setAlpha(Math.round(200 * (1f - p) * (1f - p)));
            ripplePaint.setStrokeWidth(Theme.dp(getContext(), 2.5f) * (1f - p) + 1f);
            canvas.drawCircle(cx, cy, r + p * (margin * 0.92f), ripplePaint);
        }
    }
}
