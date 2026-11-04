package com.questionbubble.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Floating panel where you type a question; the answer is copied to the clipboard. */
public class TypeQuestionActivity extends Activity {

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout panel;
    private boolean closing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(18));
        ShineDrawable bg = Ui.panel(this, 20);
        panel.setBackground(bg);
        ShineDrawable.animateWhileAttached(panel, bg, 3600);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.label(this, "Type question"), new LinearLayout.LayoutParams(0, WRAP, 1));
        TextView model = Ui.text(this, Prefs.model(this).toUpperCase(), 9.5f, 700, Ui.FAINT);
        model.setLetterSpacing(0.12f);
        header.addView(model);
        panel.addView(header);

        View div = Ui.divider(this);
        panel.addView(div, Ui.lp(MATCH, dp(1), dp(12)));

        EditText input = Ui.input(this, "What do you want to know?", false);
        input.setMinLines(3);
        input.setMaxLines(7);
        input.setGravity(Gravity.TOP | Gravity.START);
        panel.addView(input, Ui.lp(MATCH, WRAP, dp(14)));

        // answer card (hidden until there is an answer)
        LinearLayout answerCard = new LinearLayout(this);
        answerCard.setOrientation(LinearLayout.VERTICAL);
        answerCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        answerCard.setBackground(Ui.rounded(0xFF141414, dp(12), Ui.SEP, dp(1)));
        TextView answerLabel = Ui.label(this, "Answer");
        answerCard.addView(answerLabel);
        TextView answer = Ui.text(this, "", 15, 500, Ui.TEXT);
        answer.setTextIsSelectable(true);
        answer.setLineSpacing(dp(3), 1f);
        answerCard.addView(answer, Ui.lp(MATCH, WRAP, dp(8)));
        TextView answerNote = Ui.text(this, "Copied to clipboard", 11.5f, 500, Ui.FAINT);
        answerCard.addView(answerNote, Ui.lp(MATCH, WRAP, dp(10)));
        answerCard.setVisibility(View.GONE);
        panel.addView(answerCard, Ui.lp(MATCH, WRAP, dp(12)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView close = Ui.secondaryButton(this, "Close");
        TextView ask = Ui.primaryButton(this, "Ask");
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, WRAP, 1);
        cp.rightMargin = dp(10);
        row.addView(close, cp);
        row.addView(ask, new LinearLayout.LayoutParams(0, WRAP, 1));
        panel.addView(row, Ui.lp(MATCH, WRAP, dp(16)));

        close.setOnClickListener(v -> animateClose());
        ask.setOnClickListener(v -> {
            String q = input.getText().toString().trim();
            if (q.isEmpty()) {
                shake(input);
                return;
            }
            ask.setEnabled(false);
            ask.setText("Thinking");
            ask.animate().alpha(0.7f).setDuration(150).start();
            String key = Prefs.apiKey(this);
            String m = Prefs.model(this);
            worker.execute(() -> {
                String result;
                boolean ok;
                try {
                    result = OpenAiClient.ask(key, m, "Question: " + q, null);
                    ok = true;
                } catch (Exception e) {
                    result = String.valueOf(e.getMessage());
                    ok = false;
                }
                final String text = result;
                final boolean success = ok;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    ask.setEnabled(true);
                    ask.setText("Ask");
                    ask.animate().alpha(1f).setDuration(150).start();
                    answerLabel.setText(success ? "ANSWER" : "ERROR");
                    answer.setText(text);
                    answerNote.setVisibility(success ? View.VISIBLE : View.GONE);
                    if (success) {
                        ClipboardManager cm = getSystemService(ClipboardManager.class);
                        cm.setPrimaryClip(ClipData.newPlainText("answer", text));
                    }
                    if (answerCard.getVisibility() != View.VISIBLE) {
                        answerCard.setVisibility(View.VISIBLE);
                        Ui.enter(answerCard, 0);
                    } else {
                        answer.setAlpha(0f);
                        answer.animate().alpha(1f).setDuration(260).start();
                    }
                });
            });
        });

        setContentView(panel);
        getWindow().setLayout(
                Math.min(dp(440), Math.round(getResources().getDisplayMetrics().widthPixels * 0.92f)),
                WRAP);
        Ui.popIn(panel);
        input.requestFocus();
    }

    private void shake(View v) {
        v.animate().translationX(dp(8)).setDuration(50).withEndAction(() ->
                v.animate().translationX(-dp(8)).setDuration(70).withEndAction(() ->
                        v.animate().translationX(0).setDuration(60).start()).start()).start();
    }

    private void animateClose() {
        if (closing) return;
        closing = true;
        panel.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f).setDuration(160)
                .setInterpolator(Ui.EXIT).withEndAction(this::finish).start();
    }

    @Override
    public void onBackPressed() {
        animateClose();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
