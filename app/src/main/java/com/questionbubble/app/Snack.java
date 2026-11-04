package com.questionbubble.app;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small pill message that rises from the bottom of the current screen. */
final class Snack {

    private static View current;

    static void show(Activity a, String text, int iconRes) {
        ViewGroup content = a.findViewById(android.R.id.content);
        if (content == null) return;
        if (current != null && current.getParent() != null) {
            ((ViewGroup) current.getParent()).removeView(current);
        }

        LinearLayout pill = new LinearLayout(a);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(Theme.dp(a, 14), Theme.dp(a, 12), Theme.dp(a, 20), Theme.dp(a, 12));
        pill.setBackground(Theme.rounded(Theme.SURFACE_3, Theme.dp(a, 24), Theme.HAIRLINE_STRONG, Math.max(1, Theme.dp(a, 0.8f))));
        if (iconRes != 0) {
            FrameLayout disc = new FrameLayout(a);
            disc.setBackground(Theme.rounded(Theme.WHITE, Theme.dp(a, 11)));
            disc.addView(Theme.icon(a, iconRes, Theme.BLACK, 13));
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Theme.dp(a, 22), Theme.dp(a, 22));
            dp.rightMargin = Theme.dp(a, 10);
            pill.addView(disc, dp);
        }
        TextView t = Theme.text(a, text, Theme.Type.CALLOUT);
        pill.addView(t);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Theme.WRAP, Theme.WRAP,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        int bottomInset = 0;
        android.view.WindowInsets wi = content.getRootWindowInsets();
        if (wi != null) bottomInset = wi.getInsets(android.view.WindowInsets.Type.systemBars()).bottom;
        lp.bottomMargin = bottomInset + Theme.dp(a, 24);
        content.addView(pill, lp);
        pill.setElevation(Theme.dp(a, 12));
        current = pill;

        pill.setAlpha(0f);
        pill.setTranslationY(Theme.dp(a, 40));
        pill.setScaleX(0.9f);
        pill.setScaleY(0.9f);
        pill.animate().alpha(1f).translationY(0).scaleX(1f).scaleY(1f).setDuration(460)
                .setInterpolator(Theme.DECELERATE).start();
        pill.postDelayed(() -> pill.animate().alpha(0f).translationY(Theme.dp(a, 20)).setDuration(240)
                .setInterpolator(Theme.ACCELERATE).withEndAction(() -> {
                    if (pill.getParent() != null) ((ViewGroup) pill.getParent()).removeView(pill);
                    if (current == pill) current = null;
                }).start(), 2000);
    }

    private Snack() {}
}
