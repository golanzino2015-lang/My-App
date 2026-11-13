package com.questionbubble.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The message box used everywhere (chat, home, bubble): attachments, text, then a row with
 * attach, Think, Search, the model switcher and send. Enter sends.
 */
final class Composer extends LinearLayout {

    interface Host {
        void onSend(String text, List<String> images);
        void onAttach();
        void onModel();
        default void onInputFocus() {
        }
    }

    private final Host host;
    private final List<String> images = new ArrayList<>();
    private final HorizontalScrollView attachScroll;
    private final LinearLayout attachRow;
    private final EditText input;
    private final FrameLayout thinkBtn;
    private final ImageView thinkIcon;
    private final TextView modelChip;
    private final FrameLayout send;
    private final ImageView sendIcon;
    private final GradientDrawable bg;
    private boolean busy, sendOn;

    Composer(Context c, Host host) {
        super(c);
        this.host = host;
        setOrientation(VERTICAL);
        setPadding(dp(8), dp(8), dp(8), dp(8));
        bg = Theme.rounded(Theme.SURFACE_2, dp(28), Theme.HAIRLINE_STRONG, Math.max(1, dp(1)));
        setBackground(bg);

        attachScroll = new HorizontalScrollView(c);
        attachScroll.setHorizontalScrollBarEnabled(false);
        attachRow = new LinearLayout(c);
        attachRow.setOrientation(HORIZONTAL);
        attachRow.setPadding(dp(6), dp(4), dp(6), dp(4));
        attachScroll.addView(attachRow);
        attachScroll.setVisibility(GONE);
        addView(attachScroll, Theme.lp(Theme.MATCH, Theme.WRAP));

        input = new EditText(c);
        input.setBackground(null);
        input.setTextColor(Theme.TEXT);
        input.setHintTextColor(Theme.TEXT_3);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        input.setTypeface(Fonts.weight(c, 400));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        input.setHorizontallyScrolling(false);
        input.setMaxLines(6);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setPadding(dp(10), dp(8), dp(10), dp(6));
        GradientDrawable cursor = new GradientDrawable();
        cursor.setColor(Theme.ACCENT);
        cursor.setSize(dp(2), dp(20));
        input.setTextCursorDrawable(cursor);
        input.setOnEditorActionListener((v, action, ev) -> {
            boolean enter = ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_DOWN;
            if (action == EditorInfo.IME_ACTION_SEND || enter) {
                trySend();
                return true;
            }
            return false;
        });
        input.setOnKeyListener((v, keyCode, ev) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_UP) {
                trySend();
                return true;
            }
            return keyCode == KeyEvent.KEYCODE_ENTER;
        });
        input.setOnFocusChangeListener((v, has) -> {
            bg.setStroke(Math.max(1, dp(1)), has ? 0x52FFFFFF : Theme.HAIRLINE_STRONG);
            if (has) host.onInputFocus();
        });
        input.setOnClickListener(v -> host.onInputFocus());
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c2) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c2) {
                updateSend();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        addView(input, Theme.lp(Theme.MATCH, Theme.WRAP));

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(circle(R.drawable.ic_plus, () -> host.onAttach()));
        thinkIcon = Theme.icon(c, R.drawable.ic_bulb, Theme.TEXT, 17);
        thinkBtn = toggle(thinkIcon, "Extended thinking", () -> {
            boolean on = !Prefs.thinking(getContext());
            Prefs.setThinking(getContext(), on);
            refresh();
            flashHint(on ? "Thinking on" : "Thinking off");
        });
        LayoutParams tp = new LayoutParams(dp(34), dp(34));
        tp.leftMargin = dp(6);
        row.addView(thinkBtn, tp);

        // model name takes whatever room is left and shrinks, so send always fits
        modelChip = Theme.oneLine(Theme.text(c, "", Theme.Type.LABEL));
        modelChip.setTextColor(Theme.TEXT_2);
        modelChip.setTypeface(Fonts.weight(c, 500));
        modelChip.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        modelChip.setCompoundDrawablePadding(dp(2));
        modelChip.setPadding(dp(6), 0, dp(4), 0);
        modelChip.setBackground(Theme.ripple(Theme.rounded(0x00000000, dp(14)), dp(14)));
        modelChip.setOnClickListener(v -> {
            Theme.tap(v);
            host.onModel();
        });
        LayoutParams mp = new LayoutParams(0, dp(34), 1);
        mp.leftMargin = dp(4);
        row.addView(modelChip, mp);

        send = new FrameLayout(c);
        sendIcon = Theme.icon(c, R.drawable.ic_arrow_up, Theme.TEXT_3, 19);
        send.addView(sendIcon);
        send.setBackground(Theme.rounded(Theme.FILL, dp(18)));
        send.setOnClickListener(v -> trySend());
        Theme.pressable(send, 0.86f);
        LayoutParams sendLp = new LayoutParams(dp(36), dp(36));
        sendLp.leftMargin = dp(6);
        row.addView(send, sendLp);
        addView(row, Theme.lp(Theme.MATCH, Theme.WRAP, dp(2)));

        refresh();
    }

    private View circle(int icon, Runnable action) {
        FrameLayout b = new FrameLayout(getContext());
        b.setBackground(Theme.ripple(Theme.rounded(0x00000000, dp(17), Theme.HAIRLINE_STRONG, Math.max(1, dp(1))), dp(17)));
        b.addView(Theme.icon(getContext(), icon, Theme.TEXT, 18));
        b.setClickable(true);
        b.setOnClickListener(v -> {
            Theme.tap(v);
            action.run();
        });
        Theme.pressable(b, 0.88f);
        b.setLayoutParams(new LayoutParams(dp(34), dp(34)));
        return b;
    }

    private FrameLayout toggle(ImageView icon, String name, Runnable action) {
        FrameLayout b = new FrameLayout(getContext());
        b.addView(icon);
        b.setContentDescription(name);
        b.setTooltipText(name);
        b.setClickable(true);
        b.setOnClickListener(v -> {
            Theme.tick(v);
            action.run();
        });
        Theme.pressable(b, 0.88f);
        return b;
    }

    private void styleToggle(FrameLayout b, ImageView icon, boolean on) {
        b.setBackground(on ? Theme.rounded(Theme.ACCENT, dp(17))
                : Theme.rounded(0x00000000, dp(17), Theme.HAIRLINE_STRONG, Math.max(1, dp(1))));
        icon.setImageTintList(ColorStateList.valueOf(on ? Theme.BLACK : Theme.TEXT_2));
    }

    private final Runnable restoreHint = this::restoreHintNow;

    private void restoreHintNow() {
        input.setHint("Message " + Prefs.provider(getContext()).label);
    }

    /** Brief confirmation in the empty text box after a toggle. */
    private void flashHint(String text) {
        removeCallbacks(restoreHint);
        input.setHint(text);
        postDelayed(restoreHint, 1600);
    }

    /** Re-read the selected AI, model and toggles. */
    void refresh() {
        Context c = getContext();
        input.setHint("Message " + Prefs.provider(c).label);
        styleToggle(thinkBtn, thinkIcon, Prefs.thinking(c));
        modelChip.setText(Provider.modelLabel(Prefs.model(c)));
        android.graphics.drawable.Drawable d = c.getDrawable(R.drawable.ic_chevron_down);
        if (d != null) {
            d = d.mutate();
            d.setTint(Theme.TEXT_2);
            d.setBounds(0, 0, dp(14), dp(14));
            modelChip.setCompoundDrawables(null, null, d, null);
        }
    }

    EditText input() {
        return input;
    }

    String text() {
        return input.getText().toString();
    }

    void setText(String s) {
        input.setText(s);
        input.setSelection(input.getText().length());
    }

    List<String> images() {
        return new ArrayList<>(images);
    }

    void setImages(List<String> list) {
        images.clear();
        images.addAll(list);
        renderAttachments();
    }

    void addImage(String name) {
        if (!images.contains(name)) images.add(name);
        renderAttachments();
    }

    void clear() {
        input.setText("");
        images.clear();
        renderAttachments();
    }

    void setBusy(boolean b) {
        busy = b;
        updateSend();
    }

    private void trySend() {
        String t = input.getText().toString().trim();
        if (busy) return;
        if (t.isEmpty() && images.isEmpty()) {
            Theme.shake(this);
            return;
        }
        Theme.tap(send);
        host.onSend(t, new ArrayList<>(images));
    }

    private void updateSend() {
        boolean on = !busy && (input.getText().toString().trim().length() > 0 || !images.isEmpty());
        if (on == sendOn) return;
        sendOn = on;
        send.setBackground(on ? Theme.accentFill(dp(18)) : Theme.rounded(Theme.FILL, dp(18)));
        sendIcon.setImageTintList(ColorStateList.valueOf(on ? Theme.BLACK : Theme.TEXT_3));
        send.setScaleX(0.8f);
        send.setScaleY(0.8f);
        send.animate().scaleX(1f).scaleY(1f).setDuration(320)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2.4f)).start();
    }

    private void renderAttachments() {
        attachRow.removeAllViews();
        for (String name : new ArrayList<>(images)) {
            FrameLayout f = new FrameLayout(getContext());
            ImageView iv = new ImageView(getContext());
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap b = thumb(getContext(), name, 200);
            if (b != null) iv.setImageBitmap(b);
            f.addView(iv, new FrameLayout.LayoutParams(Theme.MATCH, Theme.MATCH));
            f.setBackground(Theme.rounded(Theme.SURFACE_3, dp(14), Theme.HAIRLINE_STRONG, Math.max(1, dp(1))));
            Theme.elevate(f, 0, 14);
            FrameLayout x = new FrameLayout(getContext());
            x.setBackground(Theme.rounded(0xE6000000, dp(10)));
            x.addView(Theme.icon(getContext(), R.drawable.ic_x, Theme.ACCENT, 11));
            x.setOnClickListener(v -> {
                Theme.tick(v);
                images.remove(name);
                renderAttachments();
            });
            FrameLayout.LayoutParams xp = new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.TOP | Gravity.END);
            xp.topMargin = dp(4);
            xp.rightMargin = dp(4);
            f.addView(x, xp);
            LayoutParams lp = new LayoutParams(dp(64), dp(64));
            lp.rightMargin = dp(8);
            attachRow.addView(f, lp);
        }
        attachScroll.setVisibility(images.isEmpty() ? GONE : VISIBLE);
        updateSend();
    }

    static Bitmap thumb(Context c, String name, int maxPx) {
        try {
            String path = Chats.imageFile(c, name).getPath();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            int s = 1;
            while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= maxPx) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            return BitmapFactory.decodeFile(path, o2);
        } catch (Exception e) {
            return null;
        }
    }

    private int dp(float v) {
        return Theme.dp(getContext(), v);
    }
}
