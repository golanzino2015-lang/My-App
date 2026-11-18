package com.myapp.files;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Short message in a floating black pill above the bottom bar. */
final class Snack {
    private static View current;

    static void show(FrameLayout host, String msg) {
        show(host, msg, null, null);
    }

    static void show(FrameLayout host, String msg, String action, Runnable onAction) {
        Context c = host.getContext();
        if (current != null) host.removeView(current);
        LinearLayout pill = Ui.row(c);
        pill.setBackground(Ui.ink(c, 22));
        pill.setPadding(Ui.dp(c, 20), Ui.dp(c, 14), Ui.dp(c, action == null ? 20 : 8), Ui.dp(c, 14));
        TextView t = Ui.text(c, msg, Ui.Type.CALLOUT);
        t.setTextColor(Ui.WHITE);
        t.setMaxLines(2);
        pill.addView(t, Ui.weight(Ui.WRAP));
        if (action != null) {
            TextView a = Ui.text(c, action, Ui.Type.LABEL);
            a.setTextColor(Ui.WHITE);
            a.setPadding(Ui.dp(c, 14), Ui.dp(c, 6), Ui.dp(c, 14), Ui.dp(c, 6));
            a.setBackground(Ui.rippleDark(Ui.rounded(0x26FFFFFF, Ui.dp(c, 16)), Ui.dp(c, 16)));
            pill.addView(a);
            a.setOnClickListener(v -> {
                Ui.tap(v);
                hide(host, pill);
                if (onAction != null) onAction.run();
            });
        }
        FrameLayout.LayoutParams lp = Ui.flp(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM);
        int m = Ui.dp(c, 20);
        lp.setMargins(m, 0, m, Ui.dp(c, 104) + bottomInset(host));
        Ui.elevate(pill, 12, 22);
        host.addView(pill, lp);
        current = pill;
        pill.setAlpha(0f);
        pill.setTranslationY(Ui.dp(c, 24));
        pill.setScaleX(0.96f);
        pill.animate().alpha(1f).translationY(0f).scaleX(1f).setDuration(420).setInterpolator(Ui.DECELERATE).start();
        pill.postDelayed(() -> hide(host, pill), action == null ? 2600 : 4500);
    }

    private static int bottomInset(View v) {
        android.view.WindowInsets in = v.getRootWindowInsets();
        if (in == null) return 0;
        return in.getInsets(android.view.WindowInsets.Type.systemBars()).bottom;
    }

    private static void hide(FrameLayout host, View pill) {
        if (pill.getParent() == null) return;
        pill.animate().alpha(0f).translationY(Ui.dp(host.getContext(), 16)).setDuration(240)
                .setInterpolator(Ui.ACCELERATE).withEndAction(() -> {
                    host.removeView(pill);
                    if (current == pill) current = null;
                }).start();
    }

    private Snack() {}
}
