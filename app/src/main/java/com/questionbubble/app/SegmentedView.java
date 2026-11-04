package com.questionbubble.app;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Segmented control with a white thumb that glides between options. */
final class SegmentedView extends View {

    interface Listener {
        void onSelected(int index);
    }

    private final String[] options;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textOn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textOff = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int selected;
    private float pos; // animated index
    private Listener listener;
    private ValueAnimator anim;

    SegmentedView(Context c, String[] options, int selected) {
        super(c);
        this.options = options;
        this.selected = selected;
        this.pos = selected;
        track.setColor(Theme.FILL);
        thumb.setColor(Theme.WHITE);
        thumb.setShadowLayer(Theme.dp(c, 3), 0, Theme.dp(c, 1), 0x66000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        float sp = c.getResources().getDisplayMetrics().scaledDensity;
        textOn.setTextSize(13.5f * sp);
        textOn.setTypeface(Fonts.weight(c, 600));
        textOn.setColor(Theme.BLACK);
        textOn.setTextAlign(Paint.Align.CENTER);
        textOff.setTextSize(13.5f * sp);
        textOff.setTypeface(Fonts.weight(c, 500));
        textOff.setColor(Theme.TEXT_2);
        textOff.setTextAlign(Paint.Align.CENTER);
        setClickable(true);
    }

    void setListener(Listener l) {
        listener = l;
    }

    /** Move the thumb without notifying the listener. */
    void select(int i) {
        if (i == selected) return;
        selected = i;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(pos, i);
        anim.setDuration(420);
        anim.setInterpolator(new android.view.animation.OvershootInterpolator(0.9f));
        anim.addUpdateListener(a -> {
            pos = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize(Theme.dp(getContext(), 240), w), Theme.dp(getContext(), 40));
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) return true;
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            int i = (int) (e.getX() / (getWidth() / (float) options.length));
            i = Math.max(0, Math.min(options.length - 1, i));
            if (i != selected) {
                selected = i;
                Theme.tick(this);
                if (anim != null) anim.cancel();
                anim = ValueAnimator.ofFloat(pos, i);
                anim.setDuration(420);
                anim.setInterpolator(new android.view.animation.OvershootInterpolator(0.9f));
                anim.addUpdateListener(a -> {
                    pos = (float) a.getAnimatedValue();
                    invalidate();
                });
                anim.start();
                if (listener != null) listener.onSelected(i);
            }
            return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight(), r = h / 2f;
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, r, r, track);
        float seg = w / options.length;
        float pad = Theme.dp(getContext(), 3);
        rect.set(pos * seg + pad, pad, pos * seg + seg - pad, h - pad);
        canvas.drawRoundRect(rect, r - pad, r - pad, thumb);

        float baseline = h / 2f - (textOff.descent() + textOff.ascent()) / 2f;
        for (int i = 0; i < options.length; i++) {
            float cx = i * seg + seg / 2f;
            float overlap = Math.max(0f, 1f - Math.abs(pos - i));
            textOff.setAlpha(Math.round(255 * (1f - overlap)));
            textOn.setAlpha(Math.round(255 * overlap));
            canvas.drawText(options[i], cx, baseline, textOff);
            canvas.drawText(options[i], cx, baseline, textOn);
        }
    }
}
