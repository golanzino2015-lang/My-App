package com.myapp.files;

import android.content.Context;
import android.content.res.ColorStateList;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Floating tab bar: Home, Files, Apps. The active tab is an ink pill with its label. */
final class NavBar extends FrameLayout {
    interface Listener {
        void tab(int index);
    }

    private final LinearLayout pill;
    private final LinearLayout[] items = new LinearLayout[3];
    private final ImageView[] icons = new ImageView[3];
    private final TextView[] labels = new TextView[3];
    private int selected = -1;
    private boolean shown = true;

    NavBar(Context c, Listener l) {
        super(c);
        setClipChildren(false);
        setClipToPadding(false);
        pill = Ui.row(c);
        pill.setBackground(Ui.card(c, 32));
        int p = Ui.dp(c, 7);
        pill.setPadding(p, p, p, p);
        Ui.elevate(pill, 18, 32);
        String[] names = {"Home", "Files", "Apps"};
        int[] res = {R.drawable.ic_home, R.drawable.ic_folder, R.drawable.ic_grid};
        for (int i = 0; i < 3; i++) {
            LinearLayout it = Ui.row(c);
            it.setGravity(Gravity.CENTER);
            it.setPadding(Ui.dp(c, 18), 0, Ui.dp(c, 18), 0);
            it.setMinimumWidth(Ui.dp(c, 64));
            ImageView ic = Ui.icon(c, res[i], Ui.INK, 22);
            it.addView(ic, new LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22)));
            TextView t = Ui.text(c, names[i], Ui.Type.LABEL);
            t.setTextColor(Ui.WHITE);
            t.setPadding(Ui.dp(c, 8), 0, 0, 0);
            t.setVisibility(GONE);
            it.addView(t);
            final int idx = i;
            it.setOnClickListener(v -> {
                Ui.tick(v);
                l.tab(idx);
            });
            Ui.pressable(it, 0.92f);
            pill.addView(it, new LinearLayout.LayoutParams(Ui.WRAP, Ui.dp(c, 50)));
            items[i] = it;
            icons[i] = ic;
            labels[i] = t;
        }
        addView(pill, new LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM));
    }

    void select(int index, boolean animate) {
        if (index == selected) return;
        if (animate) TransitionManager.beginDelayedTransition(pill, new AutoTransition().setDuration(280));
        selected = index;
        Context c = getContext();
        for (int i = 0; i < 3; i++) {
            boolean on = i == index;
            items[i].setBackground(on ? Ui.ink(c, 25) : null);
            icons[i].setImageTintList(ColorStateList.valueOf(on ? Ui.WHITE : Ui.INK));
            labels[i].setVisibility(on ? VISIBLE : GONE);
        }
    }

    void show(boolean on) {
        if (on == shown) return;
        shown = on;
        pill.animate().cancel();
        if (on) {
            pill.setVisibility(VISIBLE);
            pill.animate().translationY(0).alpha(1f).setDuration(380).setInterpolator(Ui.DECELERATE).start();
        } else {
            pill.animate().translationY(Ui.dp(getContext(), 120)).alpha(0f).setDuration(240)
                    .setInterpolator(Ui.ACCELERATE).withEndAction(() -> pill.setVisibility(GONE)).start();
        }
    }
}
