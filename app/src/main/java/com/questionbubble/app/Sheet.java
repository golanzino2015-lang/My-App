package com.questionbubble.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * Bottom sheet: slides up over a fading scrim, has a grab handle and can be dragged down to
 * close. Put content in {@link #body()}.
 */
final class Sheet {

    private final Activity a;
    private final Dialog dialog;
    private final View scrim;
    private final LinearLayout sheet;
    private final LinearLayout body;
    private boolean closing;
    private Runnable onClosed;

    Sheet(Activity activity) {
        a = activity;
        dialog = new Dialog(a, R.style.SheetDialog) {
            @Override
            @SuppressWarnings("deprecation")
            public void onBackPressed() {
                close();
            }
        };
        FrameLayout root = new FrameLayout(a);
        scrim = new View(a);
        scrim.setBackgroundColor(Theme.SCRIM);
        scrim.setAlpha(0f);
        scrim.setOnClickListener(v -> close());
        root.addView(scrim, new FrameLayout.LayoutParams(Theme.MATCH, Theme.MATCH));

        sheet = new LinearLayout(a);
        sheet.setOrientation(LinearLayout.VERTICAL);
        float r = Theme.dp(a, 30);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.SURFACE_2);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        bg.setStroke(Math.max(1, Theme.dp(a, 0.8f)), Theme.HAIRLINE);
        sheet.setBackground(bg);
        sheet.setClickable(true);

        View handleZone = handle();
        sheet.addView(handleZone, Theme.lp(Theme.MATCH, Theme.dp(a, 30)));

        body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Theme.dp(a, 24), Theme.dp(a, 4), Theme.dp(a, 24), Theme.dp(a, 24));
        sheet.addView(body, Theme.lp(Theme.MATCH, Theme.WRAP));
        Theme.insets(sheet, false, true, true);

        root.addView(sheet, new FrameLayout.LayoutParams(Theme.MATCH, Theme.WRAP, Gravity.BOTTOM));
        dialog.setContentView(root);

        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setLayout(Theme.MATCH, Theme.MATCH);
            w.setDecorFitsSystemWindows(false);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private View handle() {
        FrameLayout zone = new FrameLayout(a);
        View bar = new View(a);
        bar.setBackground(Theme.rounded(0x40FFFFFF, Theme.dp(a, 3)));
        zone.addView(bar, new FrameLayout.LayoutParams(Theme.dp(a, 38), Theme.dp(a, 5), Gravity.CENTER));
        zone.setOnTouchListener(new View.OnTouchListener() {
            float downY;
            VelocityTracker vt;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downY = e.getRawY();
                        vt = VelocityTracker.obtain();
                        vt.addMovement(e);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (vt != null) vt.addMovement(e);
                        float dy = Math.max(0, e.getRawY() - downY);
                        sheet.setTranslationY(dy);
                        scrim.setAlpha(1f - Math.min(1f, dy / Math.max(1, sheet.getHeight())));
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        float vy = 0;
                        if (vt != null) {
                            vt.addMovement(e);
                            vt.computeCurrentVelocity(1000);
                            vy = vt.getYVelocity();
                            vt.recycle();
                            vt = null;
                        }
                        if (sheet.getTranslationY() > sheet.getHeight() * 0.28f || vy > Theme.dp(a, 900)) {
                            close();
                        } else {
                            sheet.animate().translationY(0).setDuration(380)
                                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f)).start();
                            scrim.animate().alpha(1f).setDuration(200).start();
                        }
                        return true;
                }
                return false;
            }
        });
        return zone;
    }

    LinearLayout body() {
        return body;
    }

    Activity activity() {
        return a;
    }

    void setOnClosed(Runnable r) {
        onClosed = r;
    }

    void show() {
        dialog.show();
        sheet.setVisibility(View.INVISIBLE);
        sheet.post(() -> {
            sheet.setTranslationY(sheet.getHeight());
            sheet.setVisibility(View.VISIBLE);
            sheet.animate().translationY(0).setDuration(520).setInterpolator(Theme.DECELERATE).start();
            scrim.animate().alpha(1f).setDuration(320).start();
        });
    }

    void close() {
        if (closing) return;
        closing = true;
        sheet.animate().translationY(sheet.getHeight()).setDuration(260)
                .setInterpolator(Theme.ACCELERATE).withEndAction(() -> {
                    try {
                        dialog.dismiss();
                    } catch (Exception ignored) {
                    }
                    if (onClosed != null) onClosed.run();
                }).start();
        scrim.animate().alpha(0f).setDuration(260).start();
    }

    // ---------------------------------------------------------------- content helpers

    void title(String title, String subtitle) {
        body.addView(Theme.text(a, title, Theme.Type.TITLE), Theme.lp(Theme.MATCH, Theme.WRAP, Theme.dp(a, 6)));
        if (subtitle != null) {
            body.addView(Theme.text(a, subtitle, Theme.Type.BODY), Theme.lp(Theme.MATCH, Theme.WRAP, Theme.dp(a, 8)));
        }
    }

    void add(View v, int topDp) {
        body.addView(v, Theme.lp(Theme.MATCH, Theme.WRAP, Theme.dp(a, topDp)));
    }
}
