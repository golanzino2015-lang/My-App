package com.questionbubble.app;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Chat with My AI. Everything runs on the phone. */
public class ChatActivity extends Activity {

    /** The conversation, kept while the app is open: {you, ai}. */
    private static final List<String[]> turns = new ArrayList<>();

    private LocalAI ai;
    private LinearLayout list;
    private ScrollView scroll;
    private EditText input;
    private View empty;
    private boolean thinking;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setDecorFitsSystemWindows(false);
        Fonts.init(this);
        ai = LocalAI.get(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Theme.background(this));
        Theme.insets(root, true, true, true);

        // ---- top bar
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(10), dp(16), dp(10));
        bar.addView(Rows.iconButton(this, R.drawable.ic_chevron_left, this::onBackPressed));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_HORIZONTAL);
        titles.addView(Theme.oneLine(Theme.text(this, ai.name(), Theme.Type.TITLE_S)));
        titles.addView(Theme.oneLine(Theme.text(this, "Trained on " + TrainActivity.compact(ai.tokens()) + " words · on this phone",
                Theme.Type.LABEL)), Theme.lp(Theme.WRAP, Theme.WRAP, dp(3)));
        bar.addView(titles, new LinearLayout.LayoutParams(0, Theme.WRAP, 1));
        bar.addView(Rows.iconButton(this, R.drawable.ic_plus, () -> {
            if (thinking) return;
            turns.clear();
            list.removeAllViews();
            empty.setVisibility(View.VISIBLE);
        }));
        root.addView(bar);

        // ---- messages
        FrameLayout middle = new FrameLayout(this);
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(8), dp(16), dp(16));
        scroll.addView(list);
        middle.addView(scroll);
        TextView hello = Theme.text(this, "Say hi to " + ai.name(), Theme.Type.TITLE);
        hello.setGravity(Gravity.CENTER);
        TextView sub = Theme.text(this, "It only knows what it learned in training. Teach it new answers on the main screen.",
                Theme.Type.CAPTION);
        sub.setGravity(Gravity.CENTER);
        LinearLayout emptyBox = new LinearLayout(this);
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.setGravity(Gravity.CENTER);
        emptyBox.setPadding(dp(40), 0, dp(40), 0);
        emptyBox.addView(hello);
        emptyBox.addView(sub, Theme.lp(Theme.MATCH, Theme.WRAP, dp(8)));
        empty = emptyBox;
        middle.addView(emptyBox, new FrameLayout.LayoutParams(Theme.MATCH, Theme.MATCH));
        root.addView(middle, new LinearLayout.LayoutParams(Theme.MATCH, 0, 1));

        // ---- message box
        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(16), dp(6), dp(16), dp(12));
        input = new EditText(this);
        input.setHint("Message " + ai.name());
        input.setHintTextColor(Theme.TEXT_3);
        input.setTextColor(Theme.TEXT);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        input.setTypeface(Fonts.weight(this, 400));
        input.setMaxLines(4);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_SEND || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                send();
                return true;
            }
            return false;
        });
        input.setPadding(dp(18), dp(13), dp(18), dp(13));
        input.setBackground(Theme.rounded(Theme.SURFACE_2, dp(24), Theme.HAIRLINE, Math.max(1, dp(0.8f))));
        composer.addView(input, new LinearLayout.LayoutParams(0, Theme.WRAP, 1));
        FrameLayout sendBtn = new FrameLayout(this);
        sendBtn.setBackground(Theme.accentFill(dp(23)));
        sendBtn.addView(Theme.icon(this, R.drawable.ic_arrow_up, Theme.BLACK, 20));
        sendBtn.setOnClickListener(v -> send());
        Theme.pressable(sendBtn, 0.9f);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(46), dp(46));
        sp.leftMargin = dp(10);
        composer.addView(sendBtn, sp);
        root.addView(composer);

        setContentView(root);
        for (String[] t : turns) {
            bubble(t[0], true);
            bubble(t[1], false);
        }
        empty.setVisibility(turns.isEmpty() ? View.VISIBLE : View.GONE);
        scrollDown();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.screen_back_in, R.anim.screen_back_out);
    }

    private void send() {
        String text = input.getText().toString().trim();
        if (text.isEmpty() || thinking) return;
        Theme.tap(input);
        input.setText("");
        empty.setVisibility(View.GONE);
        bubble(text, true);
        thinking = true;
        TypingDots dots = new TypingDots(this);
        LinearLayout.LayoutParams dotsLp = Theme.lp(Theme.WRAP, Theme.WRAP, dp(14));
        dotsLp.leftMargin = dp(6);
        list.addView(dots, dotsLp);
        scrollDown();
        List<String[]> history = new ArrayList<>(turns);
        new Thread(() -> {
            ai.ensureLoaded();
            String reply = ai.exists() ? ai.reply(history, text) : "";
            if (reply.trim().isEmpty()) reply = "…";
            String r = reply;
            runOnUiThread(() -> {
                list.removeView(dots);
                bubble(r, false);
                turns.add(new String[]{text, r});
                thinking = false;
                scrollDown();
            });
        }).start();
    }

    private void bubble(String text, boolean mine) {
        TextView t = Theme.text(this, text, Theme.Type.BODY_M);
        t.setTextIsSelectable(true);
        t.setLineSpacing(dp(4), 1f);
        t.setPadding(dp(16), dp(11), dp(16), dp(11));
        t.setMaxWidth(Math.round(getResources().getDisplayMetrics().widthPixels * 0.78f));
        if (mine) {
            t.setTextColor(Theme.BLACK);
            t.setBackground(Theme.accentFill(dp(20)));
        } else {
            t.setBackground(Theme.rounded(Theme.SURFACE_2, dp(20), Theme.HAIRLINE, Math.max(1, dp(0.8f))));
        }
        LinearLayout.LayoutParams p = Theme.lp(Theme.WRAP, Theme.WRAP, dp(mine ? 14 : 8));
        p.gravity = mine ? Gravity.END : Gravity.START;
        list.addView(t, p);
        Theme.enter(t, 0);
    }

    private void scrollDown() {
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private int dp(float v) {
        return Theme.dp(this, v);
    }
}
