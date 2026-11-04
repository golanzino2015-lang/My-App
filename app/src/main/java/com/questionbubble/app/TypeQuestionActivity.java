package com.questionbubble.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Chat-style sheet over the current app: type a question, the answer is copied automatically. */
public class TypeQuestionActivity extends Activity {

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private View scrim;
    private LinearLayout sheet;
    private EditText input;
    private FrameLayout send;
    private ImageView sendIcon;
    private LinearLayout answerCard;
    private TextView answerLabel, answerText, askedText;
    private FrameLayout answerBody;
    private LinearLayout actions;
    private boolean closing, busy, sendEnabled;
    private String lastAnswer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        Fonts.init(this);

        FrameLayout root = new FrameLayout(this);
        scrim = new View(this);
        scrim.setBackgroundColor(Theme.SCRIM);
        scrim.setAlpha(0f);
        scrim.setOnClickListener(v -> close());
        root.addView(scrim, new FrameLayout.LayoutParams(Theme.MATCH, Theme.MATCH));

        sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true);
        float r = dp(30);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.SURFACE_2);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        bg.setStroke(Math.max(1, dp(0.8f)), Theme.HAIRLINE);
        sheet.setBackground(bg);
        sheet.setPadding(dp(20), dp(10), dp(20), dp(16));

        View handle = new View(this);
        handle.setBackground(Theme.rounded(0x40FFFFFF, dp(3)));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(38), dp(5));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        sheet.addView(handle, hp);

        // header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(Theme.text(this, "Ask anything", Theme.Type.TITLE));
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.addView(chip(Prefs.model(this), null));
        TextView styleChip = chip(Prefs.detailed(this) ? "Detailed" : "Short", null);
        styleChip.setOnClickListener(v -> {
            boolean d = !Prefs.detailed(this);
            Prefs.setDetailed(this, d);
            styleChip.setText(d ? "Detailed" : "Short");
            Theme.tick(v);
        });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Theme.WRAP, Theme.WRAP);
        cp.leftMargin = dp(6);
        chips.addView(styleChip, cp);
        titles.addView(chips, Theme.lp(Theme.WRAP, Theme.WRAP, dp(8)));
        header.addView(titles, new LinearLayout.LayoutParams(0, Theme.WRAP, 1));
        header.addView(Rows.iconButton(this, R.drawable.ic_x, this::close));
        sheet.addView(header, Theme.lp(Theme.MATCH, Theme.WRAP, dp(14)));

        // answer card
        answerCard = new LinearLayout(this);
        answerCard.setOrientation(LinearLayout.VERTICAL);
        answerCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        answerCard.setBackground(Theme.rounded(Theme.SURFACE_3, dp(22), Theme.HAIRLINE, Math.max(1, dp(0.8f))));
        askedText = Theme.oneLine(Theme.text(this, "", Theme.Type.CAPTION));
        answerCard.addView(askedText);
        answerLabel = Theme.text(this, "Answer", Theme.Type.OVERLINE);
        answerCard.addView(answerLabel, Theme.lp(Theme.WRAP, Theme.WRAP, dp(12)));
        answerBody = new FrameLayout(this);
        answerText = Theme.text(this, "", Theme.Type.BODY_M);
        answerText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        answerText.setTextIsSelectable(true);
        answerText.setLineSpacing(dp(4), 1f);
        answerBody.addView(answerText, new FrameLayout.LayoutParams(Theme.MATCH, Theme.WRAP));
        answerCard.addView(answerBody, Theme.lp(Theme.MATCH, Theme.WRAP, dp(8)));
        actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        answerCard.addView(actions, Theme.lp(Theme.MATCH, Theme.WRAP, dp(14)));
        answerCard.setVisibility(View.GONE);
        sheet.addView(answerCard, Theme.lp(Theme.MATCH, Theme.WRAP, dp(16)));

        // input
        FrameLayout field = new FrameLayout(this);
        GradientDrawable fbg = Theme.rounded(Theme.SURFACE_3, dp(26), Theme.HAIRLINE_STRONG, Math.max(1, dp(1)));
        field.setBackground(fbg);
        input = new EditText(this);
        input.setBackground(null);
        input.setHint("Type your question");
        input.setHintTextColor(Theme.TEXT_3);
        input.setTextColor(Theme.TEXT);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        input.setTypeface(Fonts.weight(this, 400));
        input.setMaxLines(5);
        input.setMinHeight(dp(52));
        input.setPadding(dp(18), dp(14), dp(60), dp(14));
        GradientDrawable cursor = new GradientDrawable();
        cursor.setColor(Theme.WHITE);
        cursor.setSize(dp(2), dp(20));
        input.setTextCursorDrawable(cursor);
        input.setOnFocusChangeListener((v, has) -> fbg.setStroke(Math.max(1, dp(1)), has ? 0x66FFFFFF : Theme.HAIRLINE_STRONG));
        field.addView(input, new FrameLayout.LayoutParams(Theme.MATCH, Theme.WRAP));

        send = new FrameLayout(this);
        sendIcon = Theme.icon(this, R.drawable.ic_arrow_up, Theme.TEXT_3, 20);
        send.addView(sendIcon);
        send.setBackground(Theme.rounded(Theme.FILL, dp(20)));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.END | Gravity.BOTTOM);
        sp.rightMargin = dp(6);
        sp.bottomMargin = dp(6);
        field.addView(send, sp);
        send.setOnClickListener(v -> ask());
        Theme.pressable(send, 0.88f);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                setSendEnabled(s.toString().trim().length() > 0 && !busy);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        sheet.addView(field, Theme.lp(Theme.MATCH, Theme.WRAP, dp(16)));

        root.addView(sheet, new FrameLayout.LayoutParams(Theme.MATCH, Theme.WRAP, Gravity.BOTTOM));
        Theme.insets(sheet, false, true, true);
        setContentView(root);

        sheet.setVisibility(View.INVISIBLE);
        sheet.post(() -> {
            sheet.setTranslationY(sheet.getHeight());
            sheet.setVisibility(View.VISIBLE);
            sheet.animate().translationY(0).setDuration(520).setInterpolator(Theme.DECELERATE).start();
            scrim.animate().alpha(1f).setDuration(320).start();
        });
        input.requestFocus();
    }

    private TextView chip(String text, View.OnClickListener l) {
        TextView c = Theme.text(this, text, Theme.Type.LABEL);
        c.setTextSize(12f);
        c.setTextColor(Theme.TEXT_2);
        c.setPadding(dp(10), dp(5), dp(10), dp(5));
        c.setBackground(Theme.rounded(Theme.FILL, dp(12), Theme.HAIRLINE, Math.max(1, dp(0.8f))));
        if (l != null) c.setOnClickListener(l);
        return c;
    }

    private void setSendEnabled(boolean on) {
        if (sendEnabled == on) return;
        sendEnabled = on;
        send.setBackground(Theme.rounded(on ? Theme.WHITE : Theme.FILL, dp(20)));
        sendIcon.setImageTintList(ColorStateList.valueOf(on ? Theme.BLACK : Theme.TEXT_3));
        send.setScaleX(0.8f);
        send.setScaleY(0.8f);
        send.animate().scaleX(1f).scaleY(1f).setDuration(320)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2.4f)).start();
    }

    private void ask() {
        String q = input.getText().toString().trim();
        if (q.isEmpty() || busy) {
            if (q.isEmpty()) Theme.shake(input);
            return;
        }
        Theme.tap(send);
        busy = true;
        setSendEnabled(false);
        input.setText("");

        askedText.setText("You asked: " + q);
        answerLabel.setText("Answer");
        answerText.setText("");
        answerText.setVisibility(View.GONE);
        actions.removeAllViews();
        TypingDots dots = new TypingDots(this);
        answerBody.addView(dots, new FrameLayout.LayoutParams(Theme.WRAP, Theme.WRAP, Gravity.START | Gravity.CENTER_VERTICAL));
        if (answerCard.getVisibility() != View.VISIBLE) {
            answerCard.setVisibility(View.VISIBLE);
            answerCard.setAlpha(0f);
            answerCard.setScaleX(0.96f);
            answerCard.setScaleY(0.96f);
            answerCard.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(Theme.DECELERATE).start();
        }

        String key = Prefs.apiKey(this);
        String model = Prefs.model(this);
        boolean detailed = Prefs.detailed(this);
        worker.execute(() -> {
            String result;
            boolean ok;
            try {
                result = OpenAiClient.ask(key, model, detailed, "Question: " + q, null);
                History.add(this, "type", q, result);
                ok = true;
            } catch (Exception e) {
                result = String.valueOf(e.getMessage());
                ok = false;
            }
            final String text = result;
            final boolean success = ok;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                busy = false;
                answerBody.removeView(dots);
                answerText.setVisibility(View.VISIBLE);
                answerText.setText(text);
                answerText.setAlpha(0f);
                answerText.setTranslationY(dp(6));
                answerText.animate().alpha(1f).translationY(0).setDuration(380).setInterpolator(Theme.DECELERATE).start();
                if (success) {
                    lastAnswer = text;
                    copy(text);
                    Theme.confirm(answerText);
                    showCopiedActions();
                } else {
                    answerLabel.setText("Something went wrong");
                    Theme.reject(answerText);
                    Theme.shake(answerCard);
                }
                setSendEnabled(input.getText().toString().trim().length() > 0);
            });
        });
    }

    private void showCopiedActions() {
        actions.removeAllViews();
        LinearLayout copied = new LinearLayout(this);
        copied.setOrientation(LinearLayout.HORIZONTAL);
        copied.setGravity(Gravity.CENTER_VERTICAL);
        copied.setPadding(dp(8), dp(6), dp(12), dp(6));
        copied.setBackground(Theme.rounded(Theme.WHITE, dp(16)));
        copied.addView(Theme.icon(this, R.drawable.ic_check, Theme.BLACK, 14), new LinearLayout.LayoutParams(dp(16), dp(16)));
        TextView t = Theme.text(this, "Copied", Theme.Type.LABEL);
        t.setTextColor(Theme.BLACK);
        t.setTypeface(Fonts.weight(this, 600));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(Theme.WRAP, Theme.WRAP);
        tp.leftMargin = dp(6);
        copied.addView(t, tp);
        actions.addView(copied);
        copied.setScaleX(0.6f);
        copied.setScaleY(0.6f);
        copied.setAlpha(0f);
        copied.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(360)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();

        View spacer = new View(this);
        actions.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        PillButton again = new PillButton(this, "Copy again", PillButton.Kind.SECONDARY, R.drawable.ic_copy, true);
        again.setOnClickListener(v -> {
            if (lastAnswer != null) copy(lastAnswer);
            Snack.show(this, "Copied to clipboard", R.drawable.ic_check);
        });
        actions.addView(again);
    }

    private void copy(String text) {
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        cm.setPrimaryClip(ClipData.newPlainText("answer", text));
    }

    private void close() {
        if (closing) return;
        closing = true;
        android.view.inputmethod.InputMethodManager imm = getSystemService(android.view.inputmethod.InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        sheet.animate().translationY(sheet.getHeight()).setDuration(260).setInterpolator(Theme.ACCELERATE)
                .withEndAction(() -> {
                    finish();
                    overridePendingTransition(0, 0);
                }).start();
        scrim.animate().alpha(0f).setDuration(260).start();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        close();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private int dp(float v) {
        return Theme.dp(this, v);
    }
}
