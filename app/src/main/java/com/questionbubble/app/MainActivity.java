package com.questionbubble.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Home: bubble status, quick stats, recent answers and settings. */
public class MainActivity extends Activity {

    private LinearLayout content;
    private boolean firstRender = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Prefs.onboarded(this)) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        getWindow().setDecorFitsSystemWindows(false);
        Fonts.init(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Theme.BG);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(14), dp(20), dp(36));
        scroll.addView(content);
        Theme.insets(scroll, true, true, false);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null) render();
    }

    private void render() {
        content.removeAllViews();
        boolean access = Access.enabled(this);
        boolean visible = Prefs.bubbleVisible(this);
        boolean hasKey = !Prefs.apiKey(this).isEmpty();
        int step = 0;

        // ---- top bar
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Theme.text(this, "Question Bubble", Theme.Type.HEADLINE);
        bar.addView(title, new LinearLayout.LayoutParams(0, Theme.WRAP, 1));
        bar.addView(Rows.iconButton(this, R.drawable.ic_history, this::openHistory));
        add(bar, 8, step++);

        // ---- hero status card
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        hero.setBackground(Theme.rounded(Theme.SURFACE, dp(28), Theme.HAIRLINE, Math.max(1, dp(0.8f))));
        LinearLayout heroRow = new LinearLayout(this);
        heroRow.setOrientation(LinearLayout.HORIZONTAL);
        heroRow.setGravity(Gravity.CENTER_VERTICAL);
        OrbView orb = new OrbView(this, 10);
        orb.setDrawShadow(false);
        orb.setEnergy(0f);
        orb.setGlow(access && visible ? 0.4f : 0f);
        if (!(access && visible)) orb.setAlpha(0.45f);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(dp(78), dp(78));
        op.rightMargin = dp(12);
        heroRow.addView(orb, op);
        LinearLayout heroText = new LinearLayout(this);
        heroText.setOrientation(LinearLayout.VERTICAL);
        String state = !access ? "Needs access" : visible ? "Bubble is on" : "Bubble is hidden";
        String sub = !access ? "Allow Accessibility to start" : visible ? "Floating over your apps" : "Switch it on to bring it back";
        heroText.addView(Theme.text(this, state, Theme.Type.TITLE));
        heroText.addView(Theme.text(this, sub, Theme.Type.CAPTION), Theme.lp(Theme.WRAP, Theme.WRAP, dp(5)));
        heroRow.addView(heroText, new LinearLayout.LayoutParams(0, Theme.WRAP, 1));
        if (access) {
            ToggleView toggle = new ToggleView(this, visible);
            toggle.setListener(on -> {
                Prefs.setBubbleVisible(this, on);
                orb.animate().alpha(on ? 1f : 0.45f).setDuration(300).start();
                orb.setGlow(on ? 0.4f : 0f);
                if (on) orb.ripple();
                content.postDelayed(this::render, 380);
            });
            heroRow.addView(toggle);
        }
        hero.addView(heroRow);
        if (!access) {
            PillButton grant = new PillButton(this, "Grant access", PillButton.Kind.PRIMARY);
            grant.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
            hero.addView(grant, Theme.lp(Theme.MATCH, Theme.WRAP, dp(18)));
            TextView greyed = Theme.text(this, "Switch greyed out?", Theme.Type.LABEL);
            greyed.setGravity(Gravity.CENTER);
            greyed.setPadding(0, dp(14), 0, dp(2));
            greyed.setOnClickListener(v -> Sheets.restricted(this));
            hero.addView(greyed, Theme.lp(Theme.MATCH, Theme.WRAP));
        }
        if (!hasKey) {
            LinearLayout warn = new LinearLayout(this);
            warn.setOrientation(LinearLayout.HORIZONTAL);
            warn.setGravity(Gravity.CENTER_VERTICAL);
            warn.setPadding(dp(14), dp(12), dp(10), dp(12));
            warn.setBackground(Theme.ripple(Theme.rounded(Theme.FILL, dp(16)), dp(16)));
            FrameLayout ic = new FrameLayout(this);
            ic.addView(Theme.icon(this, R.drawable.ic_key, Theme.TEXT, 18));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(20), dp(20));
            ip.rightMargin = dp(12);
            warn.addView(ic, ip);
            warn.addView(Theme.text(this, "Add your OpenAI key to start", Theme.Type.CALLOUT),
                    new LinearLayout.LayoutParams(0, Theme.WRAP, 1));
            warn.addView(Rows.value(this, null));
            warn.setClickable(true);
            warn.setOnClickListener(v -> Sheets.apiKey(this, this::render));
            hero.addView(warn, Theme.lp(Theme.MATCH, Theme.WRAP, dp(16)));
        }
        add(hero, 20, step++);

        // ---- stats
        List<History.Item> items = History.list(this);
        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(stat(String.valueOf(History.countToday(this)), "Answers today"),
                new LinearLayout.LayoutParams(0, Theme.WRAP, 1));
        LinearLayout.LayoutParams s2 = new LinearLayout.LayoutParams(0, Theme.WRAP, 1);
        s2.leftMargin = dp(10);
        stats.addView(stat(String.valueOf(items.size()), "Saved answers"), s2);
        add(stats, 10, step++);

        // ---- recent
        LinearLayout recentHead = new LinearLayout(this);
        recentHead.setOrientation(LinearLayout.HORIZONTAL);
        recentHead.setGravity(Gravity.CENTER_VERTICAL);
        recentHead.addView(Rows.section(this, "Recent"), new LinearLayout.LayoutParams(0, Theme.WRAP, 1));
        if (!items.isEmpty()) {
            TextView all = Theme.text(this, "See all", Theme.Type.LABEL);
            all.setTextColor(Theme.TEXT);
            all.setPadding(dp(10), dp(6), dp(6), dp(6));
            all.setOnClickListener(v -> openHistory());
            recentHead.addView(all);
        }
        add(recentHead, 30, step++);

        if (items.isEmpty()) {
            add(empty(), 10, step++);
        } else {
            LinearLayout g = Rows.group(this);
            for (int i = 0; i < Math.min(3, items.size()); i++) Rows.add(g, answerRow(items.get(i)));
            add(g, 10, step++);
        }

        // ---- settings
        add(Rows.section(this, "Answers"), 30, step++);
        LinearLayout ai = Rows.group(this);
        Rows.add(ai, Rows.row(this, R.drawable.ic_key, "API key", null, Rows.value(this, Prefs.maskedKey(this)),
                () -> Sheets.apiKey(this, this::render)));
        Rows.add(ai, Rows.row(this, R.drawable.ic_cpu, "Model", null, Rows.value(this, Prefs.model(this)),
                () -> Sheets.model(this, this::render)));
        LinearLayout styleRow = Rows.row(this, R.drawable.ic_sparkles, "Answer style",
                Prefs.detailed(this) ? "Answer plus a short why" : "Just the answer", null, null);
        LinearLayout styleWrap = new LinearLayout(this);
        styleWrap.setOrientation(LinearLayout.VERTICAL);
        styleWrap.addView(styleRow);
        SegmentedView style = new SegmentedView(this, new String[]{"Short", "Detailed"}, Prefs.detailed(this) ? 1 : 0);
        style.setListener(i -> {
            Prefs.setDetailed(this, i == 1);
            TextView subT = (TextView) ((LinearLayout) styleRow.getChildAt(1)).getChildAt(1);
            subT.setText(i == 1 ? "Answer plus a short why" : "Just the answer");
        });
        LinearLayout.LayoutParams slp = Theme.lp(Theme.MATCH, Theme.WRAP);
        slp.leftMargin = dp(66);
        slp.rightMargin = dp(16);
        slp.bottomMargin = dp(16);
        styleWrap.addView(style, slp);
        Rows.add(ai, styleWrap);
        add(ai, 10, step++);

        add(Rows.section(this, "Bubble"), 30, step++);
        LinearLayout bub = Rows.group(this);
        SegmentedView size = new SegmentedView(this, new String[]{"S", "M", "L"}, Prefs.bubbleSize(this));
        size.setLayoutParams(new LinearLayout.LayoutParams(dp(138), Theme.WRAP));
        size.setListener(i -> Prefs.setBubbleSize(this, i));
        Rows.add(bub, Rows.row(this, R.drawable.ic_circle_dot, "Size", null, size, null));
        ToggleView hap = new ToggleView(this, Prefs.haptics(this));
        hap.setListener(on -> Prefs.setHaptics(this, on));
        Rows.add(bub, Rows.row(this, R.drawable.ic_vibrate, "Haptics", null, hap, null));
        Rows.add(bub, Rows.row(this, R.drawable.ic_shield, "Accessibility", access ? "On" : "Off",
                Rows.value(this, null), () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        add(bub, 10, step++);

        add(Rows.section(this, "General"), 30, step++);
        LinearLayout gen = Rows.group(this);
        Rows.add(gen, Rows.row(this, R.drawable.ic_replay, "Replay intro", null, Rows.value(this, null), () -> {
            startActivity(new Intent(this, OnboardingActivity.class).putExtra(OnboardingActivity.EXTRA_REPLAY, true));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        }));
        Rows.add(gen, Rows.row(this, R.drawable.ic_trash, "Clear history", null, Rows.value(this, null),
                () -> Sheets.confirm(this, "Clear history?", "All saved answers will be removed from this phone.",
                        "Clear history", () -> {
                            History.clear(this);
                            render();
                            Snack.show(this, "History cleared", R.drawable.ic_check);
                        })));
        add(gen, 10, step++);

        TextView foot = Theme.text(this, "Question Bubble · Version 1.0", Theme.Type.LABEL);
        foot.setTextColor(Theme.TEXT_3);
        foot.setGravity(Gravity.CENTER);
        add(foot, 30, step);

        firstRender = false;
    }

    private void add(View v, int topDp, int index) {
        content.addView(v, Theme.lp(Theme.MATCH, Theme.WRAP, dp(topDp)));
        if (firstRender) Theme.enter(v, 40L * Math.min(index, 8));
    }

    private View stat(String value, String label) {
        LinearLayout s = new LinearLayout(this);
        s.setOrientation(LinearLayout.VERTICAL);
        s.setPadding(dp(18), dp(16), dp(18), dp(16));
        s.setBackground(Theme.card(this));
        TextView v = Theme.text(this, value, Theme.Type.HEADLINE);
        v.setFontFeatureSettings("tnum");
        s.addView(v);
        s.addView(Theme.text(this, label, Theme.Type.CAPTION), Theme.lp(Theme.WRAP, Theme.WRAP, dp(4)));
        return s;
    }

    private View empty() {
        LinearLayout e = new LinearLayout(this);
        e.setOrientation(LinearLayout.VERTICAL);
        e.setGravity(Gravity.CENTER_HORIZONTAL);
        e.setPadding(dp(24), dp(28), dp(24), dp(28));
        e.setBackground(Theme.card(this));
        FrameLayout tile = Theme.iconTile(this, R.drawable.ic_message, 44);
        e.addView(tile, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView t = Theme.text(this, "No answers yet", Theme.Type.BODY_M);
        t.setGravity(Gravity.CENTER);
        e.addView(t, Theme.lp(Theme.WRAP, Theme.WRAP, dp(14)));
        TextView s = Theme.text(this, "Tap the orb and scan a question. Answers show up here.", Theme.Type.CAPTION);
        s.setGravity(Gravity.CENTER);
        e.addView(s, Theme.lp(Theme.WRAP, Theme.WRAP, dp(6)));
        return e;
    }

    private View answerRow(History.Item it) {
        String meta = (it.source.equals("type") ? "Typed" : "Screen scan") + " · " + History.relative(it.time);
        LinearLayout r = Rows.row(this, it.source.equals("type") ? R.drawable.ic_message : R.drawable.ic_scan,
                firstLine(it.answer), meta, null, () -> {
                    ClipboardManager cm = getSystemService(ClipboardManager.class);
                    cm.setPrimaryClip(ClipData.newPlainText("answer", it.answer));
                    Snack.show(this, "Copied to clipboard", R.drawable.ic_check);
                });
        TextView t = (TextView) ((LinearLayout) r.getChildAt(1)).getChildAt(0);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout copy = new FrameLayout(this);
        copy.addView(Theme.icon(this, R.drawable.ic_copy, Theme.TEXT_3, 18));
        r.addView(copy, new LinearLayout.LayoutParams(dp(22), dp(22)));
        return r;
    }

    static String firstLine(String s) {
        int i = s.indexOf('\n');
        return i > 0 ? s.substring(0, i) : s;
    }

    private void openHistory() {
        startActivity(new Intent(this, HistoryActivity.class));
        overridePendingTransition(R.anim.screen_in, R.anim.screen_out);
    }

    private int dp(float v) {
        return Theme.dp(this, v);
    }
}
