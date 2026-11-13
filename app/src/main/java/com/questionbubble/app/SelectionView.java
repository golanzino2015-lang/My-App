package com.questionbubble.app;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;

/**
 * Full-screen scan overlay over a frozen screenshot. Drag to crop, or press and hold until the
 * ring fills to auto-detect. All rects passed out are screen pixels matching the screenshot.
 */
@SuppressLint("ViewConstructor")
final class SelectionView extends View {

    interface Listener {
        void onCropped(Rect r);
        void onLongPress(int x, int y);
        void onFullScreen();
        void onCancel();
    }

    private static final long HOLD_MS = 520;

    private final Bitmap shot;
    private final Listener listener;
    private final float d; // density
    private final int slop;

    private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titleP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint btnLight = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint btnDark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Drawable handIcon, maxIcon;

    private final int[] loc = new int[2];
    private float appear = 0f;   // 0..1 entrance
    private float chrome = 1f;   // hint + buttons visibility
    private float startX, startY, curX, curY;
    private boolean dragging, holding, longPressed, done;
    private float hold;          // 0..1 ring progress
    private int pressedBtn;      // 0 none, 1 cancel, 2 full
    private RectF detected;      // animated detect rect (screen coords)
    private ValueAnimator holdAnim, chromeAnim, pulseAnim;
    private boolean searching;   // waiting for the AI to find the question
    private float pulse;
    private String statusTitle, statusSub, foundSub;

    SelectionView(Context c, Bitmap shot, Listener l) {
        super(c);
        this.shot = shot;
        this.listener = l;
        d = c.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();

        dim.setColor(0xB3000000);
        dim.setStyle(Paint.Style.FILL);
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(1.5f * d);
        frame.setColor(0xD9FFFFFF);
        handle.setStyle(Paint.Style.STROKE);
        handle.setStrokeWidth(4f * d);
        handle.setStrokeCap(Paint.Cap.ROUND);
        handle.setStrokeJoin(Paint.Join.ROUND);
        handle.setColor(Theme.ACCENT);
        ringTrack.setStyle(Paint.Style.STROKE);
        ringTrack.setStrokeWidth(3f * d);
        ringTrack.setColor(0x4DFFFFFF);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(3f * d);
        ring.setStrokeCap(Paint.Cap.ROUND);
        ring.setColor(Theme.ACCENT);
        dot.setColor(Theme.ACCENT);
        glass.setColor((Theme.PANEL & 0x00FFFFFF) | 0xF0000000);
        glassStroke.setStyle(Paint.Style.STROKE);
        glassStroke.setStrokeWidth(Math.max(1f, 0.8f * d));
        glassStroke.setColor(Theme.HAIRLINE_STRONG);
        white.setColor(Theme.ACCENT);
        float sp = c.getResources().getDisplayMetrics().scaledDensity;
        titleP.setTypeface(Fonts.weight(c, 600));
        titleP.setTextSize(15 * sp);
        titleP.setColor(Theme.TEXT);
        titleP.setLetterSpacing(-0.01f);
        subP.setTypeface(Fonts.weight(c, 400));
        subP.setTextSize(13 * sp);
        subP.setColor(Theme.TEXT_2);
        btnLight.setTypeface(Fonts.weight(c, 600));
        btnLight.setTextSize(15 * sp);
        btnLight.setColor(Theme.BLACK);
        btnLight.setTextAlign(Paint.Align.CENTER);
        btnDark.setTypeface(Fonts.weight(c, 600));
        btnDark.setTextSize(15 * sp);
        btnDark.setColor(Theme.TEXT);
        btnDark.setTextAlign(Paint.Align.CENTER);
        chipP.setTypeface(Fonts.weight(c, 600));
        chipP.setTextSize(12.5f * sp);
        chipP.setColor(Theme.BLACK);
        chipP.setTextAlign(Paint.Align.CENTER);
        Drawable hi = c.getDrawable(R.drawable.ic_hand);
        handIcon = hi == null ? null : hi.mutate();
        if (handIcon != null) handIcon.setTint(Theme.TEXT);
        Drawable mi = c.getDrawable(R.drawable.ic_maximize);
        maxIcon = mi == null ? null : mi.mutate();
        if (maxIcon != null) maxIcon.setTint(Theme.BLACK);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(420);
        a.setInterpolator(Theme.DECELERATE);
        a.addUpdateListener(v -> {
            appear = (float) v.getAnimatedValue();
            invalidate();
        });
        a.start();
    }

    // ---------------------------------------------------------------- geometry

    private int topInset() {
        WindowInsets wi = getRootWindowInsets();
        return wi == null ? (int) (32 * d) : wi.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()).top;
    }

    private int bottomInset() {
        WindowInsets wi = getRootWindowInsets();
        return wi == null ? (int) (24 * d) : wi.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()).bottom;
    }

    private RectF cancelBtn() {
        if (searching) {
            float h = 52 * d, w = 140 * d;
            float bottom = getHeight() - bottomInset() - 28 * d;
            return new RectF(getWidth() / 2f - w / 2, bottom - h, getWidth() / 2f + w / 2, bottom);
        }
        float h = 52 * d, w = 120 * d, gap = 10 * d;
        float total = w + gap + 168 * d;
        float left = getWidth() / 2f - total / 2f;
        float bottom = getHeight() - bottomInset() - 28 * d + (1f - appear) * 80 * d;
        return new RectF(left, bottom - h, left + w, bottom);
    }

    private RectF fullBtn() {
        RectF c = cancelBtn();
        return new RectF(c.right + 10 * d, c.top, c.right + 10 * d + 168 * d, c.bottom);
    }

    private Rect dragRect() {
        int l = Math.round(Math.min(startX, curX)), t = Math.round(Math.min(startY, curY));
        int r = Math.round(Math.max(startX, curX)), b = Math.round(Math.max(startY, curY));
        return new Rect(Math.max(0, l), Math.max(0, t), Math.min(shot.getWidth(), r), Math.min(shot.getHeight(), b));
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void onDraw(Canvas canvas) {
        getLocationOnScreen(loc);
        canvas.save();
        canvas.translate(-loc[0], -loc[1]);

        canvas.drawBitmap(shot, 0, 0, null);

        RectF hole = detected != null ? detected : (dragging ? new RectF(dragRect()) : null);
        float rr = 14 * d;
        path.reset();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.addRect(0, 0, shot.getWidth(), shot.getHeight(), Path.Direction.CW);
        if (hole != null && hole.width() > 2 && hole.height() > 2) {
            float r = Math.min(rr, Math.min(hole.width(), hole.height()) / 2f);
            path.addRoundRect(hole, r, r, Path.Direction.CW);
        }
        dim.setAlpha(Math.round(0xB3 * appear));
        canvas.drawPath(path, dim);

        if (hole != null && hole.width() > 2 && hole.height() > 2) {
            float r = Math.min(rr, Math.min(hole.width(), hole.height()) / 2f);
            canvas.drawRoundRect(hole, r, r, frame);
            drawHandles(canvas, hole, r);
            if (dragging && detected == null && hole.width() > 60 * d && hole.height() > 20 * d) {
                drawChip(canvas, hole, "Release to scan");
            }
        }

        if (searching && detected == null) {
            float R = 30 * d;
            ring.setAlpha(255);
            canvas.drawCircle(startX, startY, R, ring);
            for (int k = 0; k < 2; k++) {
                float p = (pulse + k * 0.5f) % 1f;
                ringTrack.setAlpha(Math.round(200 * (1f - p)));
                canvas.drawCircle(startX, startY, R + p * 46 * d, ringTrack);
            }
            ringTrack.setAlpha(0x4D);
            dot.setAlpha(255);
            canvas.drawCircle(startX, startY, 5 * d, dot);
        }
        if (holding && hold > 0f) {
            float R = 30 * d;
            canvas.drawCircle(startX, startY, R, ringTrack);
            RectF o = new RectF(startX - R, startY - R, startX + R, startY + R);
            canvas.drawArc(o, -90, 360 * hold, false, ring);
            dot.setAlpha(Math.round(255 * Math.min(1f, hold * 2)));
            canvas.drawCircle(startX, startY, 5 * d * (0.6f + 0.4f * hold), dot);
        }
        canvas.restore();

        float ca = appear * chrome;
        if (ca > 0.01f) drawChrome(canvas, ca);
    }

    private void drawHandles(Canvas canvas, RectF h, float r) {
        float len = Math.min(26 * d, Math.min(h.width(), h.height()) / 2.6f);
        float o = 0f;
        Path p = new Path();
        // top-left
        p.moveTo(h.left - o, h.top + len);
        p.lineTo(h.left - o, h.top + r);
        p.quadTo(h.left - o, h.top - o, h.left + r, h.top - o);
        p.lineTo(h.left + len, h.top - o);
        // top-right
        p.moveTo(h.right - len, h.top - o);
        p.lineTo(h.right - r, h.top - o);
        p.quadTo(h.right + o, h.top - o, h.right + o, h.top + r);
        p.lineTo(h.right + o, h.top + len);
        // bottom-right
        p.moveTo(h.right + o, h.bottom - len);
        p.lineTo(h.right + o, h.bottom - r);
        p.quadTo(h.right + o, h.bottom + o, h.right - r, h.bottom + o);
        p.lineTo(h.right - len, h.bottom + o);
        // bottom-left
        p.moveTo(h.left + len, h.bottom + o);
        p.lineTo(h.left + r, h.bottom + o);
        p.quadTo(h.left - o, h.bottom + o, h.left - o, h.bottom - r);
        p.lineTo(h.left - o, h.bottom - len);
        canvas.drawPath(p, handle);
    }

    private void drawChip(Canvas canvas, RectF h, String text) {
        float w = chipP.measureText(text) + 24 * d, ht = 28 * d;
        float cx = h.centerX();
        float top = h.bottom + 12 * d;
        if (top + ht > shot.getHeight() - 140 * d) top = h.top - 12 * d - ht;
        RectF r = new RectF(cx - w / 2, top, cx + w / 2, top + ht);
        canvas.drawRoundRect(r, ht / 2, ht / 2, white);
        canvas.drawText(text, cx, r.centerY() - (chipP.descent() + chipP.ascent()) / 2, chipP);
    }

    private void drawChrome(Canvas canvas, float a) {
        int alpha = Math.round(255 * a);
        // instruction card
        String t = detected != null ? "Question found" : searching && statusTitle != null ? statusTitle : "Select a question";
        String s = detected != null ? (foundSub != null ? foundSub : "Answer copied") : searching && statusSub != null ? statusSub : "Drag to crop, or press and hold on it";
        float iconS = 20 * d, padL = 18 * d, gap = 14 * d;
        float textW = Math.max(titleP.measureText(t), subP.measureText(s));
        float w = Math.min(getWidth() - 32 * d, padL + iconS + gap + textW + 22 * d);
        float h = 66 * d;
        float top = topInset() + 14 * d - (1f - appear) * 40 * d;
        RectF card = new RectF(getWidth() / 2f - w / 2, top, getWidth() / 2f + w / 2, top + h);
        glass.setAlpha(Math.round(0xF0 * a));
        glassStroke.setAlpha(Math.round(0x2B * a));
        canvas.drawRoundRect(card, 22 * d, 22 * d, glass);
        canvas.drawRoundRect(card, 22 * d, 22 * d, glassStroke);
        if (handIcon != null) {
            int il = Math.round(card.left + padL), it = Math.round(card.centerY() - iconS / 2);
            handIcon.setBounds(il, it, il + Math.round(iconS), it + Math.round(iconS));
            handIcon.setAlpha(alpha);
            handIcon.draw(canvas);
        }
        float tx = card.left + padL + iconS + gap;
        titleP.setAlpha(alpha);
        subP.setAlpha(Math.round(0x9E * a));
        canvas.drawText(t, tx, card.top + 28 * d, titleP);
        canvas.drawText(s, tx, card.top + 49 * d, subP);

        if (detected != null) return;
        if (searching) {
            RectF cb = cancelBtn();
            float pc = pressedBtn == 1 ? 0.96f : 1f;
            canvas.save();
            canvas.scale(pc, pc, cb.centerX(), cb.centerY());
            canvas.drawRoundRect(cb, cb.height() / 2, cb.height() / 2, glass);
            canvas.drawRoundRect(cb, cb.height() / 2, cb.height() / 2, glassStroke);
            btnDark.setAlpha(alpha);
            canvas.drawText("Cancel", cb.centerX(), cb.centerY() - (btnDark.descent() + btnDark.ascent()) / 2, btnDark);
            canvas.restore();
            return;
        }

        // bottom buttons
        RectF cb = cancelBtn(), fb = fullBtn();
        float pc = pressedBtn == 1 ? 0.96f : 1f, pf = pressedBtn == 2 ? 0.96f : 1f;
        canvas.save();
        canvas.scale(pc, pc, cb.centerX(), cb.centerY());
        canvas.drawRoundRect(cb, cb.height() / 2, cb.height() / 2, glass);
        canvas.drawRoundRect(cb, cb.height() / 2, cb.height() / 2, glassStroke);
        btnDark.setAlpha(alpha);
        canvas.drawText("Cancel", cb.centerX(), cb.centerY() - (btnDark.descent() + btnDark.ascent()) / 2, btnDark);
        canvas.restore();

        canvas.save();
        canvas.scale(pf, pf, fb.centerX(), fb.centerY());
        white.setAlpha(alpha);
        canvas.drawRoundRect(fb, fb.height() / 2, fb.height() / 2, white);
        white.setAlpha(255);
        String ft = "Full screen";
        float tw = btnLight.measureText(ft);
        float is = 17 * d;
        float startXp = fb.centerX() - (is + 8 * d + tw) / 2;
        if (maxIcon != null) {
            int il = Math.round(startXp), it = Math.round(fb.centerY() - is / 2);
            maxIcon.setBounds(il, it, il + Math.round(is), it + Math.round(is));
            maxIcon.setAlpha(alpha);
            maxIcon.draw(canvas);
        }
        btnLight.setAlpha(alpha);
        btnLight.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(ft, startXp + is + 8 * d, fb.centerY() - (btnLight.descent() + btnLight.ascent()) / 2, btnLight);
        canvas.restore();
    }

    // ---------------------------------------------------------------- touch

    private void setChrome(float to) {
        if (chromeAnim != null) chromeAnim.cancel();
        chromeAnim = ValueAnimator.ofFloat(chrome, to);
        chromeAnim.setDuration(220);
        chromeAnim.addUpdateListener(v -> {
            chrome = (float) v.getAnimatedValue();
            invalidate();
        });
        chromeAnim.start();
    }

    private void startHold() {
        holding = true;
        hold = 0f;
        holdAnim = ValueAnimator.ofFloat(0f, 1f);
        holdAnim.setStartDelay(110);
        holdAnim.setDuration(HOLD_MS);
        holdAnim.setInterpolator(new android.view.animation.LinearInterpolator());
        holdAnim.addUpdateListener(v -> {
            hold = (float) v.getAnimatedValue();
            invalidate();
            if (hold >= 1f && holding && !longPressed && !dragging) {
                longPressed = true;
                holding = false;
                if (Prefs.haptics(getContext())) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                listener.onLongPress(Math.round(startX), Math.round(startY));
            }
        });
        holdAnim.start();
    }

    private void cancelHold() {
        holding = false;
        if (holdAnim != null) holdAnim.cancel();
        hold = 0f;
        invalidate();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (searching && detected == null) {
            float sx = e.getX(), sy = e.getY();
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN && cancelBtn().contains(sx, sy)) {
                pressedBtn = 1;
                invalidate();
            } else if (e.getActionMasked() == MotionEvent.ACTION_UP && pressedBtn == 1) {
                pressedBtn = 0;
                invalidate();
                if (cancelBtn().contains(sx, sy)) {
                    stopPulse();
                    listener.onCancel();
                }
            }
            return true;
        }
        if (done) return true;
        float vx = e.getX(), vy = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = curX = e.getRawX();
                startY = curY = e.getRawY();
                dragging = false;
                longPressed = false;
                pressedBtn = 0;
                if (chrome > 0.5f && cancelBtn().contains(vx, vy)) pressedBtn = 1;
                else if (chrome > 0.5f && fullBtn().contains(vx, vy)) pressedBtn = 2;
                if (pressedBtn != 0) {
                    if (Prefs.haptics(getContext())) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    invalidate();
                } else {
                    startHold();
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (pressedBtn != 0 || longPressed) return true;
                curX = e.getRawX();
                curY = e.getRawY();
                if (!dragging && Math.hypot(curX - startX, curY - startY) > slop) {
                    dragging = true;
                    cancelHold();
                    setChrome(0f);
                }
                if (dragging) invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                if (pressedBtn != 0) {
                    int b = pressedBtn;
                    pressedBtn = 0;
                    invalidate();
                    RectF r = b == 1 ? cancelBtn() : fullBtn();
                    if (r.contains(vx, vy)) {
                        done = true;
                        if (b == 1) listener.onCancel();
                        else listener.onFullScreen();
                    }
                    return true;
                }
                if (longPressed) return true;
                cancelHold();
                if (dragging) {
                    Rect r = dragRect();
                    dragging = false;
                    if (r.width() > 28 * d && r.height() > 18 * d) {
                        done = true;
                        detected = new RectF(r);
                        if (Prefs.haptics(getContext())) performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                        invalidate();
                        listener.onCropped(r);
                    } else {
                        setChrome(1f);
                        invalidate();
                    }
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                cancelHold();
                dragging = false;
                pressedBtn = 0;
                setChrome(1f);
                return true;
        }
        return super.onTouchEvent(e);
    }

    /** Where the press was, in screen pixels (used when the press happened in another app). */
    void setPress(int x, int y) {
        startX = curX = x;
        startY = curY = y;
    }

    /** After a press-and-hold: pulse at the finger while the AI looks for the question. */
    void startSearching(String title, String sub, String whenFound) {
        foundSub = whenFound;
        done = true;
        holding = false;
        searching = true;
        statusTitle = title;
        statusSub = sub;
        setChrome(1f);
        pulseAnim = ValueAnimator.ofFloat(0f, 1f);
        pulseAnim.setDuration(1400);
        pulseAnim.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnim.setInterpolator(new android.view.animation.LinearInterpolator());
        pulseAnim.addUpdateListener(v -> {
            pulse = (float) v.getAnimatedValue();
            invalidate();
        });
        pulseAnim.start();
    }

    private void stopPulse() {
        if (pulseAnim != null) pulseAnim.cancel();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopPulse();
        super.onDetachedFromWindow();
    }

    /** Grow the found area out from the finger, then call {@code onShown}. */
    void showDetected(Rect target, Runnable onShown) {
        stopPulse();
        done = true;
        setChrome(1f);
        float s = 40 * d;
        RectF from = new RectF(startX - s, startY - s, startX + s, startY + s);
        RectF to = new RectF(target);
        detected = new RectF(from);
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(460);
        a.setInterpolator(Theme.DECELERATE);
        a.addUpdateListener(v -> {
            float f = (float) v.getAnimatedValue();
            detected.set(from.left + (to.left - from.left) * f, from.top + (to.top - from.top) * f,
                    from.right + (to.right - from.right) * f, from.bottom + (to.bottom - from.bottom) * f);
            invalidate();
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                postDelayed(onShown, 260);
            }
        });
        a.start();
    }
}
