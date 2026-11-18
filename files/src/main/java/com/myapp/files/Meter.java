package com.myapp.files;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Rounded progress bar with a gradient fill; animates to new values. */
final class Meter extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final int[] colors;
    private float value, shown;
    private ValueAnimator anim;
    private boolean indeterminate;
    private float phase;

    /** dark = for black surfaces (white fill); otherwise a black fill on a light track. */
    Meter(Context c, boolean dark) {
        super(c);
        track.setColor(dark ? 0x2EFFFFFF : 0xFFEDEDF0);
        colors = dark ? new int[]{0xFFFFFFFF, 0xFFD8D8DE} : new int[]{0xFF45454B, 0xFF000000};
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Ui.stroke(c));
        edge.setColor(dark ? 0x00000000 : Ui.INK);
    }

    void set(float v, boolean animate) {
        value = Math.max(0f, Math.min(1f, v));
        indeterminate = false;
        if (anim != null) anim.cancel();
        if (!animate) {
            shown = value;
            invalidate();
            return;
        }
        anim = ValueAnimator.ofFloat(shown, value);
        anim.setDuration(900);
        anim.setInterpolator(Ui.DECELERATE);
        anim.addUpdateListener(a -> {
            shown = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    void indeterminate() {
        indeterminate = true;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1100);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (anim != null) anim.cancel();
    }

    @Override
    protected void onDraw(Canvas c) {
        float h = getHeight(), w = getWidth(), rad = h / 2f;
        float e = edge.getStrokeWidth() / 2f;
        r.set(e, e, w - e, h - e);
        c.drawRoundRect(r, rad, rad, track);
        float x0 = 0, x1;
        if (indeterminate) {
            float seg = w * 0.35f;
            x0 = -seg + (w + seg) * phase;
            x1 = x0 + seg;
            x0 = Math.max(0, x0);
            x1 = Math.min(w, x1);
        } else {
            x1 = w * shown;
            if (x1 > 0 && x1 < h) x1 = h;
        }
        if (x1 > x0) {
            fill.setShader(new LinearGradient(0, 0, w, 0, colors[0], colors[1], Shader.TileMode.CLAMP));
            r.set(x0, 0, x1, h);
            c.drawRoundRect(r, rad, rad, fill);
        }
        r.set(e, e, w - e, h - e);
        c.drawRoundRect(r, rad, rad, edge);
    }
}
