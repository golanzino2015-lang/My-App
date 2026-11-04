package com.questionbubble.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** All saved answers, grouped by day. Tap to copy, long-press to delete. */
public class HistoryActivity extends Activity {

    private LinearLayout content;
    private boolean first = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setDecorFitsSystemWindows(false);
        Fonts.init(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Theme.BG);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(10), dp(20), dp(36));
        scroll.addView(content);
        Theme.insets(scroll, true, true, false);
        setContentView(scroll);
        render();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.screen_back_in, R.anim.screen_back_out);
    }

    private void render() {
        content.removeAllViews();
        List<History.Item> items = History.list(this);
        int step = 0;

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(Rows.iconButton(this, R.drawable.ic_chevron_left, this::onBackPressed));
        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        if (!items.isEmpty()) {
            PillButton clear = new PillButton(this, "Clear", PillButton.Kind.SECONDARY, R.drawable.ic_trash, true);
            clear.setOnClickListener(v -> Sheets.confirm(this, "Clear history?",
                    "All saved answers will be removed from this phone.", "Clear history", () -> {
                        History.clear(this);
                        render();
                        Snack.show(this, "History cleared", R.drawable.ic_check);
                    }));
            bar.addView(clear);
        }
        add(bar, 6, step++);

        add(Theme.text(this, "History", Theme.Type.DISPLAY), 22, step++);
        TextView sub = Theme.text(this, items.isEmpty() ? "Your answers will appear here."
                : items.size() + (items.size() == 1 ? " saved answer" : " saved answers") + " · tap to copy", Theme.Type.BODY);
        add(sub, 8, step++);

        if (items.isEmpty()) {
            LinearLayout e = new LinearLayout(this);
            e.setOrientation(LinearLayout.VERTICAL);
            e.setGravity(Gravity.CENTER_HORIZONTAL);
            e.setPadding(0, dp(56), 0, 0);
            OrbView orb = new OrbView(this, 16);
            orb.setDrawShadow(false);
            e.addView(orb, new LinearLayout.LayoutParams(dp(120), dp(120)));
            TextView t = Theme.text(this, "Nothing here yet", Theme.Type.TITLE_S);
            t.setGravity(Gravity.CENTER);
            e.addView(t, Theme.lp(Theme.WRAP, Theme.WRAP, dp(18)));
            TextView s = Theme.text(this, "Scan or type a question with the bubble.", Theme.Type.CAPTION);
            s.setGravity(Gravity.CENTER);
            e.addView(s, Theme.lp(Theme.WRAP, Theme.WRAP, dp(6)));
            add(e, 0, step);
            first = false;
            return;
        }

        String day = null;
        LinearLayout group = null;
        for (History.Item it : items) {
            String d = History.day(it.time);
            if (!d.equals(day)) {
                day = d;
                add(Rows.section(this, d), 28, step++);
                group = Rows.group(this);
                add(group, 10, step++);
            }
            Rows.add(group, item(it));
        }
        first = false;
    }

    private View item(History.Item it) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(dp(18), dp(16), dp(14), dp(16));
        r.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x14FFFFFF), null, null));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        if (!it.question.isEmpty()) {
            TextView q = Theme.text(this, it.question, Theme.Type.CAPTION);
            q.setMaxLines(2);
            q.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(q, Theme.lp(Theme.MATCH, Theme.WRAP, 0));
        }
        TextView a = Theme.text(this, it.answer, Theme.Type.BODY_M);
        a.setMaxLines(5);
        a.setEllipsize(android.text.TextUtils.TruncateAt.END);
        a.setLineSpacing(dp(3), 1f);
        col.addView(a, Theme.lp(Theme.MATCH, Theme.WRAP, it.question.isEmpty() ? 0 : dp(6)));
        TextView meta = Theme.text(this, (it.source.equals("type") ? "Typed" : "Screen scan") + " · "
                + History.relative(it.time), Theme.Type.LABEL);
        meta.setTextColor(Theme.TEXT_3);
        col.addView(meta, Theme.lp(Theme.WRAP, Theme.WRAP, dp(8)));
        r.addView(col, new LinearLayout.LayoutParams(0, Theme.WRAP, 1));

        FrameLayout copy = new FrameLayout(this);
        copy.addView(Theme.icon(this, R.drawable.ic_copy, Theme.TEXT_3, 18));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(22), dp(22));
        cp.leftMargin = dp(12);
        r.addView(copy, cp);

        r.setClickable(true);
        r.setOnClickListener(v -> {
            Theme.tap(v);
            ClipboardManager cm = getSystemService(ClipboardManager.class);
            cm.setPrimaryClip(ClipData.newPlainText("answer", it.answer));
            Snack.show(this, "Copied to clipboard", R.drawable.ic_check);
        });
        r.setOnLongClickListener(v -> {
            Theme.tick(v);
            Sheets.confirm(this, "Delete this answer?", MainActivity.firstLine(it.answer), "Delete", () -> {
                History.remove(this, it.time);
                render();
                Snack.show(this, "Deleted", R.drawable.ic_check);
            });
            return true;
        });
        return r;
    }

    private void add(View v, int topDp, int index) {
        content.addView(v, Theme.lp(Theme.MATCH, Theme.WRAP, dp(topDp)));
        if (first) Theme.enter(v, 35L * Math.min(index, 10));
    }

    private int dp(float v) {
        return Theme.dp(this, v);
    }
}
