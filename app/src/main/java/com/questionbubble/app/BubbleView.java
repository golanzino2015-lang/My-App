package com.questionbubble.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** The floating button: dark glass circle, white mark, shine ring that spins while working. */
final class BubbleView extends View {

    private final ShineDrawable bg;
    private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private boolean busy;
    private float spin;
    private float markAlpha = 1f;
    private ValueAnimator spinAnim;

    BubbleView(Context c) {
        super(c);
        bg = new ShineDrawable(0, Ui.dp(c, 1.3f), true, 0xF51E1E1E, 0xF50A0A0A, 0xCCFFFFFF);
        bg.setCallback(this);
        mark.setStyle(Paint.Style.STROKE);
        mark.setStrokeCap(Paint.Cap.ROUND);
        mark.setColor(Ui.WHITE);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeCap(Paint.Cap.ROUND);
        ring.setColor(Ui.WHITE);
        ring.setStrokeWidth(Ui.dp(c, 2));
    }

    void setBusy(boolean b) {
        if (busy == b) return;
        busy = b;
        if (b) {
            bg.start(1400);
            spinAnim = ValueAnimator.ofFloat(0f, 360f);
            spinAnim.setDuration(900);
            spinAnim.setRepeatCount(ValueAnimator.INFINITE);
            spinAnim.setInterpolator(new LinearInterpolator());
            spinAnim.addUpdateListener(a -> {
                spin = (float) a.getAnimatedValue();
                invalidate();
            });
            spinAnim.start();
        } else {
            bg.stop();
            if (spinAnim != null) spinAnim.cancel();
        }
        ValueAnimator fade = ValueAnimator.ofFloat(markAlpha, b ? 0f : 1f);
        fade.setDuration(220);
        fade.addUpdateListener(a -> {
            markAlpha = (float) a.getAnimatedValue();
            invalidate();
        });
        fade.start();
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return who == bg || super.verifyDrawable(who);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        bg.setBounds(0, 0, w, h);
    }

    @Override
    protected void onDetachedFromWindow() {
        bg.stop();
        if (spinAnim != null) spinAnim.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        bg.draw(canvas);
        float s = getWidth();
        float cx = s / 2f, cy = s / 2f;

        if (markAlpha > 0f) {
            // "Q" mark: ring + tail
            mark.setAlpha(Math.round(255 * markAlpha));
            mark.setStrokeWidth(s * 0.055f);
            float r = s * 0.17f;
            float ox = cx - s * 0.025f, oy = cy - s * 0.025f;
            canvas.drawCircle(ox, oy, r, mark);
            float t0 = r * 0.72f, t1 = r * 1.45f;
            canvas.drawLine(ox + t0, oy + t0, ox + t1, oy + t1, mark);
        }
        if (busy || markAlpha < 1f) {
            ring.setAlpha(Math.round(255 * (1f - markAlpha)));
            float r = s * 0.2f;
            oval.set(cx - r, cy - r, cx + r, cy + r);
            canvas.drawArc(oval, spin - 90f, 110f, false, ring);
        }
    }
}
