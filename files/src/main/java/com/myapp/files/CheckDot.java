package com.myapp.files;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

/** Round selection check: black outline, fills with an ink gradient and draws a white tick. */
final class CheckDot extends View {
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private float t; // 0 = empty, 1 = checked
    private boolean checked;
    private ValueAnimator anim;

    CheckDot(Context c) {
        super(c);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Ui.dp(c, 1.6f));
        ring.setColor(Ui.INK);
        tick.setStyle(Paint.Style.STROKE);
        tick.setStrokeWidth(Ui.dp(c, 2.2f));
        tick.setStrokeCap(Paint.Cap.ROUND);
        tick.setStrokeJoin(Paint.Join.ROUND);
        tick.setColor(Ui.WHITE);
    }

    void set(boolean on, boolean animate) {
        if (on == checked && (anim == null || !anim.isRunning())) {
            t = on ? 1f : 0f;
            invalidate();
            return;
        }
        checked = on;
        if (anim != null) anim.cancel();
        if (!animate) {
            t = on ? 1f : 0f;
            invalidate();
            return;
        }
        anim = ValueAnimator.ofFloat(t, on ? 1f : 0f);
        anim.setDuration(220);
        anim.setInterpolator(Ui.EMPHASIZED);
        anim.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float r = Math.min(w, h) / 2f - ring.getStrokeWidth();
        float cx = w / 2f, cy = h / 2f;
        c.drawCircle(cx, cy, r, ring);
        if (t <= 0f) return;
        fill.setShader(new LinearGradient(0, 0, w, h, 0xFF45454B, 0xFF000000, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * t + ring.getStrokeWidth() / 2f * t, fill);
        path.reset();
        float s = r * 0.5f;
        path.moveTo(cx - s, cy + s * 0.05f);
        path.lineTo(cx - s * 0.25f, cy + s * 0.75f);
        path.lineTo(cx + s * 0.95f, cy - s * 0.6f);
        tick.setAlpha((int) (255 * Math.max(0f, (t - 0.4f) / 0.6f)));
        c.drawPath(path, tick);
    }
}
