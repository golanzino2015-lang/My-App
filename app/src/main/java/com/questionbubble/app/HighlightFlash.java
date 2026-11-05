package com.questionbubble.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/** Briefly outlines the question that was found, on top of everything, without blocking touches. */
final class HighlightFlash extends View {

    private final RectF box;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] loc = new int[2];

    private HighlightFlash(Context c, Rect screenBox) {
        super(c);
        box = new RectF(screenBox);
        float d = c.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(3 * d);
        stroke.setColor(Theme.WHITE);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(10 * d);
        glow.setColor(0x40FFFFFF);
    }

    static void show(Context c, WindowManager wm, Rect screenBox) {
        HighlightFlash v = new HighlightFlash(c, screenBox);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        p.setFitInsetsTypes(0);
        try {
            wm.addView(v, p);
        } catch (Exception e) {
            return;
        }
        v.setAlpha(0f);
        v.setScaleX(1.04f);
        v.setScaleY(1.04f);
        v.setPivotX(box(v).centerX());
        v.setPivotY(box(v).centerY());
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(Theme.DECELERATE)
                .withEndAction(() -> v.animate().alpha(0f).setStartDelay(650).setDuration(380)
                        .withEndAction(() -> {
                            try {
                                wm.removeView(v);
                            } catch (Exception ignored) {
                            }
                        }).start()).start();
    }

    private static RectF box(HighlightFlash v) {
        return v.box;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        getLocationOnScreen(loc);
        canvas.save();
        canvas.translate(-loc[0], -loc[1]);
        float r = 16 * getResources().getDisplayMetrics().density;
        canvas.drawRoundRect(box, r, r, glow);
        canvas.drawRoundRect(box, r, r, stroke);
        canvas.restore();
    }
}
