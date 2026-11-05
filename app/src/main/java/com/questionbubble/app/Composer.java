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
    private final TextView thinkChip, searchChip, modelChip;
    private final FrameLayout send;
    private final ImageView sendIcon;
    private final GradientDrawable bg;
    private boolean busy, sendOn;

    Composer(Context c, Host host) {
        super(c);
        this.host = host;
        setOrientation(VERTICAL);
        setPadding(dp(8), dp(8), dp(8), dp(8));
        bg = Theme.rounded(0xFF181818, dp(28), Theme.HAIRLINE_STRONG, Math.max(1, dp(1)));
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
        cursor.setColor(Theme.WHITE);
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
        thinkChip = chip(R.drawable.ic_bulb, "Think");
        thinkChip.setOnClickListener(v -> {
            Theme.tick(v);
            Prefs.setThinking(getContext(), !Prefs.thinking(getContext()));
            refresh();
        });
        searchChip = chip(R.drawable.ic_globe, "Search");
        searchChip.setOnClickListener(v -> {
            Theme.tick(v);
            Prefs.setWebSearch(getContext(), !Prefs.webSearch(getContext()));
            refresh();
        });
        LayoutParams cp = new LayoutParams(Theme.WRAP, dp(34));
        cp.leftMargin = dp(6);
        row.addView(thinkChip, cp);
        LayoutParams sp2 = new LayoutParams(Theme.WRAP, dp(34));
        sp2.leftMargin = dp(6);
        row.addView(searchChip, sp2);
        View spacer = new View(c);
        row.addView(spacer, new LayoutParams(0, 1, 1));

        modelChip = Theme.oneLine(Theme.text(c, "", Theme.Type.LABEL));
        modelChip.setTextColor(Theme.TEXT_2);
        modelChip.setTypeface(Fonts.weight(c, 500));
        modelChip.setGravity(Gravity.CENTER_VERTICAL);
        modelChip.setMaxWidth(dp(130));
        modelChip.setCompoundDrawablePadding(dp(2));
        modelChip.setPadding(dp(8), 0, dp(4), 0);
        modelChip.setBackground(Theme.ripple(Theme.rounded(0x00000000, dp(14)), dp(14)));
        modelChip.setOnClickListener(v -> {
            Theme.tap(v);
            host.onModel();
        });
        row.addView(modelChip, new LayoutParams(Theme.WRAP, dp(34)));

        send = new FrameLayout(c);
        sendIcon = Theme.icon(c, R.drawable.ic_arrow_up, Theme.TEXT_3, 18);
        send.addView(sendIcon);
        send.setBackground(Theme.rounded(Theme.FILL, dp(17)));
        send.setOnClickListener(v -> trySend());
        Theme.pressable(send, 0.86f);
        LayoutParams sendLp = new LayoutParams(dp(34), dp(34));
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

    private TextView chip(int icon, String label) {
        TextView t = Theme.text(getContext(), label, Theme.Type.LABEL);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setTypeface(Fonts.weight(getContext(), 500));
        t.setCompoundDrawablePadding(dp(5));
        t.setPadding(dp(10), 0, dp(12), 0);
        t.setTag(icon);
        Theme.pressable(t, 0.92f);
        return t;
    }

    private void styleChip(TextView t, boolean on) {
        int fg = on ? Theme.BLACK : Theme.TEXT_2;
        t.setTextColor(fg);
        t.setBackground(on ? Theme.rounded(Theme.WHITE, dp(17))
                : Theme.rounded(0x00000000, dp(17), Theme.HAIRLINE_STRONG, Math.max(1, dp(1))));
        android.graphics.drawable.Drawable d = getContext().getDrawable((int) t.getTag());
        if (d != null) {
            d = d.mutate();
            d.setTint(fg);
            d.setBounds(0, 0, dp(15), dp(15));
            t.setCompoundDrawables(d, null, null, null);
        }
    }

    /** Re-read the selected AI, model and toggles. */
    void refresh() {
        Context c = getContext();
        input.setHint("Message " + Prefs.provider(c).label);
        styleChip(thinkChip, Prefs.thinking(c));
        styleChip(searchChip, Prefs.webSearch(c));
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
        send.setBackground(Theme.rounded(on ? Theme.WHITE : Theme.FILL, dp(17)));
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
            x.addView(Theme.icon(getContext(), R.drawable.ic_x, Theme.WHITE, 11));
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
