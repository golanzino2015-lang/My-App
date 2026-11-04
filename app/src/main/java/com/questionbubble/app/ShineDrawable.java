package com.questionbubble.app;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Dark vertical-gradient panel with a thin outline that has a bright highlight sweeping around it.
 * Works for rounded rectangles and circles.
 */
final class ShineDrawable extends Drawable {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Matrix matrix = new Matrix();
    private final float radius, strokeW;
    private final boolean circle;
    private final int topColor, bottomColor, highlight;

    private float rotation = 300f; // highlight sits top-left when idle
    private ValueAnimator anim;

    ShineDrawable(float radius, float strokeW, boolean circle, int topColor, int bottomColor, int highlight) {
        this.radius = radius;
        this.strokeW = strokeW;
        this.circle = circle;
        this.topColor = topColor;
        this.bottomColor = bottomColor;
        this.highlight = highlight;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(strokeW);
    }

    @Override
    protected void onBoundsChange(Rect b) {
        rect.set(b);
        rect.inset(strokeW / 2f, strokeW / 2f);
        fill.setShader(new LinearGradient(0, b.top, 0, b.bottom, topColor, bottomColor, Shader.TileMode.CLAMP));
        stroke.setShader(new SweepGradient(b.exactCenterX(), b.exactCenterY(),
                new int[]{Ui.SEP, Ui.SEP, highlight, Ui.SEP, Ui.SEP},
                new float[]{0f, 0.38f, 0.5f, 0.62f, 1f}));
        applyRotation();
    }

    private void applyRotation() {
        Shader s = stroke.getShader();
        if (s == null) return;
        Rect b = getBounds();
        matrix.setRotate(rotation, b.exactCenterX(), b.exactCenterY());
        s.setLocalMatrix(matrix);
    }

    @Override
    public void draw(Canvas canvas) {
        float r = circle ? rect.height() / 2f : radius;
        canvas.drawRoundRect(rect, r, r, fill);
        canvas.drawRoundRect(rect, r, r, stroke);
    }

    /** Start the highlight orbiting; one lap every {@code periodMs}. */
    void start(long periodMs) {
        stop();
        float from = rotation;
        anim = ValueAnimator.ofFloat(from, from + 360f);
        anim.setDuration(periodMs);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            rotation = ((float) a.getAnimatedValue()) % 360f;
            applyRotation();
            invalidateSelf();
        });
        anim.start();
    }

    void stop() {
        if (anim != null) {
            anim.cancel();
            anim = null;
        }
    }

    /** Animate only while the view is on screen. */
    static void animateWhileAttached(View v, ShineDrawable d, long periodMs) {
        v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                d.start(periodMs);
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                d.stop();
            }
        });
        if (v.isAttachedToWindow()) d.start(periodMs);
    }

    @Override
    public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
        stroke.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        fill.setColorFilter(cf);
        stroke.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
