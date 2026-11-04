package com.questionbubble.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.view.View;

/** White disc that pops in, then a black check mark draws itself. */
final class CheckView extends View {

    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path check = new Path();
    private final Path seg = new Path();
    private PathMeasure measure;
    private float discP = 1f, checkP = 1f;

    CheckView(Context c) {
        super(c);
        disc.setColor(Theme.WHITE);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(Theme.BLACK);
    }

    void play() {
        discP = 0f;
        checkP = 0f;
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(320);
        a.setInterpolator(new android.view.animation.OvershootInterpolator(2f));
        a.addUpdateListener(v -> {
            discP = (float) v.getAnimatedValue();
            invalidate();
        });
        a.start();
        ValueAnimator b = ValueAnimator.ofFloat(0f, 1f);
        b.setStartDelay(160);
        b.setDuration(320);
        b.setInterpolator(Theme.EMPHASIZED);
        b.addUpdateListener(v -> {
            checkP = (float) v.getAnimatedValue();
            invalidate();
        });
        b.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float s = Math.min(w, h);
        check.reset();
        check.moveTo(s * 0.30f, s * 0.52f);
        check.lineTo(s * 0.44f, s * 0.66f);
        check.lineTo(s * 0.71f, s * 0.37f);
        measure = new PathMeasure(check, false);
        stroke.setStrokeWidth(s * 0.09f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = Math.min(getWidth(), getHeight());
        canvas.drawCircle(s / 2f, s / 2f, s / 2f * discP, disc);
        if (measure != null && checkP > 0f) {
            seg.reset();
            measure.getSegment(0, measure.getLength() * checkP, seg, true);
            canvas.drawPath(seg, stroke);
        }
    }
}
