package com.questionbubble.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Popover that grows out of the bubble. Two swipeable pages: Tools (scan, type, history,
 * settings, last answer) and Chat (recent chats + a message box you can type into right here).
 */
final class MenuPopover {

    interface Actions {
        void scan();
        void type();
        void history();
        void settings();
        void copyLast();
        void chat(String chatId, boolean fresh);
        void send(String text, List<String> images);
        void attachScreen();
        void attachPhoto();
        void needKey();
    }

    private final Context c;
    private final WindowManager wm;
    private FrameLayout root;
    private LinearLayout panel;
    private WindowManager.LayoutParams params;
    private Composer composer;
    private Rect safe;
    private boolean focusable;

    MenuPopover(Context c, WindowManager wm) {
        this.c = c;
        this.wm = wm;
    }

    boolean isShowing() {
        return root != null;
    }

    String draftText() {
        return composer == null ? "" : composer.text();
    }

    List<String> draftImages() {
        return composer == null ? new ArrayList<>() : composer.images();
    }

    @SuppressLint("ClickableViewAccessibility")
    void show(Rect orb, Rect safeArea, boolean orbOnLeft, boolean busy, History.Item last,
              int startPage, String draftText, List<String> draftImages,
              Actions actions, Runnable onOutside) {
        if (root != null) return;
        safe = safeArea;
        int shadowPad = dp(28);
        int panelW = Math.min(dp(320), safe.width() - dp(24) - orb.width() - dp(12));

        panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(10), dp(10), dp(10));
        panel.setBackground(Theme.rounded((Theme.PANEL & 0x00FFFFFF) | 0xF5000000, dp(28), Theme.HAIRLINE_STRONG, Math.max(1, dp(0.8f))));
        Theme.elevate(panel, 24, 28);

        List<View> stagger = new ArrayList<>();

        // header: page tabs + status
        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        SegmentedView tabs = new SegmentedView(c, new String[]{"Tools", "Chat"}, startPage);
        tabs.setHeightDp(34);
        header.addView(tabs, new LinearLayout.LayoutParams(dp(140), Theme.WRAP));
        View spacer = new View(c);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        header.addView(statusChip(busy));
        panel.addView(header);
        stagger.add(header);

        PagerFrame pager = new PagerFrame(c);
        LinearLayout tools = new LinearLayout(c);
        tools.setOrientation(LinearLayout.VERTICAL);
        LinearLayout chatPage = new LinearLayout(c);
        chatPage.setOrientation(LinearLayout.VERTICAL);
        pager.addView(tools);
        pager.addView(chatPage);
        tabs.setListener(i -> pager.goTo(i, true));
        pager.setListener(tabs::select);
        panel.addView(pager, Theme.lp(Theme.MATCH, Theme.WRAP, dp(10)));

        // ---- page 1: tools
        View scan = heroAction(R.drawable.ic_scan, "Scan question", "Drag to crop · Hold to detect", actions::scan);
        tools.addView(scan, Theme.lp(Theme.MATCH, Theme.WRAP));
        stagger.add(scan);
        LinearLayout tiles = new LinearLayout(c);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        View t1 = tile(R.drawable.ic_keyboard, "Type", actions::type);
        View t2 = tile(R.drawable.ic_history, "History", actions::history);
        View t3 = tile(R.drawable.ic_sliders, "Settings", actions::settings);
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, dp(48), 1);
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, dp(48), 1);
        LinearLayout.LayoutParams d = new LinearLayout.LayoutParams(0, dp(48), 1);
        b.leftMargin = dp(6);
        d.leftMargin = dp(6);
        tiles.addView(t1, a);
        tiles.addView(t2, b);
        tiles.addView(t3, d);
        tools.addView(tiles, Theme.lp(Theme.MATCH, Theme.WRAP, dp(6)));
        stagger.add(t1);
        stagger.add(t2);
        stagger.add(t3);
        if (last != null) {
            View lastRow = lastAnswer(last, actions);
            tools.addView(lastRow, Theme.lp(Theme.MATCH, Theme.WRAP, dp(6)));
            stagger.add(lastRow);
        }

        // ---- page 2: chat
        TextView hello = Theme.text(c, "Ask " + Prefs.provider(c).label + " anything", Theme.Type.TITLE_S);
        hello.setTextSize(16f);
        hello.setPadding(dp(4), dp(2), 0, 0);
        chatPage.addView(hello, Theme.lp(Theme.MATCH, Theme.WRAP));

        // quick starts
        android.widget.HorizontalScrollView sugScroll = new android.widget.HorizontalScrollView(c);
        sugScroll.setHorizontalScrollBarEnabled(false);
        sugScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout sug = new LinearLayout(c);
        sug.setOrientation(LinearLayout.HORIZONTAL);
        sug.addView(pill(R.drawable.ic_scan, "Explain my screen", true, () -> {
            if (composer != null && composer.text().trim().isEmpty()) composer.setText("Explain what's on my screen");
            actions.attachScreen();
        }));
        sug.addView(pill(R.drawable.ic_sparkles, "Summarize", false, () -> startTyping("Summarize this: ")));
        sug.addView(pill(R.drawable.ic_globe, "Translate", false, () -> startTyping("Translate this to English: ")));
        sug.addView(pill(R.drawable.ic_edit, "Write a reply", false, () -> startTyping("Help me write a reply to: ")));
        sugScroll.addView(sug);
        chatPage.addView(sugScroll, Theme.lp(Theme.MATCH, Theme.WRAP, dp(10)));

        // recent chats, side-scrolling
        List<Chats.Chat> recent = Chats.list(c);
        if (!recent.isEmpty()) {
            TextView label = Theme.text(c, "Recent", Theme.Type.OVERLINE);
            label.setPadding(dp(4), 0, 0, 0);
            chatPage.addView(label, Theme.lp(Theme.MATCH, Theme.WRAP, dp(12)));
            android.widget.HorizontalScrollView recScroll = new android.widget.HorizontalScrollView(c);
            recScroll.setHorizontalScrollBarEnabled(false);
            recScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            LinearLayout rec = new LinearLayout(c);
            rec.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < Math.min(6, recent.size()); i++) {
                Chats.Chat ch = recent.get(i);
                rec.addView(recentCard(ch, () -> actions.chat(ch.id, false)));
            }
            recScroll.addView(rec);
            chatPage.addView(recScroll, Theme.lp(Theme.MATCH, Theme.WRAP, dp(6)));
        }

        // inline attach options
        LinearLayout attach = new LinearLayout(c);
        attach.setOrientation(LinearLayout.HORIZONTAL);
        attach.setVisibility(View.GONE);
        View cap = tile(R.drawable.ic_scan, "Capture screen", () -> {
            attach.setVisibility(View.GONE);
            actions.attachScreen();
        });
        View pic = tile(R.drawable.ic_image, "Photo library", () -> {
            attach.setVisibility(View.GONE);
            actions.attachPhoto();
        });
        LinearLayout.LayoutParams ca = new LinearLayout.LayoutParams(0, dp(48), 1);
        LinearLayout.LayoutParams cb = new LinearLayout.LayoutParams(0, dp(48), 1);
        cb.leftMargin = dp(8);
        attach.addView(cap, ca);
        attach.addView(pic, cb);
        chatPage.addView(attach, Theme.lp(Theme.MATCH, Theme.WRAP, dp(10)));

        // inline model menu
        FrameLayout models = new FrameLayout(c) {
            @Override
            protected void onMeasure(int w, int h) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(dp(300), MeasureSpec.AT_MOST));
            }
        };
        models.setVisibility(View.GONE);
        chatPage.addView(models, Theme.lp(Theme.MATCH, Theme.WRAP, dp(10)));

        composer = new Composer(c, new Composer.Host() {
            @Override
            public void onSend(String text, List<String> images) {
                actions.send(text, images);
            }

            @Override
            public void onAttach() {
                models.setVisibility(View.GONE);
                boolean show = attach.getVisibility() != View.VISIBLE;
                attach.setVisibility(show ? View.VISIBLE : View.GONE);
                if (show) {
                    attach.setAlpha(0f);
                    attach.animate().alpha(1f).setDuration(220).start();
                }
                keepOnScreen();
            }

            @Override
            public void onModel() {
                attach.setVisibility(View.GONE);
                if (models.getVisibility() == View.VISIBLE) {
                    models.setVisibility(View.GONE);
                    keepOnScreen();
                    return;
                }
                models.removeAllViews();
                ScrollView sv = new ScrollView(c);
                sv.setVerticalScrollBarEnabled(false);
                sv.addView(ModelList.build(c, true, new ModelList.Callback() {
                    @Override
                    public void onPicked() {
                        models.setVisibility(View.GONE);
                        composer.refresh();
                        keepOnScreen();
                    }

                    @Override
                    public void onNeedsKey(Provider p, String model) {
                        actions.needKey();
                    }
                }));
                models.addView(sv);
                models.setVisibility(View.VISIBLE);
                models.setAlpha(0f);
                models.animate().alpha(1f).setDuration(220).start();
                keepOnScreen();
            }

            @Override
            public void onInputFocus() {
                makeFocusable();
            }
        });
        composer.setText(draftText == null ? "" : draftText);
        if (draftImages != null) composer.setImages(draftImages);
        chatPage.addView(composer, Theme.lp(Theme.MATCH, Theme.WRAP, dp(8)));

        // window
        root = new FrameLayout(c) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    onOutside.run();
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }
        };
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setPadding(shadowPad, shadowPad, shadowPad, shadowPad);
        root.addView(panel, new FrameLayout.LayoutParams(panelW, Theme.WRAP));

        root.measure(View.MeasureSpec.makeMeasureSpec(panelW + shadowPad * 2, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int panelH = panel.getMeasuredHeight();

        params = new WindowManager.LayoutParams(
                panelW + shadowPad * 2, ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        int gap = dp(12);
        int panelX = orbOnLeft ? orb.right + gap : orb.left - gap - panelW;
        int panelY = orb.centerY() - panelH / 2;
        panelY = Math.max(safe.top + dp(8), Math.min(safe.bottom - panelH - dp(8), panelY));
        params.x = panelX - shadowPad;
        params.y = panelY - shadowPad;

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
        wm.addView(root, params);
        pager.goTo(startPage, false);

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

    /** Typing started: lift the menu to the top so the keyboard doesn't cover it, open the keyboard. */
    private void makeFocusable() {
        if (root == null || composer == null) return;
        if (!focusable) {
            focusable = true;
            params.y = safe.top + dp(4) - dp(28);
            try {
                wm.updateViewLayout(root, params);
            } catch (Exception ignored) {
            }
        }
        root.post(() -> {
            if (composer == null) return;
            composer.input().requestFocus();
            InputMethodManager imm = c.getSystemService(InputMethodManager.class);
            if (imm != null) imm.showSoftInput(composer.input(), InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void startTyping(String text) {
        if (composer == null) return;
        composer.setText(text);
        makeFocusable();
    }

    private View pill(int icon, String label, boolean strong, Runnable action) {
        LinearLayout p = new LinearLayout(c);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setPadding(dp(11), dp(7), dp(13), dp(7));
        int fg = strong ? Theme.BLACK : Theme.TEXT;
        p.setBackground(strong ? Theme.accentFill(dp(16))
                : Theme.ripple(Theme.rounded(Theme.FILL, dp(16), Theme.HAIRLINE, Math.max(1, dp(0.8f))), dp(16)));
        p.addView(Theme.icon(c, icon, fg, 14), new LinearLayout.LayoutParams(dp(14), dp(14)));
        TextView t = Theme.text(c, label, Theme.Type.LABEL);
        t.setTextColor(fg);
        t.setTypeface(Fonts.weight(c, 600));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(Theme.WRAP, Theme.WRAP);
        tp.leftMargin = dp(6);
        p.addView(t, tp);
        p.setClickable(true);
        p.setOnClickListener(v -> {
            Theme.tap(v);
            action.run();
        });
        Theme.pressable(p, 0.93f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.WRAP, dp(32));
        lp.rightMargin = dp(6);
        p.setLayoutParams(lp);
        return p;
    }

    private View recentCard(Chats.Chat ch, Runnable action) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(9), dp(12), dp(9));
        card.setBackground(Theme.ripple(Theme.rounded(Theme.FILL, dp(16), Theme.HAIRLINE, Math.max(1, dp(0.8f))), dp(16)));
        TextView title = Theme.oneLine(Theme.text(c, ch.title.isEmpty() ? "Chat" : ch.title, Theme.Type.CALLOUT));
        title.setMaxWidth(dp(150));
        card.addView(title);
        TextView sub = Theme.oneLine(Theme.text(c, History.relative(ch.updated), Theme.Type.LABEL));
        sub.setTextColor(Theme.TEXT_3);
        card.addView(sub, Theme.lp(Theme.WRAP, Theme.WRAP, dp(3)));
        card.setClickable(true);
        card.setOnClickListener(v -> {
            Theme.tap(v);
            action.run();
        });
        Theme.pressable(card, 0.95f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(164), Theme.WRAP);
        lp.rightMargin = dp(6);
        card.setLayoutParams(lp);
        return card;
    }

    /** After the content grows, nudge the window up so it stays on screen. */
    private void keepOnScreen() {
        if (root == null) return;
        root.post(() -> {
            if (root == null) return;
            int h = root.getHeight();
            int maxY = safe.bottom - h + dp(28);
            if (params.y > maxY) {
                params.y = Math.max(safe.top - dp(24), maxY);
                try {
                    wm.updateViewLayout(root, params);
                } catch (Exception ignored) {
                }
            }
        });
    }

    private View lastAnswer(History.Item last, Actions actions) {
        LinearLayout lastRow = new LinearLayout(c);
        lastRow.setOrientation(LinearLayout.HORIZONTAL);
        lastRow.setGravity(Gravity.CENTER_VERTICAL);
        lastRow.setPadding(dp(12), dp(8), dp(8), dp(8));
        lastRow.setBackground(Theme.rounded(0x0DFFFFFF, dp(18), Theme.HAIRLINE, Math.max(1, dp(0.8f))));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Theme.text(c, "Last answer · " + History.relative(last.time), Theme.Type.OVERLINE));
        TextView ans = Theme.text(c, last.answer, Theme.Type.CALLOUT);
        ans.setMaxLines(1);
        ans.setEllipsize(android.text.TextUtils.TruncateAt.END);
        ans.setLineSpacing(dp(2), 1f);
        col.addView(ans, Theme.lp(Theme.MATCH, Theme.WRAP, dp(4)));
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
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(32), dp(32));
        cp.leftMargin = dp(8);
        lastRow.addView(copy, cp);
        return lastRow;
    }

    private View heroAction(int icon, String title, String sub, Runnable action) {
        LinearLayout hero = new LinearLayout(c);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(10), dp(8), dp(12), dp(8));
        hero.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x1F000000),
                Theme.accentFill(dp(20)), Theme.rounded(Theme.ACCENT, dp(20))));
        FrameLayout disc = new FrameLayout(c);
        disc.setBackground(Theme.rounded(Theme.BLACK, dp(18)));
        disc.addView(Theme.icon(c, icon, Theme.ACCENT, 18));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(36), dp(36));
        dlp.rightMargin = dp(10);
        hero.addView(disc, dlp);
        LinearLayout heroText = new LinearLayout(c);
        heroText.setOrientation(LinearLayout.VERTICAL);
        TextView ht = Theme.text(c, title, Theme.Type.TITLE_S);
        ht.setTextSize(15f);
        ht.setTextColor(Theme.BLACK);
        TextView hs = Theme.oneLine(Theme.text(c, sub, Theme.Type.LABEL));
        hs.setTextSize(12f);
        hs.setTextColor(0x8C000000);
        heroText.addView(ht);
        heroText.addView(hs, Theme.lp(Theme.WRAP, Theme.WRAP, dp(2)));
        hero.addView(heroText, new LinearLayout.LayoutParams(0, Theme.WRAP, 1));
        hero.addView(Theme.icon(c, R.drawable.ic_chevron_right, 0x66000000, 18));
        hero.setClickable(true);
        hero.setOnClickListener(v -> {
            Theme.tap(v);
            action.run();
        });
        Theme.pressable(hero, 0.97f);
        return hero;
    }

    private View chatRow(Chats.Chat ch, Runnable action) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(12), dp(7), dp(12), dp(7));
        r.setBackground(Theme.ripple(Theme.rounded(Theme.FILL, dp(14), Theme.HAIRLINE, Math.max(1, dp(0.8f))), dp(14)));
        FrameLayout ic = new FrameLayout(c);
        ic.addView(Theme.icon(c, R.drawable.ic_message, Theme.TEXT_2, 17));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(20), dp(20));
        ip.rightMargin = dp(12);
        r.addView(ic, ip);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Theme.oneLine(Theme.text(c, ch.title.isEmpty() ? "Chat" : ch.title, Theme.Type.CALLOUT)));
        TextView sub = Theme.oneLine(Theme.text(c, History.relative(ch.updated) + " · " + ch.preview(), Theme.Type.LABEL));
        sub.setTextColor(Theme.TEXT_3);
        col.addView(sub, Theme.lp(Theme.MATCH, Theme.WRAP, dp(2)));
        r.addView(col, new LinearLayout.LayoutParams(0, Theme.WRAP, 1));
        r.setClickable(true);
        r.setOnClickListener(v -> {
            Theme.tap(v);
            action.run();
        });
        Theme.pressable(r, 0.97f);
        return r;
    }

    private View statusChip(boolean busy) {
        LinearLayout chip = new LinearLayout(c);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(8), dp(4), dp(9), dp(4));
        chip.setBackground(Theme.rounded(Theme.FILL, dp(12)));
        View dot = new View(c);
        dot.setBackground(Theme.rounded(busy ? Theme.ACCENT : 0xB3FFFFFF, dp(3)));
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
        t.setOrientation(LinearLayout.HORIZONTAL);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(6), 0, dp(6), 0);
        t.setBackground(Theme.ripple(Theme.rounded(Theme.FILL, dp(16), Theme.HAIRLINE, Math.max(1, dp(0.8f))), dp(16)));
        t.addView(Theme.icon(c, icon, Theme.TEXT, 17), new LinearLayout.LayoutParams(dp(17), dp(17)));
        TextView l = Theme.oneLine(Theme.text(c, label, Theme.Type.LABEL));
        l.setTextColor(Theme.TEXT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.WRAP, Theme.WRAP);
        lp.leftMargin = dp(6);
        t.addView(l, lp);
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
        composer = null;
        focusable = false;
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
        composer = null;
        focusable = false;
    }

    private int dp(float v) {
        return Theme.dp(c, v);
    }
}
