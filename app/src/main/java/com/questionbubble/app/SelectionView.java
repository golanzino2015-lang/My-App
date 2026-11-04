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
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillStroke = new Paint(Paint.ANTI_ALIAS_FLAG);

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

        dimPaint.setColor(0xB8000000);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(1.5f * density);
        boxPaint.setColor(0xCCFFFFFF);
        detectPaint.setStyle(Paint.Style.STROKE);
        detectPaint.setStrokeWidth(2f * density);
        detectPaint.setColor(Color.WHITE);
        handlePaint.setStyle(Paint.Style.STROKE);
        handlePaint.setStrokeWidth(3.5f * density);
        handlePaint.setStrokeCap(Paint.Cap.ROUND);
        handlePaint.setColor(Color.WHITE);
        titlePaint.setColor(Ui.DIM);
        titlePaint.setTextSize(10.5f * density);
        titlePaint.setLetterSpacing(0.14f);
        titlePaint.setTypeface(Ui.font(700));
        titlePaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Ui.TEXT);
        textPaint.setTextSize(13.5f * density);
        textPaint.setTypeface(Ui.font(500));
        textPaint.setTextAlign(Paint.Align.CENTER);
        pillPaint.setColor(0xF0141414);
        pillStroke.setStyle(Paint.Style.STROKE);
        pillStroke.setStrokeWidth(1f * density);
        pillStroke.setColor(Ui.SEP);
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
            float rr = 6 * density;
            canvas.drawRoundRect(new RectF(sel), rr, rr, detected != null ? detectPaint : boxPaint);
            drawHandles(canvas, sel);
        }
        canvas.restore();

        // Hint card at the top.
        float cx = getWidth() / 2f;
        float top = 52 * density;
        String head = detected != null ? "QUESTION DETECTED" : "SELECT QUESTION";
        String line = detected != null ? "Sending to ChatGPT" : "Drag to crop  \u00B7  Hold to auto-detect";
        float w = Math.max(textPaint.measureText(line), titlePaint.measureText(head)) + 40 * density;
        RectF pill = new RectF(cx - w / 2, top, cx + w / 2, top + 58 * density);
        canvas.drawRoundRect(pill, 16 * density, 16 * density, pillPaint);
        canvas.drawRoundRect(pill, 16 * density, 16 * density, pillStroke);
        canvas.drawText(head, cx, top + 22 * density, titlePaint);
        canvas.drawText(line, cx, top + 43 * density, textPaint);

        // Cancel button (bottom centre).
        if (detected == null) {
            RectF c = cancelRect();
            canvas.drawRoundRect(c, c.height() / 2, c.height() / 2, pillPaint);
            canvas.drawRoundRect(c, c.height() / 2, c.height() / 2, pillStroke);
            canvas.drawText("Cancel", c.centerX(), c.centerY() + 5 * density, textPaint);
        }
    }

    /** L-shaped white corner handles. */
    private void drawHandles(Canvas canvas, Rect r) {
        float len = Math.min(18 * density, Math.min(r.width(), r.height()) / 3f);
        float l = r.left, t = r.top, ri = r.right, b = r.bottom;
        canvas.drawLine(l, t, l + len, t, handlePaint);
        canvas.drawLine(l, t, l, t + len, handlePaint);
        canvas.drawLine(ri, t, ri - len, t, handlePaint);
        canvas.drawLine(ri, t, ri, t + len, handlePaint);
        canvas.drawLine(l, b, l + len, b, handlePaint);
        canvas.drawLine(l, b, l, b - len, handlePaint);
        canvas.drawLine(ri, b, ri - len, b, handlePaint);
        canvas.drawLine(ri, b, ri, b - len, handlePaint);
    }

    private RectF cancelRect() {
        float cx = getWidth() / 2f;
        float bottom = getHeight() - 72 * density;
        return new RectF(cx - 64 * density, bottom - 46 * density, cx + 64 * density, bottom);
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
