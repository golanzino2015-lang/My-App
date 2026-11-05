package com.questionbubble.app;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/** Side-by-side pages you can swipe between (children are the pages, left to right). */
final class PagerFrame extends FrameLayout {

    interface Listener {
        void onPage(int index);
    }

    private final int slop;
    private float pos;
    private int page;
    private Listener listener;
    private float downX, downY, startPos;
    private boolean dragging;
    private VelocityTracker vt;
    private ValueAnimator anim;
    private boolean fill;

    PagerFrame(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setClipChildren(true);
        setClipToPadding(true);
    }

    /** Pages take the pager's full height (for full-screen pages). */
    void setFill(boolean f) {
        fill = f;
    }

    void setListener(Listener l) {
        listener = l;
    }

    int page() {
        return page;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        if (fill) {
            int h = MeasureSpec.getSize(heightSpec);
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            }
            setMeasuredDimension(w, h);
            return;
        }
        int h = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            ch.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            h = Math.max(h, ch.getMeasuredHeight());
        }
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            ch.layout(i * w, 0, (i + 1) * w, ch.getMeasuredHeight());
        }
        apply();
    }

    private void apply() {
        setScrollX(Math.round(pos * getWidth()));
        int w = Math.max(1, getWidth());
        for (int i = 0; i < getChildCount(); i++) {
            float d = Math.min(1f, Math.abs(pos - i));
            getChildAt(i).setAlpha(fill ? 1f : 1f - 0.6f * d);
        }
    }

    void goTo(int i, boolean animate) {
        i = Math.max(0, Math.min(getChildCount() - 1, i));
        boolean changed = i != page;
        page = i;
        if (anim != null) anim.cancel();
        if (!animate) {
            pos = i;
            apply();
        } else {
            anim = ValueAnimator.ofFloat(pos, i);
            anim.setDuration(420);
            anim.setInterpolator(new android.view.animation.OvershootInterpolator(0.7f));
            anim.addUpdateListener(a -> {
                pos = (float) a.getAnimatedValue();
                apply();
            });
            anim.start();
        }
        if (changed && listener != null) listener.onPage(i);
    }

    private void track(MotionEvent e) {
        if (vt == null) vt = VelocityTracker.obtain();
        vt.addMovement(e);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                downY = e.getRawY();
                startPos = pos;
                dragging = false;
                if (vt != null) vt.clear();
                track(e);
                return false;
            case MotionEvent.ACTION_MOVE:
                track(e);
                float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                if (!dragging && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                    dragging = true;
                    if (anim != null) anim.cancel();
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                return false;
        }
        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        track(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                downY = e.getRawY();
                startPos = pos;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getRawX() - downX;
                if (!dragging && Math.abs(dx) > slop) dragging = true;
                if (dragging) {
                    float p = startPos - dx / Math.max(1, getWidth());
                    float max = getChildCount() - 1;
                    if (p < 0) p = p * 0.3f;
                    if (p > max) p = max + (p - max) * 0.3f;
                    pos = p;
                    apply();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                float vx = 0;
                if (vt != null) {
                    vt.computeCurrentVelocity(1000);
                    vx = vt.getXVelocity();
                    vt.recycle();
                    vt = null;
                }
                int target = Math.round(pos);
                float fling = getResources().getDisplayMetrics().density * 400;
                if (vx < -fling) target = (int) Math.floor(pos) + 1;
                else if (vx > fling) target = (int) Math.ceil(pos) - 1;
                dragging = false;
                if (target != page) Theme.tick(this);
                goTo(target, true);
                return true;
            }
        }
        return super.onTouchEvent(e);
    }
}
