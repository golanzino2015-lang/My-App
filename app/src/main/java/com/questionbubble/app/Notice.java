package com.questionbubble.app;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Glass notification that slides in at the top of the screen (replaces plain toasts). */
final class Notice {

    enum Kind { LOADING, SUCCESS, ERROR, INFO }

    private final Context c;
    private final WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable hideRunnable = this::hide;

    private LinearLayout root;
    private FrameLayout iconBox;
    private TextView title, body;
    private boolean shown;

    Notice(Context c, WindowManager wm) {
        this.c = c;
        this.wm = wm;
    }

    void show(Kind kind, String t, String b, long autoHideMs) {
        main.removeCallbacks(hideRunnable);
        if (root == null) build();

        iconBox.removeAllViews();
        int s = Ui.dp(c, kind == Kind.LOADING ? 18 : 16);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(s, s, Gravity.CENTER);
        if (kind == Kind.LOADING) {
            iconBox.addView(new SpinnerView(c), lp);
        } else if (kind != Kind.INFO) {
            ImageView iv = new ImageView(c);
            iv.setImageResource(kind == Kind.SUCCESS ? R.drawable.ic_check : R.drawable.ic_alert);
            iconBox.addView(iv, lp);
        }
        iconBox.setVisibility(kind == Kind.INFO ? View.GONE : View.VISIBLE);

        title.setText(t);
        body.setText(b == null ? "" : b);
        body.setVisibility(b == null || b.isEmpty() ? View.GONE : View.VISIBLE);

        if (!shown) {
            shown = true;
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    Math.min(Ui.dp(c, 380), c.getResources().getDisplayMetrics().widthPixels - Ui.dp(c, 32)),
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            p.y = Ui.dp(c, 44);
            try {
                wm.addView(root, p);
            } catch (Exception e) {
                shown = false;
                return;
            }
            root.setAlpha(0f);
            root.setTranslationY(-Ui.dp(c, 18));
            root.setScaleX(0.96f);
            root.setScaleY(0.96f);
            root.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                    .setDuration(360).setInterpolator(Ui.EMPHASIZED).start();
        } else {
            // content swap: quick settle
            root.setScaleX(0.98f);
            root.setScaleY(0.98f);
            root.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(220)
                    .setInterpolator(Ui.EMPHASIZED).start();
        }
        if (autoHideMs > 0) main.postDelayed(hideRunnable, autoHideMs);
    }

    void hide() {
        main.removeCallbacks(hideRunnable);
        if (!shown || root == null) return;
        shown = false;
        View r = root;
        root = null;
        r.animate().alpha(0f).translationY(-Ui.dp(c, 12)).setDuration(200)
                .setInterpolator(Ui.EXIT)
                .withEndAction(() -> {
                    try {
                        wm.removeView(r);
                    } catch (Exception ignored) {
                    }
                }).start();
    }

    private void build() {
        root = new LinearLayout(c);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(c, 14);
        root.setPadding(pad, Ui.dp(c, 12), pad, Ui.dp(c, 12));
        ShineDrawable bg = Ui.panel(c, 16);
        root.setBackground(bg);
        ShineDrawable.animateWhileAttached(root, bg, 3200);

        iconBox = new FrameLayout(c);
        iconBox.setBackground(Ui.rounded(Ui.ELEM, Ui.dp(c, 10), Ui.SEP, Ui.dp(c, 1)));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(c, 34), Ui.dp(c, 34));
        ip.rightMargin = Ui.dp(c, 12);
        root.addView(iconBox, ip);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        title = Ui.text(c, "", 14, 600, Ui.TEXT);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        body = Ui.text(c, "", 12.5f, 400, Ui.DIM);
        body.setMaxLines(3);
        body.setEllipsize(TextUtils.TruncateAt.END);
        body.setLineSpacing(Ui.dp(c, 2), 1f);
        col.addView(title);
        col.addView(body, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(c, 4)));
        root.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    }
}
