package com.questionbubble.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** API key input with a Paste button and an inline status line. */
final class KeyField extends LinearLayout {

    private final EditText input;
    private final TextView status;
    private final GradientDrawable bg;

    KeyField(Context c) {
        super(c);
        setOrientation(VERTICAL);

        FrameLayout box = new FrameLayout(c);
        bg = Theme.rounded(Theme.SURFACE_3, Theme.dp(c, 18), Theme.HAIRLINE_STRONG, Math.max(1, Theme.dp(c, 1)));
        box.setBackground(bg);

        input = new EditText(c);
        input.setBackground(null);
        input.setHint("sk-...");
        input.setHintTextColor(Theme.TEXT_3);
        input.setTextColor(Theme.TEXT);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        input.setTypeface(Fonts.weight(c, 500));
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setPadding(Theme.dp(c, 18), Theme.dp(c, 18), Theme.dp(c, 96), Theme.dp(c, 18));
        GradientDrawable cursor = new GradientDrawable();
        cursor.setColor(Theme.ACCENT);
        cursor.setSize(Theme.dp(c, 2), Theme.dp(c, 20));
        input.setTextCursorDrawable(cursor);
        input.setOnFocusChangeListener((v, has) -> bg.setStroke(Math.max(1, Theme.dp(c, 1)), has ? Theme.ACCENT : Theme.HAIRLINE_STRONG));
        box.addView(input, new FrameLayout.LayoutParams(Theme.MATCH, Theme.WRAP));

        TextView paste = Theme.text(c, "Paste", Theme.Type.LABEL);
        paste.setTextColor(Theme.BLACK);
        paste.setTypeface(Fonts.weight(c, 600));
        paste.setGravity(Gravity.CENTER);
        paste.setPadding(Theme.dp(c, 14), 0, Theme.dp(c, 14), 0);
        paste.setBackground(Theme.rounded(Theme.ACCENT, Theme.dp(c, 15)));
        paste.setClickable(true);
        paste.setOnClickListener(v -> {
            Theme.tap(v);
            ClipboardManager cm = c.getSystemService(ClipboardManager.class);
            ClipData clip = cm.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence t = clip.getItemAt(0).coerceToText(c);
                if (t != null) {
                    input.setText(t.toString().trim());
                    input.setSelection(input.getText().length());
                    clearStatus();
                }
            }
        });
        Theme.pressable(paste, 0.92f);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(Theme.WRAP, Theme.dp(c, 30),
                Gravity.END | Gravity.CENTER_VERTICAL);
        pp.rightMargin = Theme.dp(c, 12);
        box.addView(paste, pp);
        addView(box, Theme.lp(Theme.MATCH, Theme.WRAP));

        status = Theme.text(c, "", Theme.Type.CAPTION);
        status.setVisibility(GONE);
        status.setPadding(Theme.dp(c, 6), 0, 0, 0);
        addView(status, Theme.lp(Theme.MATCH, Theme.WRAP, Theme.dp(c, 10)));
    }

    void setHint(String hint) {
        input.setHint(hint);
    }

    String text() {
        return input.getText().toString().trim();
    }

    void setText(String s) {
        input.setText(s);
    }

    EditText editText() {
        return input;
    }

    void setError(String msg) {
        status.setText(msg);
        status.setTextColor(0xFFFF8A80);
        showStatus();
        bg.setStroke(Math.max(1, Theme.dp(getContext(), 1)), 0xFFFF8A80);
        Theme.shake(this);
        Theme.reject(this);
    }

    void setOk(String msg) {
        status.setText(msg);
        status.setTextColor(Theme.TEXT);
        showStatus();
        bg.setStroke(Math.max(1, Theme.dp(getContext(), 1)), Theme.ACCENT);
    }

    void clearStatus() {
        status.setVisibility(GONE);
        bg.setStroke(Math.max(1, Theme.dp(getContext(), 1)), input.hasFocus() ? Theme.ACCENT : Theme.HAIRLINE_STRONG);
    }

    private void showStatus() {
        if (status.getVisibility() != VISIBLE) {
            status.setVisibility(VISIBLE);
            status.setAlpha(0f);
            status.setTranslationY(-Theme.dp(getContext(), 4));
            status.animate().alpha(1f).translationY(0).setDuration(260).start();
        }
    }

    @Override
    public View findFocus() {
        return input;
    }
}
