package com.questionbubble.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Popover that grows out of the bubble: hero "Scan" action, a row of tiles, the last answer. */
final class MenuPopover {

    interface Actions {
        void scan();
        void type();
        void history();
        void settings();
        void copyLast();
    }

    private final Context c;
    private final WindowManager wm;
    private FrameLayout root;
    private LinearLayout panel;

    MenuPopover(Context c, WindowManager wm) {
        this.c = c;
        this.wm = wm;
    }

    boolean isShowing() {
        return root != null;
    }

    @SuppressLint("ClickableViewAccessibility")
    void show(Rect orb, Rect safe, boolean orbOnLeft, boolean busy, History.Item last,
              Actions actions, Runnable onOutside) {
        if (root != null) return;
        int shadowPad = dp(28);
        int panelW = Math.min(dp(300), safe.width() - dp(24) - orb.width() - dp(12));

        panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(16), dp(14), dp(14));
        panel.setBackground(Theme.rounded(0xF5121212, dp(28), Theme.HAIRLINE_STRONG, Math.max(1, dp(0.8f))));
        Theme.elevate(panel, 24, 28);

        List<View> stagger = new ArrayList<>();

        // header
        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), 0, dp(2), 0);
        TextView title = Theme.text(c, "Question Bubble", Theme.Type.TITLE_S);
        title.setTextSize(15.5f);
        header.addView(title, new LinearLayout.LayoutParams(0, Theme.WRAP, 1));
        header.addView(statusChip(busy));
        panel.addView(header);
        stagger.add(header);

        // hero action
        LinearLayout hero = new LinearLayout(c);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(12), dp(12), dp(14), dp(12));
        hero.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x1F000000),
                Theme.rounded(Theme.WHITE, dp(20)), Theme.rounded(Theme.WHITE, dp(20))));
        FrameLayout disc = new FrameLayout(c);
        disc.setBackground(Theme.rounded(Theme.BLACK, dp(21)));
        disc.addView(Theme.icon(c, R.drawable.ic_scan, Theme.WHITE, 20));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(42), dp(42));
        dlp.rightMargin = dp(12);
        hero.addView(disc, dlp);
        LinearLayout heroText = new LinearLayout(c);
        heroText.setOrientation(LinearLayout.VERTICAL);
        TextView ht = Theme.text(c, "Scan question", Theme.Type.TITLE_S);
        ht.setTextSize(15.5f);
        ht.setTextColor(Theme.BLACK);
        TextView hs = Theme.oneLine(Theme.text(c, "Drag to crop · Hold to detect", Theme.Type.LABEL));
        hs.setTextColor(0x8C000000);
        heroText.addView(ht);
        heroText.addView(hs, Theme.lp(Theme.WRAP, Theme.WRAP, dp(3)));
        hero.addView(heroText, new LinearLayout.LayoutParams(0, Theme.WRAP, 1));
        hero.addView(Theme.icon(c, R.drawable.ic_chevron_right, 0x66000000, 18));
        hero.setClickable(true);
        hero.setOnClickListener(v -> {
            Theme.tap(v);
            actions.scan();
        });
        Theme.pressable(hero, 0.97f);
        panel.addView(hero, Theme.lp(Theme.MATCH, Theme.WRAP, dp(14)));
        stagger.add(hero);

        // tiles
        LinearLayout tiles = new LinearLayout(c);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        View t1 = tile(R.drawable.ic_keyboard, "Type", actions::type);
        View t2 = tile(R.drawable.ic_history, "History", actions::history);
        View t3 = tile(R.drawable.ic_sliders, "Settings", actions::settings);
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, dp(80), 1);
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, dp(80), 1);
        LinearLayout.LayoutParams d = new LinearLayout.LayoutParams(0, dp(80), 1);
        b.leftMargin = dp(8);
        d.leftMargin = dp(8);
        tiles.addView(t1, a);
        tiles.addView(t2, b);
        tiles.addView(t3, d);
        panel.addView(tiles, Theme.lp(Theme.MATCH, Theme.WRAP, dp(8)));
        stagger.add(t1);
        stagger.add(t2);
        stagger.add(t3);

        // last answer
        if (last != null) {
            LinearLayout lastRow = new LinearLayout(c);
            lastRow.setOrientation(LinearLayout.HORIZONTAL);
            lastRow.setGravity(Gravity.CENTER_VERTICAL);
            lastRow.setPadding(dp(14), dp(12), dp(10), dp(12));
            lastRow.setBackground(Theme.rounded(0x0DFFFFFF, dp(18), Theme.HAIRLINE, Math.max(1, dp(0.8f))));
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(Theme.text(c, "Last answer · " + History.relative(last.time), Theme.Type.OVERLINE));
            TextView ans = Theme.text(c, last.answer, Theme.Type.CALLOUT);
            ans.setMaxLines(2);
            ans.setEllipsize(android.text.TextUtils.TruncateAt.END);
            ans.setLineSpacing(dp(2), 1f);
            col.addView(ans, Theme.lp(Theme.MATCH, Theme.WRAP, dp(6)));
            lastRow.addView(col, new LinearLayout.LayoutParams(0, Theme.WRAP, 1));
            FrameLayout copy = new FrameLayout(c);
            copy.setBackground(Theme.ripple(Theme.rounded(Theme.FILL, dp(18)), dp(18)));
            copy.addView(Theme.icon(c, R.drawable.ic_copy, Theme.TEXT, 17));
            copy.setClickable(true);
            copy.setOnClickListener(v -> {
                Theme.tap(v);
                actions.copyLast();
            });
            Theme.pressable(copy, 0.9f);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(36), dp(36));
            cp.leftMargin = dp(10);
            lastRow.addView(copy, cp);
            panel.addView(lastRow, Theme.lp(Theme.MATCH, Theme.WRAP, dp(8)));
            stagger.add(lastRow);
        }

        root = new FrameLayout(c);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setPadding(shadowPad, shadowPad, shadowPad, shadowPad);
        root.addView(panel, new FrameLayout.LayoutParams(panelW, Theme.WRAP));

        root.measure(View.MeasureSpec.makeMeasureSpec(panelW + shadowPad * 2, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int panelH = panel.getMeasuredHeight();

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                panelW + shadowPad * 2, ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        int gap = dp(12);
        int panelX = orbOnLeft ? orb.right + gap : orb.left - gap - panelW;
        int panelY = orb.centerY() - panelH / 2;
        panelY = Math.max(safe.top + dp(8), Math.min(safe.bottom - panelH - dp(8), panelY));
        p.x = panelX - shadowPad;
        p.y = panelY - shadowPad;

        root.setOnTouchListener((v, e) -> {
            int act = e.getActionMasked();
            boolean outsidePanel = act == MotionEvent.ACTION_DOWN && panel != null
                    && (e.getX() < panel.getLeft() || e.getX() > panel.getRight()
                    || e.getY() < panel.getTop() || e.getY() > panel.getBottom());
            if (act == MotionEvent.ACTION_OUTSIDE || outsidePanel) {
                onOutside.run();
                return true;
            }
            return false;
        });
        wm.addView(root, p);

        // grow out of the bubble
        panel.setPivotX(orbOnLeft ? 0 : panelW);
        panel.setPivotY(Math.max(0, Math.min(panelH, orb.centerY() - panelY)));
        panel.setAlpha(0f);
        panel.setScaleX(0.6f);
        panel.setScaleY(0.6f);
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(460)
                .setInterpolator(new android.view.animation.OvershootInterpolator(0.9f)).start();
        for (int i = 0; i < stagger.size(); i++) {
            View s = stagger.get(i);
            s.setAlpha(0f);
            s.setTranslationY(dp(10));
            s.animate().alpha(1f).translationY(0).setStartDelay(60 + 35L * i).setDuration(420)
                    .setInterpolator(Theme.DECELERATE).start();
        }
    }

    private View statusChip(boolean busy) {
        LinearLayout chip = new LinearLayout(c);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(9), dp(5), dp(10), dp(5));
        chip.setBackground(Theme.rounded(Theme.FILL, dp(12)));
        View dot = new View(c);
        dot.setBackground(Theme.rounded(busy ? Theme.WHITE : 0xB3FFFFFF, dp(3)));
        LinearLayout.LayoutParams dp6 = new LinearLayout.LayoutParams(dp(6), dp(6));
        dp6.rightMargin = dp(6);
        chip.addView(dot, dp6);
        TextView t = Theme.text(c, busy ? "Working" : "Ready", Theme.Type.LABEL);
        t.setTextSize(12f);
        chip.addView(t);
        if (busy) {
            android.animation.ObjectAnimator pulse = android.animation.ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 0.25f);
            pulse.setDuration(700);
            pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            pulse.start();
        }
        return chip;
    }

    private View tile(int icon, String label, Runnable action) {
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Theme.ripple(Theme.rounded(Theme.FILL, dp(20), Theme.HAIRLINE, Math.max(1, dp(0.8f))), dp(20)));
        t.addView(Theme.icon(c, icon, Theme.TEXT, 22), new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView l = Theme.text(c, label, Theme.Type.LABEL);
        l.setTextColor(Theme.TEXT_2);
        t.addView(l, Theme.lp(Theme.WRAP, Theme.WRAP, dp(9)));
        t.setClickable(true);
        t.setOnClickListener(v -> {
            Theme.tap(v);
            action.run();
        });
        Theme.pressable(t, 0.94f);
        return t;
    }

    void dismiss() {
        if (root == null) return;
        View r = root;
        View pnl = panel;
        root = null;
        panel = null;
        pnl.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setStartDelay(0).setDuration(170)
                .setInterpolator(Theme.ACCELERATE).withEndAction(() -> {
                    try {
                        wm.removeView(r);
                    } catch (Exception ignored) {
                    }
                }).start();
    }

    void dismissNow() {
        if (root == null) return;
        try {
            wm.removeView(root);
        } catch (Exception ignored) {
        }
        root = null;
        panel = null;
    }

    private int dp(float v) {
        return Theme.dp(c, v);
    }
}
