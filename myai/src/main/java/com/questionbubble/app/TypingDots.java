package com.questionbubble.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Choreographer;
import android.view.View;

/** Three dots rising and falling in a wave, used while waiting for an answer. */
final class TypingDots extends View implements Choreographer.FrameCallback {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long start;
    private boolean running;

    TypingDots(Context c) {
        super(c);
        p.setColor(Theme.TEXT);
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(Theme.dp(getContext(), 44), Theme.dp(getContext(), 16));
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
    public void doFrame(long t) {
        if (!running) return;
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float t = (System.nanoTime() - start) / 1e9f;
        float r = Theme.dp(getContext(), 3.2f);
        float gap = Theme.dp(getContext(), 11);
        float cy = getHeight() / 2f;
        for (int i = 0; i < 3; i++) {
            float ph = (float) Math.sin((t * 2 * Math.PI / 1.1) - i * 0.9);
            float k = Math.max(0f, ph);
            p.setAlpha(Math.round(255 * (0.3f + 0.7f * k)));
            canvas.drawCircle(r + i * gap, cy - k * Theme.dp(getContext(), 3), r, p);
        }
    }
}
