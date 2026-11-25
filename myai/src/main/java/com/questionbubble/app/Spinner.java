package com.questionbubble.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Choreographer;
import android.view.View;

/** Material-style indeterminate ring: rotating arc whose length breathes. */
final class Spinner extends View implements Choreographer.FrameCallback {

    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private long start;
    private boolean running;

    Spinner(Context c, int color) {
        super(c);
        float w = Theme.dp(c, 2.2f);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(w);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(color);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(w);
        track.setColor((color & 0x00FFFFFF) | 0x24000000);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start = System.nanoTime();
        running = true;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!running) return;
        if (getVisibility() == VISIBLE) invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = arc.getStrokeWidth();
        r.set(s, s, getWidth() - s, getHeight() - s);
        canvas.drawOval(r, track);
        float t = (System.nanoTime() - start) / 1e9f;
        float rot = (t * 300f) % 360f;
        float phase = (float) (0.5 - 0.5 * Math.cos(t * Math.PI * 1.25));
        float sweep = 28f + 230f * phase;
        float head = rot + phase * 140f;
        canvas.drawArc(r, head - 90f, sweep, false, arc);
    }
}
