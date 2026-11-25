package com.questionbubble.app;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/** Monochrome switch: white track with black knob when on; the knob stretches while pressed. */
final class ToggleView extends View {

    interface Listener {
        void onChanged(boolean on);
    }

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hair = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private boolean on;
    private float progress;  // 0 off .. 1 on
    private float stretch;   // 0..1 while pressed
    private Listener listener;
    private ValueAnimator anim, pressAnim;

    ToggleView(Context c, boolean initial) {
        super(c);
        on = initial;
        progress = initial ? 1f : 0f;
        hair.setStyle(Paint.Style.STROKE);
        hair.setStrokeWidth(Math.max(1, Theme.dp(c, 0.8f)));
        hair.setColor(Theme.HAIRLINE_STRONG);
        knob.setShadowLayer(Theme.dp(c, 2), 0, Theme.dp(c, 1), 0x55000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null); // for the knob shadow
        setClickable(true);
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setChecked(boolean value, boolean animate) {
        if (on == value) return;
        on = value;
        if (!animate) {
            progress = on ? 1f : 0f;
            invalidate();
            return;
        }
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(progress, on ? 1f : 0f);
        anim.setDuration(360);
        anim.setInterpolator(new OvershootInterpolator(1.4f));
        anim.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    boolean isChecked() {
        return on;
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(Theme.dp(getContext(), 52), Theme.dp(getContext(), 32));
    }

    private void animateStretch(float to) {
        if (pressAnim != null) pressAnim.cancel();
        pressAnim = ValueAnimator.ofFloat(stretch, to);
        pressAnim.setDuration(160);
        pressAnim.addUpdateListener(a -> {
            stretch = (float) a.getAnimatedValue();
            invalidate();
        });
        pressAnim.start();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                animateStretch(1f);
                return true;
            case MotionEvent.ACTION_UP:
                animateStretch(0f);
                if (e.getX() >= -20 && e.getX() <= getWidth() + 20) {
                    setChecked(!on, true);
                    Theme.tick(this);
                    if (listener != null) listener.onChanged(on);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                animateStretch(0f);
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight(), r = h / 2f;
        float p = Math.max(0f, Math.min(1f, progress));
        track.setColor(blend(blend(Theme.SURFACE_3, 0xFFFFFFFF, 0.1f), Theme.ACCENT, p));
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, r, r, track);
        if (p < 0.5f) {
            rect.inset(hair.getStrokeWidth() / 2, hair.getStrokeWidth() / 2);
            canvas.drawRoundRect(rect, r, r, hair);
        }
        float pad = Theme.dp(getContext(), 3);
        float kr = r - pad;
        float kw = kr * 2 + stretch * Theme.dp(getContext(), 7);
        float travel = w - pad * 2 - kw;
        float left = pad + travel * progress;
        knob.setColor(blend(0xFFE6E6E6, Theme.BLACK, p));
        rect.set(left, pad, left + kw, h - pad);
        canvas.drawRoundRect(rect, kr, kr, knob);
    }

    static int blend(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24), ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24), br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }
}
