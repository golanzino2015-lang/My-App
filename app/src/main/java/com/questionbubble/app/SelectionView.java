package com.questionbubble.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Full-screen overlay that shows a frozen screenshot. The user either drags a box around the
 * question (crop) or long-presses on it (auto-detect). All coordinates passed to the listener are
 * absolute screen pixels, matching the screenshot bitmap.
 */
@SuppressLint("ViewConstructor")
final class SelectionView extends View {

    interface Listener {
        void onCropped(Rect screenRect);
        void onLongPress(int screenX, int screenY);
        void onCancel();
    }

    private final Bitmap shot;
    private final Listener listener;
    private final float density;
    private final int touchSlop;

    private final Paint dimPaint = new Paint();
    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint detectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final int[] loc = new int[2];
    private float startX, startY, curX, curY;
    private boolean dragging, longPressed, done, downOnCancel;
    private Rect detected;

    private final Runnable longPressRunnable = this::fireLongPress;

    private void fireLongPress() {
        if (dragging || done) return;
        longPressed = true;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        listener.onLongPress(Math.round(startX), Math.round(startY));
    }

    SelectionView(Context ctx, Bitmap shot, Listener listener) {
        super(ctx);
        this.shot = shot;
        this.listener = listener;
        this.density = ctx.getResources().getDisplayMetrics().density;
        this.touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();

        dimPaint.setColor(0x99000000);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(3 * density);
        boxPaint.setColor(0xFF10A37F);
        detectPaint.setStyle(Paint.Style.STROKE);
        detectPaint.setStrokeWidth(4 * density);
        detectPaint.setColor(0xFFFFC107);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(14 * density);
        textPaint.setTextAlign(Paint.Align.CENTER);
        pillPaint.setColor(0xDD202124);
    }

    /** Briefly highlight the area that was auto-detected. */
    void showDetected(Rect r) {
        detected = r;
        done = true;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        getLocationOnScreen(loc);

        canvas.save();
        canvas.translate(-loc[0], -loc[1]); // draw in screen coordinates
        canvas.drawBitmap(shot, 0, 0, null);
        canvas.drawRect(0, 0, shot.getWidth(), shot.getHeight(), dimPaint);

        Rect sel = detected != null ? detected : (dragging ? currentRect() : null);
        if (sel != null) {
            canvas.drawBitmap(shot, sel, sel, null); // un-dimmed inside the box
            canvas.drawRect(sel, detected != null ? detectPaint : boxPaint);
        }
        canvas.restore();

        // Hint pill at the top.
        float cx = getWidth() / 2f;
        float top = 48 * density;
        String l1 = "Drag a box around the question";
        String l2 = "or long-press the question to auto-detect";
        float w = Math.max(textPaint.measureText(l1), textPaint.measureText(l2)) + 32 * density;
        RectF pill = new RectF(cx - w / 2, top, cx + w / 2, top + 56 * density);
        canvas.drawRoundRect(pill, 16 * density, 16 * density, pillPaint);
        canvas.drawText(l1, cx, top + 23 * density, textPaint);
        canvas.drawText(l2, cx, top + 44 * density, textPaint);

        // Cancel button (bottom centre).
        RectF c = cancelRect();
        canvas.drawRoundRect(c, 24 * density, 24 * density, pillPaint);
        canvas.drawText("✕  Cancel", c.centerX(), c.centerY() + 5 * density, textPaint);
    }

    private RectF cancelRect() {
        float cx = getWidth() / 2f;
        float bottom = getHeight() - 64 * density;
        return new RectF(cx - 70 * density, bottom - 48 * density, cx + 70 * density, bottom);
    }

    private Rect currentRect() {
        int l = Math.round(Math.min(startX, curX));
        int t = Math.round(Math.min(startY, curY));
        int r = Math.round(Math.max(startX, curX));
        int b = Math.round(Math.max(startY, curY));
        l = Math.max(0, l);
        t = Math.max(0, t);
        r = Math.min(shot.getWidth(), r);
        b = Math.min(shot.getHeight(), b);
        return new Rect(l, t, Math.max(l, r), Math.max(t, b));
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (done) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = curX = e.getRawX();
                startY = curY = e.getRawY();
                dragging = false;
                longPressed = false;
                downOnCancel = cancelRect().contains(e.getX(), e.getY());
                if (!downOnCancel) {
                    postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (longPressed || downOnCancel) return true;
                curX = e.getRawX();
                curY = e.getRawY();
                if (!dragging && Math.hypot(curX - startX, curY - startY) > touchSlop) {
                    dragging = true;
                    removeCallbacks(longPressRunnable);
                }
                if (dragging) invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                removeCallbacks(longPressRunnable);
                if (longPressed) return true;
                if (downOnCancel) {
                    if (cancelRect().contains(e.getX(), e.getY())) {
                        done = true;
                        listener.onCancel();
                    }
                    return true;
                }
                if (dragging) {
                    Rect r = currentRect();
                    dragging = false;
                    if (r.width() > 24 * density && r.height() > 16 * density) {
                        done = true;
                        listener.onCropped(r);
                    }
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPressRunnable);
                dragging = false;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }
}
