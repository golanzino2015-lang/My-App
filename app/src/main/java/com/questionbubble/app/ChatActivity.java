package com.questionbubble.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Saved chats with the selected AI, shown as a tall sheet over the current app. */
public class ChatActivity extends Activity {

    static final String EXTRA_CHAT = "chat_id";
    static final String EXTRA_ATTACH = "attach";
    static final String EXTRA_NEW = "new_chat";
    private static final int PICK_IMAGE = 41;

    /** Survives the round trip to the screen capture. */
    static String draftText = "";
    static final List<String> draftImages = new ArrayList<>();

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private Chats.Chat chat;
    private View scrim;
    private LinearLayout panel;
    private TextView titleView, subtitleView;
    private ScrollView scroll;
    private LinearLayout list;
    private HorizontalScrollView attachScroll;
    private LinearLayout attachRow;
    private EditText input;
    private FrameLayout send;
    private ImageView sendIcon;
    private View typingRow, errorRow;
    private boolean busy, closing, sendEnabled;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        Fonts.init(this);

        FrameLayout root = new FrameLayout(this);
        scrim = new View(this);
        scrim.setBackgroundColor(Theme.SCRIM);
        scrim.setAlpha(0f);
        scrim.setOnClickListener(v -> close(null));
        root.addView(scrim, new FrameLayout.LayoutParams(Theme.MATCH, Theme.MATCH));

        FrameLayout holder = new FrameLayout(this);
        holder.setPadding(0, dp(10), 0, 0);
        Theme.insets(holder, true, false, false);
        root.addView(holder, new FrameLayout.LayoutParams(Theme.MATCH, Theme.MATCH));

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);
        float r = dp(30);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.SURFACE_2);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        bg.setStroke(Math.max(1, dp(0.8f)), Theme.HAIRLINE);
        panel.setBackground(bg);
        holder.addView(panel, new FrameLayout.LayoutParams(Theme.MATCH, Theme.MATCH));
        Theme.insets(panel, false, true, true);

        buildHeader();

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(18), dp(8), dp(18), dp(16));
        scroll.addView(list);
        panel.addView(scroll, new LinearLayout.LayoutParams(Theme.MATCH, 0, 1));

        attachScroll = new HorizontalScrollView(this);
        attachScroll.setHorizontalScrollBarEnabled(false);
        attachRow = new LinearLayout(this);
        attachRow.setOrientation(LinearLayout.HORIZONTAL);
        attachRow.setPadding(dp(16), dp(4), dp(16), dp(6));
        attachScroll.addView(attachRow);
        attachScroll.setVisibility(View.GONE);
        panel.addView(attachScroll, Theme.lp(Theme.MATCH, Theme.WRAP));

        buildInputBar();
        setContentView(root);

        panel.setVisibility(View.INVISIBLE);
        panel.post(() -> {
            panel.setTranslationY(panel.getHeight());
            panel.setVisibility(View.VISIBLE);
            panel.animate().translationY(0).setDuration(540).setInterpolator(Theme.DECELERATE).start();
            scrim.animate().alpha(1f).setDuration(320).start();
        });

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        closing = false;
        handleIntent(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (input != null) draftText = input.getText().toString();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        close(null);
    }

    private void handleIntent(Intent i) {
        String id = i.getStringExtra(EXTRA_CHAT);
        if (i.getBooleanExtra(EXTRA_NEW, false)) {
            chat = Chats.create();
            draftText = "";
            draftImages.clear();
        } else if (id != null) {
            chat = Chats.load(this, id);
            if (chat == null) chat = Chats.create();
        } else if (chat == null) {
            List<Chats.Chat> all = Chats.list(this);
            chat = all.isEmpty() ? Chats.create() : all.get(0);
        }
        String attach = i.getStringExtra(EXTRA_ATTACH);
        if (attach != null && !draftImages.contains(attach)) draftImages.add(attach);
        input.setText(draftText);
        input.setSelection(input.getText().length());
        renderAll();
        renderAttachments();
        updateHeader();
        if (attach != null) input.requestFocus();
    }

    // ---------------------------------------------------------------- layout

    private void buildHeader() {
        View handle = new View(this);
        handle.setBackground(Theme.rounded(0x40FFFFFF, dp(3)));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(38), dp(5));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.topMargin = dp(10);
        panel.addView(handle, hp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.addView(Rows.iconButton(this, R.drawable.ic_menu, this::showChats));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_HORIZONTAL);
        titleView = Theme.oneLine(Theme.text(this, "New chat", Theme.Type.TITLE_S));
        titleView.setGravity(Gravity.CENTER);
        subtitleView = Theme.oneLine(Theme.text(this, "", Theme.Type.LABEL));
        subtitleView.setGravity(Gravity.CENTER);
        titles.addView(titleView, Theme.lp(Theme.MATCH, Theme.WRAP));
        titles.addView(subtitleView, Theme.lp(Theme.MATCH, Theme.WRAP, dp(3)));
        titles.setPadding(dp(8), 0, dp(8), 0);
        titles.setClickable(true);
        titles.setOnClickListener(v -> {
            Theme.tap(v);
            Sheets.provider(this, this::updateHeader);
        });
        row.addView(titles, new LinearLayout.LayoutParams(0, Theme.WRAP, 1));

        View newChat = Rows.iconButton(this, R.drawable.ic_edit, this::newChat);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(dp(42), dp(42));
        np.rightMargin = dp(8);
        row.addView(newChat, np);
        row.addView(Rows.iconButton(this, R.drawable.ic_x, () -> close(null)));
        panel.addView(row, Theme.lp(Theme.MATCH, Theme.WRAP));

        View div = new View(this);
        div.setBackgroundColor(Theme.HAIRLINE);
        panel.addView(div, Theme.lp(Theme.MATCH, Math.max(1, dp(0.8f))));
    }

    private void buildInputBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.BOTTOM);
        bar.setPadding(dp(12), dp(8), dp(12), dp(12));

        bar.addView(roundButton(R.drawable.ic_scan, this::capture));
        View gallery = roundButton(R.drawable.ic_image, this::pickImage);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(46), dp(46));
        gp.leftMargin = dp(8);
        gp.rightMargin = dp(8);
        bar.addView(gallery, gp);

        FrameLayout field = new FrameLayout(this);
        GradientDrawable fbg = Theme.rounded(Theme.SURFACE_3, dp(23), Theme.HAIRLINE_STRONG, Math.max(1, dp(1)));
        field.setBackground(fbg);
        input = new EditText(this);
        input.setBackground(null);
        input.setHint("Message");
        input.setHintTextColor(Theme.TEXT_3);
        input.setTextColor(Theme.TEXT);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        input.setTypeface(Fonts.weight(this, 400));
        input.setMaxLines(6);
        input.setMinHeight(dp(46));
        input.setPadding(dp(16), dp(11), dp(52), dp(11));
        GradientDrawable cursor = new GradientDrawable();
        cursor.setColor(Theme.WHITE);
        cursor.setSize(dp(2), dp(20));
        input.setTextCursorDrawable(cursor);
        input.setOnFocusChangeListener((v, has) -> fbg.setStroke(Math.max(1, dp(1)), has ? 0x66FFFFFF : Theme.HAIRLINE_STRONG));
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                updateSend();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        field.addView(input, new FrameLayout.LayoutParams(Theme.MATCH, Theme.WRAP));

        send = new FrameLayout(this);
        sendIcon = Theme.icon(this, R.drawable.ic_arrow_up, Theme.TEXT_3, 19);
        send.addView(sendIcon);
        send.setBackground(Theme.rounded(Theme.FILL, dp(18)));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.END | Gravity.BOTTOM);
        sp.rightMargin = dp(5);
        sp.bottomMargin = dp(5);
        field.addView(send, sp);
        send.setOnClickListener(v -> send());
        Theme.pressable(send, 0.86f);

        bar.addView(field, new LinearLayout.LayoutParams(0, Theme.WRAP, 1));
        panel.addView(bar, Theme.lp(Theme.MATCH, Theme.WRAP));
    }

    private View roundButton(int icon, Runnable action) {
        FrameLayout b = new FrameLayout(this);
        b.setBackground(Theme.ripple(Theme.rounded(Theme.FILL, dp(23), Theme.HAIRLINE, Math.max(1, dp(0.8f))), dp(23)));
        b.addView(Theme.icon(this, icon, Theme.TEXT, 20));
        b.setClickable(true);
        b.setOnClickListener(v -> {
            Theme.tap(v);
            action.run();
        });
        Theme.pressable(b, 0.88f);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(46), dp(46)));
        return b;
    }

    private void updateHeader() {
        titleView.setText(chat == null || chat.title.isEmpty() ? "New chat" : chat.title);
        subtitleView.setText(Prefs.provider(this).label + " · " + Prefs.model(this));
        if (chat != null && chat.messages.isEmpty()) renderAll();
    }

    private void updateSend() {
        boolean on = !busy && (input.getText().toString().trim().length() > 0 || !draftImages.isEmpty());
        if (sendEnabled == on) return;
        sendEnabled = on;
        send.setBackground(Theme.rounded(on ? Theme.WHITE : Theme.FILL, dp(18)));
        sendIcon.setImageTintList(ColorStateList.valueOf(on ? Theme.BLACK : Theme.TEXT_3));
        send.setScaleX(0.8f);
        send.setScaleY(0.8f);
        send.animate().scaleX(1f).scaleY(1f).setDuration(320)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2.4f)).start();
    }

    // ---------------------------------------------------------------- messages

    private void renderAll() {
        list.removeAllViews();
        typingRow = null;
        errorRow = null;
        if (chat.messages.isEmpty()) {
            list.addView(emptyState(), Theme.lp(Theme.MATCH, Theme.MATCH));
            return;
        }
        for (Chats.Msg m : chat.messages) list.addView(bubble(m), rowLp());
        scrollToEnd(false);
    }

    private LinearLayout.LayoutParams rowLp() {
        return Theme.lp(Theme.MATCH, Theme.WRAP, dp(14));
    }

    private View emptyState() {
        LinearLayout e = new LinearLayout(this);
        e.setOrientation(LinearLayout.VERTICAL);
        e.setGravity(Gravity.CENTER);
        e.setPadding(dp(8), dp(24), dp(8), dp(24));
        OrbView orb = new OrbView(this, 14);
        orb.setDrawShadow(false);
        orb.setGlow(0.3f);
        e.addView(orb, new LinearLayout.LayoutParams(dp(108), dp(108)));
        TextView t = Theme.text(this, "Chat with " + Prefs.provider(this).label, Theme.Type.TITLE);
        t.setGravity(Gravity.CENTER);
        e.addView(t, Theme.lp(Theme.WRAP, Theme.WRAP, dp(16)));
        TextView s = Theme.text(this, "Chats save automatically. Tap the scan button to send part of your screen.", Theme.Type.CAPTION);
        s.setGravity(Gravity.CENTER);
        e.addView(s, Theme.lp(Theme.WRAP, Theme.WRAP, dp(8)));
        e.addView(suggestion(R.drawable.ic_scan, "Ask about my screen", this::capture), Theme.lp(Theme.MATCH, Theme.WRAP, dp(22)));
        e.addView(suggestion(R.drawable.ic_edit, "Help me write a reply", () -> prefill("Help me write a reply to: ")), Theme.lp(Theme.MATCH, Theme.WRAP, dp(8)));
        e.addView(suggestion(R.drawable.ic_sparkles, "Explain something simply", () -> prefill("Explain this simply: ")), Theme.lp(Theme.MATCH, Theme.WRAP, dp(8)));
        for (int i = 0; i < e.getChildCount(); i++) Theme.enter(e.getChildAt(i), 60L * i);
        return e;
    }

    private View suggestion(int icon, String text, Runnable action) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), dp(14), dp(16), dp(14));
        r.setBackground(Theme.ripple(Theme.rounded(Theme.FILL, dp(18), Theme.HAIRLINE, Math.max(1, dp(0.8f))), dp(18)));
        FrameLayout ic = new FrameLayout(this);
        ic.addView(Theme.icon(this, icon, Theme.TEXT_2, 18));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(20), dp(20));
        ip.rightMargin = dp(12);
        r.addView(ic, ip);
        r.addView(Theme.text(this, text, Theme.Type.CALLOUT));
        r.setClickable(true);
        r.setOnClickListener(v -> {
            Theme.tap(v);
            action.run();
        });
        Theme.pressable(r, 0.97f);
        return r;
    }

    private void prefill(String s) {
        input.setText(s);
        input.setSelection(s.length());
        input.requestFocus();
        android.view.inputmethod.InputMethodManager imm = getSystemService(android.view.inputmethod.InputMethodManager.class);
        if (imm != null) imm.showSoftInput(input, 0);
    }

    private View bubble(Chats.Msg m) {
        if (m.isUser()) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.END);
            int maxW = Math.round(getResources().getDisplayMetrics().widthPixels * 0.76f);
            for (String img : m.images) {
                ImageView iv = new ImageView(this);
                iv.setAdjustViewBounds(true);
                iv.setMaxWidth(dp(230));
                iv.setMaxHeight(dp(300));
                iv.setScaleType(ImageView.ScaleType.FIT_END);
                Bitmap b = thumb(img, 700);
                if (b != null) iv.setImageBitmap(b);
                Theme.elevate(iv, 0, 18);
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Theme.WRAP, Theme.WRAP);
                ip.bottomMargin = dp(6);
                col.addView(iv, ip);
            }
            if (!m.text.isEmpty()) {
                TextView t = Theme.text(this, m.text, Theme.Type.BODY_M);
                t.setTextColor(Theme.BLACK);
                t.setTypeface(Fonts.weight(this, 500));
                t.setLineSpacing(dp(3), 1f);
                t.setMaxWidth(maxW);
                t.setPadding(dp(15), dp(11), dp(15), dp(11));
                float R = dp(20), s = dp(6);
                GradientDrawable g = new GradientDrawable();
                g.setColor(Theme.WHITE);
                g.setCornerRadii(new float[]{R, R, R, R, s, s, R, R});
                t.setBackground(g);
                t.setOnLongClickListener(v -> {
                    copy(m.text);
                    return true;
                });
                col.addView(t);
            }
            return col;
        }
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(aiLabel(m.provider));
        TextView t = Theme.text(this, Md.render(this, m.text), Theme.Type.BODY_M);
        t.setTextColor(Theme.TEXT);
        t.setTypeface(Fonts.weight(this, 400));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        t.setLineSpacing(dp(5), 1f);
        t.setTextIsSelectable(true);
        col.addView(t, Theme.lp(Theme.MATCH, Theme.WRAP, dp(8)));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView copy = Theme.text(this, "Copy", Theme.Type.LABEL);
        copy.setCompoundDrawablePadding(dp(6));
        android.graphics.drawable.Drawable ic = getDrawable(R.drawable.ic_copy);
        if (ic != null) {
            ic = ic.mutate();
            ic.setTint(Theme.TEXT_2);
            ic.setBounds(0, 0, dp(14), dp(14));
            copy.setCompoundDrawables(ic, null, null, null);
        }
        copy.setPadding(dp(10), dp(6), dp(12), dp(6));
        copy.setBackground(Theme.ripple(Theme.rounded(Theme.FILL, dp(14)), dp(14)));
        copy.setOnClickListener(v -> {
            Theme.tap(v);
            copy(m.text);
        });
        actions.addView(copy);
        col.addView(actions, Theme.lp(Theme.WRAP, Theme.WRAP, dp(10)));
        return col;
    }

    private View aiLabel(String providerId) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColors(new int[]{0xFF8A8A8A, 0xFF1A1A1A});
        d.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        d.setGradientRadius(dp(9));
        d.setStroke(Math.max(1, dp(0.8f)), 0x55FFFFFF);
        dot.setBackground(d);
        LinearLayout.LayoutParams dp14 = new LinearLayout.LayoutParams(dp(16), dp(16));
        dp14.rightMargin = dp(8);
        row.addView(dot, dp14);
        String label = providerId == null || providerId.isEmpty() ? Prefs.provider(this).label : Provider.fromId(providerId).label;
        TextView t = Theme.text(this, label, Theme.Type.LABEL);
        t.setTypeface(Fonts.weight(this, 600));
        row.addView(t);
        return row;
    }

    private Bitmap thumb(String name, int maxPx) {
        try {
            String path = Chats.imageFile(this, name).getPath();
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

    private void scrollToEnd(boolean smooth) {
        scroll.post(() -> {
            if (smooth) scroll.smoothScrollTo(0, list.getHeight());
            else scroll.scrollTo(0, list.getHeight());
        });
    }

    private void animateIn(View v) {
        v.setAlpha(0f);
        v.setTranslationY(dp(14));
        v.animate().alpha(1f).translationY(0).setDuration(420).setInterpolator(Theme.DECELERATE).start();
    }

    // ---------------------------------------------------------------- sending

    private void send() {
        if (busy) return;
        String text = input.getText().toString().trim();
        if (text.isEmpty() && draftImages.isEmpty()) {
            Theme.shake(input);
            return;
        }
        Theme.tap(send);
        if (errorRow != null) {
            list.removeView(errorRow);
            errorRow = null;
        }
        List<String> imgs = new ArrayList<>(draftImages);
        Chats.Msg user = new Chats.Msg("user", text, imgs, "", "", null, System.currentTimeMillis());
        if (chat.messages.isEmpty()) list.removeAllViews();
        chat.messages.add(user);
        if (chat.title.isEmpty()) {
            String t = text.isEmpty() ? "Screenshot" : MainActivity.firstLine(text);
            chat.title = t.length() > 48 ? t.substring(0, 47) + "…" : t;
        }
        chat.updated = System.currentTimeMillis();
        Chats.save(this, chat);
        updateHeader();

        String typedText = input.getText().toString();
        input.setText("");
        draftImages.clear();
        draftText = "";
        renderAttachments();

        View userView = bubble(user);
        list.addView(userView, rowLp());
        animateIn(userView);

        LinearLayout typing = new LinearLayout(this);
        typing.setOrientation(LinearLayout.VERTICAL);
        typing.addView(aiLabel(null));
        typing.addView(new TypingDots(this), Theme.lp(Theme.WRAP, Theme.WRAP, dp(12)));
        typingRow = typing;
        list.addView(typing, rowLp());
        animateIn(typing);
        scrollToEnd(true);

        busy = true;
        updateSend();
        worker.execute(() -> {
            Chats.Msg reply = null;
            String error = null;
            try {
                reply = ChatApi.reply(this, chat);
            } catch (Exception e) {
                error = String.valueOf(e.getMessage());
            }
            Chats.Msg r = reply;
            String err = error;
            main.post(() -> {
                if (isDestroyed()) return;
                busy = false;
                if (typingRow != null) list.removeView(typingRow);
                typingRow = null;
                if (r != null) {
                    chat.messages.add(r);
                    chat.updated = System.currentTimeMillis();
                    Chats.save(this, chat);
                    View v = bubble(r);
                    list.addView(v, rowLp());
                    animateIn(v);
                    Theme.confirm(v);
                } else {
                    // Keep the history append-only: drop the unanswered message, give it back to edit.
                    chat.messages.remove(user);
                    Chats.save(this, chat);
                    list.removeView(userView);
                    input.setText(typedText);
                    input.setSelection(input.getText().length());
                    draftImages.addAll(imgs);
                    renderAttachments();
                    showError(err);
                    if (chat.messages.isEmpty()) renderAll();
                }
                updateSend();
                scrollToEnd(true);
            });
        });
    }

    private void showError(String msg) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(Theme.rounded(0x14FF8A80, dp(16), 0x33FF8A80, Math.max(1, dp(0.8f))));
        FrameLayout ic = new FrameLayout(this);
        ic.addView(Theme.icon(this, R.drawable.ic_alert, 0xFFFF8A80, 18));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(20), dp(20));
        ip.rightMargin = dp(12);
        row.addView(ic, ip);
        TextView t = Theme.text(this, msg + " Your message is back in the box.", Theme.Type.CAPTION);
        t.setTextColor(Theme.TEXT);
        row.addView(t, new LinearLayout.LayoutParams(0, Theme.WRAP, 1));
        errorRow = row;
        list.addView(row, rowLp());
        animateIn(row);
        Theme.shake(row);
        Theme.reject(row);
    }

    private void copy(String text) {
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        cm.setPrimaryClip(ClipData.newPlainText("answer", text));
        Snack.show(this, "Copied", R.drawable.ic_check);
    }

    // ---------------------------------------------------------------- attachments

    private void renderAttachments() {
        attachRow.removeAllViews();
        for (String name : new ArrayList<>(draftImages)) {
            FrameLayout f = new FrameLayout(this);
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap b = thumb(name, 240);
            if (b != null) iv.setImageBitmap(b);
            f.addView(iv, new FrameLayout.LayoutParams(Theme.MATCH, Theme.MATCH));
            Theme.elevate(f, 0, 14);
            f.setBackground(Theme.rounded(Theme.SURFACE_3, dp(14), Theme.HAIRLINE_STRONG, Math.max(1, dp(1))));

            FrameLayout x = new FrameLayout(this);
            x.setBackground(Theme.rounded(0xE6000000, dp(11)));
            x.addView(Theme.icon(this, R.drawable.ic_x, Theme.WHITE, 12));
            x.setOnClickListener(v -> {
                Theme.tick(v);
                draftImages.remove(name);
                renderAttachments();
            });
            FrameLayout.LayoutParams xp = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP | Gravity.END);
            xp.topMargin = dp(4);
            xp.rightMargin = dp(4);
            f.addView(x, xp);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(72), dp(72));
            lp.rightMargin = dp(8);
            attachRow.addView(f, lp);
        }
        boolean show = !draftImages.isEmpty();
        if (show && attachScroll.getVisibility() != View.VISIBLE) {
            attachScroll.setVisibility(View.VISIBLE);
            animateIn(attachScroll);
        } else if (!show) {
            attachScroll.setVisibility(View.GONE);
        }
        updateSend();
    }

    /** Hide the chat, let the user crop part of the screen, then come back with it attached. */
    private void capture() {
        if (busy) return;
        BubbleService svc = BubbleService.instance;
        if (svc == null) {
            Snack.show(this, "Turn on screen access in Question Bubble first", R.drawable.ic_alert);
            return;
        }
        draftText = input.getText().toString();
        if (!chat.messages.isEmpty()) Chats.save(this, chat);
        String id = chat.id;
        close(() -> svc.captureForChat(id));
    }

    @SuppressWarnings("deprecation")
    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(i, PICK_IMAGE);
        } catch (Exception e) {
            Snack.show(this, "No photo picker found", R.drawable.ic_alert);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        worker.execute(() -> {
            String name = null;
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(in, null, o);
                }
                int s = 1;
                while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= 1600) s *= 2;
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = s;
                Bitmap b;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    b = BitmapFactory.decodeStream(in, null, o2);
                }
                if (b != null) {
                    name = Chats.saveImage(this, b);
                    b.recycle();
                }
            } catch (Exception ignored) {
            }
            String n = name;
            main.post(() -> {
                if (n == null) {
                    Snack.show(this, "Couldn't open that image", R.drawable.ic_alert);
                } else {
                    draftImages.add(n);
                    renderAttachments();
                }
            });
        });
    }

    // ---------------------------------------------------------------- chats list

    private void newChat() {
        if (busy) return;
        chat = Chats.create();
        draftText = "";
        draftImages.clear();
        input.setText("");
        renderAttachments();
        renderAll();
        updateHeader();
    }

    private void showChats() {
        Sheet s = new Sheet(this);
        s.title("Chats", null);
        PillButton fresh = new PillButton(this, "New chat", PillButton.Kind.PRIMARY, R.drawable.ic_edit, false);
        fresh.setOnClickListener(v -> {
            s.close();
            newChat();
        });
        s.add(fresh, 16);
        List<Chats.Chat> all = Chats.list(this);
        if (all.isEmpty()) {
            TextView t = Theme.text(this, "No saved chats yet.", Theme.Type.CAPTION);
            t.setGravity(Gravity.CENTER);
            s.add(t, 20);
        } else {
            ScrollView sv = new ScrollView(this);
            sv.setVerticalScrollBarEnabled(false);
            LinearLayout group = Rows.group(this);
            for (Chats.Chat c : all) {
                boolean current = chat != null && c.id.equals(chat.id);
                View trailing = null;
                if (current) {
                    FrameLayout f = new FrameLayout(this);
                    f.addView(Theme.icon(this, R.drawable.ic_check, Theme.TEXT, 18));
                    f.setLayoutParams(new LinearLayout.LayoutParams(dp(20), dp(20)));
                    trailing = f;
                }
                LinearLayout row = Rows.row(this, R.drawable.ic_message, c.title.isEmpty() ? "Chat" : c.title,
                        History.relative(c.updated) + " · " + c.preview(), trailing, () -> {
                            s.close();
                            if (busy) return;
                            chat = Chats.load(this, c.id);
                            if (chat == null) chat = Chats.create();
                            renderAll();
                            updateHeader();
                        });
                TextView sub = (TextView) ((LinearLayout) row.getChildAt(1)).getChildAt(1);
                sub.setSingleLine(true);
                sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.setOnLongClickListener(v -> {
                    Theme.tick(v);
                    s.close();
                    main.postDelayed(() -> Sheets.confirm(this, "Delete this chat?", c.title, "Delete", () -> {
                        Chats.delete(this, c.id);
                        if (chat != null && chat.id.equals(c.id)) newChat();
                        Snack.show(this, "Chat deleted", R.drawable.ic_check);
                    }), 300);
                    return true;
                });
                Rows.add(group, row);
            }
            sv.addView(group);
            int maxH = Math.round(getResources().getDisplayMetrics().heightPixels * 0.55f);
            sv.setLayoutParams(new LinearLayout.LayoutParams(Theme.MATCH, Theme.WRAP));
            FrameLayout cap = new FrameLayout(this) {
                @Override
                protected void onMeasure(int w, int h) {
                    super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST));
                }
            };
            cap.addView(sv);
            s.add(cap, 16);
            TextView hint = Theme.text(this, "Press and hold a chat to delete it.", Theme.Type.LABEL);
            hint.setTextColor(Theme.TEXT_3);
            hint.setGravity(Gravity.CENTER);
            s.add(hint, 12);
        }
        s.show();
    }

    // ---------------------------------------------------------------- closing

    private void close(Runnable after) {
        if (closing) return;
        closing = true;
        draftText = input.getText().toString();
        android.view.inputmethod.InputMethodManager imm = getSystemService(android.view.inputmethod.InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        panel.animate().translationY(panel.getHeight()).setDuration(260).setInterpolator(Theme.ACCELERATE)
                .withEndAction(() -> {
                    finish();
                    overridePendingTransition(0, 0);
                    if (after != null) after.run();
                }).start();
        scrim.animate().alpha(0f).setDuration(260).start();
    }

    private int dp(float v) {
        return Theme.dp(this, v);
    }
}
