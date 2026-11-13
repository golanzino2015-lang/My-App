package com.questionbubble.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Full-screen chat in the style of a modern AI chat app. Chats save themselves. */
public class ChatActivity extends Activity {

    static final String EXTRA_CHAT = "chat_id";
    static final String EXTRA_ATTACH = "attach";
    static final String EXTRA_NEW = "new_chat";
    static final String EXTRA_AUTOSEND = "autosend";
    private static final int PICK_IMAGE = 41;

    /** Composer contents that survive leaving for a screen capture (or come from the bubble). */
    static String draftText = "";
    static final List<String> draftImages = new ArrayList<>();

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private Chats.Chat chat;
    private TextView titleView, modelView;
    private ScrollView scroll;
    private LinearLayout list;
    private Composer composer;
    private View pendingRow, errorRow;
    private boolean busy;
    private long requestStart;
    private FrameLayout drawer;
    private FrameLayout jump;
    private boolean jumpShown;
    private View drawerScrim;
    private LinearLayout drawerList;
    private boolean drawerOpen;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        Fonts.init(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Theme.BG);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new FrameLayout.LayoutParams(Theme.MATCH, Theme.MATCH));
        Theme.insets(content, true, true, true);

        content.addView(topBar(), Theme.lp(Theme.MATCH, Theme.WRAP));

        FrameLayout stage = new FrameLayout(this);
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(18), dp(10), dp(18), dp(28));
        scroll.addView(list);
        stage.addView(scroll, new FrameLayout.LayoutParams(Theme.MATCH, Theme.MATCH));

        // soft fades so messages slide under the bars
        View topFade = new View(this);
        topFade.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Theme.BG, 0x00000000}));
        stage.addView(topFade, new FrameLayout.LayoutParams(Theme.MATCH, dp(18), Gravity.TOP));
        View bottomFade = new View(this);
        bottomFade.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP, new int[]{Theme.BG, 0x00000000}));
        stage.addView(bottomFade, new FrameLayout.LayoutParams(Theme.MATCH, dp(28), Gravity.BOTTOM));

        // jump to the latest message
        jump = new FrameLayout(this);
        jump.setBackground(Theme.rounded(Theme.SURFACE_3, dp(20), Theme.HAIRLINE_STRONG, Math.max(1, dp(1))));
        jump.addView(Theme.icon(this, R.drawable.ic_chevron_down, Theme.TEXT, 20));
        Theme.elevate(jump, 8, 20);
        jump.setClickable(true);
        jump.setOnClickListener(v -> {
            Theme.tap(v);
            scrollToEnd(true);
        });
        FrameLayout.LayoutParams jp = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        jp.bottomMargin = dp(10);
        stage.addView(jump, jp);
        jump.setAlpha(0f);
        jump.setVisibility(View.GONE);
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> updateJump());
        content.addView(stage, new LinearLayout.LayoutParams(Theme.MATCH, 0, 1));

        composer = new Composer(this, new Composer.Host() {
            @Override
            public void onSend(String text, List<String> images) {
                sendNew(text, images);
            }

            @Override
            public void onAttach() {
                attachMenu();
            }

            @Override
            public void onModel() {
                Sheets.modelPicker(ChatActivity.this, () -> {
                    composer.refresh();
                    updateHeader();
                });
            }
        });
        LinearLayout.LayoutParams cp = Theme.lp(Theme.MATCH, Theme.WRAP);
        cp.leftMargin = dp(10);
        cp.rightMargin = dp(10);
        cp.bottomMargin = dp(8);
        content.addView(composer, cp);
        Theme.elevate(composer, 14, 28);

        buildDrawer(root);
        setContentView(root);

        content.setAlpha(0f);
        content.setTranslationY(dp(24));
        content.animate().alpha(1f).translationY(0).setDuration(460).setInterpolator(Theme.DECELERATE).start();

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (composer != null) {
            composer.refresh();
            updateHeader();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (composer != null) {
            draftText = composer.text();
            draftImages.clear();
            draftImages.addAll(composer.images());
        }
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (drawerOpen) closeDrawer();
        else {
            finish();
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        }
    }

    private void handleIntent(Intent i) {
        String id = i.getStringExtra(EXTRA_CHAT);
        if (i.getBooleanExtra(EXTRA_NEW, false)) {
            chat = Chats.create();
        } else if (id != null) {
            chat = Chats.load(this, id);
            if (chat == null) chat = Chats.create();
        } else if (chat == null) {
            List<Chats.Chat> all = Chats.list(this);
            chat = all.isEmpty() ? Chats.create() : all.get(0);
        }
        String attach = i.getStringExtra(EXTRA_ATTACH);
        if (attach != null && !draftImages.contains(attach)) draftImages.add(attach);
        composer.setText(draftText);
        composer.setImages(draftImages);
        renderAll();
        updateHeader();
        if (i.getBooleanExtra(EXTRA_AUTOSEND, false)) {
            i.removeExtra(EXTRA_AUTOSEND);
            String t = composer.text().trim();
            List<String> imgs = composer.images();
            if (!t.isEmpty() || !imgs.isEmpty()) sendNew(t, imgs);
        } else if (attach != null) {
            composer.input().requestFocus();
        }
    }

    // ---------------------------------------------------------------- top bar

    private View topBar() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(6), dp(8), dp(6));
        row.addView(barButton(R.drawable.ic_menu, this::openDrawer));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_HORIZONTAL);
        modelView = Theme.oneLine(Theme.text(this, "", Theme.Type.CALLOUT));
        modelView.setTypeface(Fonts.weight(this, 600));
        modelView.setGravity(Gravity.CENTER_VERTICAL);
        modelView.setCompoundDrawablePadding(dp(6));
        modelView.setPadding(dp(12), 0, dp(10), 0);
        modelView.setMinHeight(dp(36));
        modelView.setBackground(Theme.ripple(Theme.rounded(Theme.FILL, dp(18), Theme.HAIRLINE, Math.max(1, dp(0.8f))), dp(18)));
        modelView.setOnClickListener(v -> {
            Theme.tap(v);
            Sheets.modelPicker(this, () -> {
                composer.refresh();
                updateHeader();
            });
        });
        Theme.pressable(modelView, 0.95f);
        center.addView(modelView, new LinearLayout.LayoutParams(Theme.WRAP, dp(36)));
        titleView = Theme.oneLine(Theme.text(this, "", Theme.Type.LABEL));
        titleView.setTextColor(Theme.TEXT_3);
        titleView.setGravity(Gravity.CENTER);
        titleView.setMaxWidth(dp(220));
        center.addView(titleView, Theme.lp(Theme.WRAP, Theme.WRAP, dp(3)));
        row.addView(center, new LinearLayout.LayoutParams(0, Theme.WRAP, 1));

        row.addView(barButton(R.drawable.ic_edit, this::newChat));
        return row;
    }

    private View barButton(int icon, Runnable action) {
        FrameLayout b = new FrameLayout(this);
        b.setBackground(Theme.ripple(Theme.rounded(0x00000000, dp(22)), dp(22)));
        b.addView(Theme.icon(this, icon, Theme.TEXT, 22));
        b.setClickable(true);
        b.setOnClickListener(v -> {
            Theme.tap(v);
            action.run();
        });
        Theme.pressable(b, 0.88f);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        return b;
    }

    private void updateHeader() {
        boolean hasTitle = chat != null && !chat.title.isEmpty();
        titleView.setText(hasTitle ? chat.title : "");
        titleView.setVisibility(hasTitle ? View.VISIBLE : View.GONE);
        modelView.setText(Provider.modelLabel(Prefs.model(this)));
        android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{Theme.ACCENT_LIGHT, Theme.ACCENT, Theme.ACCENT_DARK});
        dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dot.setBounds(0, 0, dp(10), dp(10));
        android.graphics.drawable.Drawable d = getDrawable(R.drawable.ic_chevron_down);
        if (d != null) {
            d = d.mutate();
            d.setTint(Theme.TEXT_2);
            d.setBounds(0, 0, dp(15), dp(15));
        }
        modelView.setCompoundDrawables(dot, null, d, null);
        if (chat != null && chat.messages.isEmpty() && !busy) renderAll();
    }

    private void updateJump() {
        if (jump == null || list == null) return;
        int distance = list.getHeight() - (scroll.getScrollY() + scroll.getHeight());
        boolean show = distance > dp(240);
        if (show == jumpShown) return;
        jumpShown = show;
        if (show) {
            jump.setVisibility(View.VISIBLE);
            jump.setScaleX(0.7f);
            jump.setScaleY(0.7f);
            jump.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(Theme.DECELERATE).start();
        } else {
            jump.animate().alpha(0f).scaleX(0.7f).scaleY(0.7f).setDuration(160)
                    .withEndAction(() -> jump.setVisibility(View.GONE)).start();
        }
    }

    // ---------------------------------------------------------------- messages

    private void renderAll() {
        list.removeAllViews();
        pendingRow = null;
        errorRow = null;
        if (chat.messages.isEmpty()) {
            list.addView(emptyState(), Theme.lp(Theme.MATCH, Theme.MATCH));
            return;
        }
        int lastAssistant = -1;
        for (int i = 0; i < chat.messages.size(); i++) if (!chat.messages.get(i).isUser()) lastAssistant = i;
        for (int i = 0; i < chat.messages.size(); i++) {
            Chats.Msg m = chat.messages.get(i);
            list.addView(m.isUser() ? userView(m) : assistantView(m, i == lastAssistant && i == chat.messages.size() - 1), rowLp());
        }
        scrollToEnd(false);
    }

    private LinearLayout.LayoutParams rowLp() {
        return Theme.lp(Theme.MATCH, Theme.WRAP, dp(18));
    }

    private View emptyState() {
        LinearLayout e = new LinearLayout(this);
        e.setOrientation(LinearLayout.VERTICAL);
        e.setGravity(Gravity.CENTER_VERTICAL);
        e.setPadding(dp(4), dp(12), dp(4), dp(12));
        OrbView orb = new OrbView(this, 10);
        orb.setDrawShadow(false);
        orb.setGlow(0.35f);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(dp(76), dp(76));
        op.gravity = Gravity.CENTER_HORIZONTAL;
        e.addView(orb, op);
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String greet = hour < 5 ? "Up late?" : hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
        TextView g = Theme.text(this, greet, Theme.Type.DISPLAY);
        g.setGravity(Gravity.CENTER);
        g.setTextSize(30f);
        e.addView(g, Theme.lp(Theme.MATCH, Theme.WRAP, dp(18)));
        TextView s = Theme.text(this, "How can " + Prefs.provider(this).label + " help?", Theme.Type.BODY);
        s.setGravity(Gravity.CENTER);
        e.addView(s, Theme.lp(Theme.MATCH, Theme.WRAP, dp(6)));

        LinearLayout r1 = new LinearLayout(this), r2 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        r1.addView(card(R.drawable.ic_scan, "Ask about my screen", "Capture part of it", this::capture), cardLp(false));
        r1.addView(card(R.drawable.ic_edit, "Help me write", "A reply, email or post", () -> prefill("Help me write ")), cardLp(true));
        r2.addView(card(R.drawable.ic_bulb, "Explain simply", "Any topic, step by step", () -> prefill("Explain simply: ")), cardLp(false));
        r2.addView(card(R.drawable.ic_sparkles, "Solve a problem", "Math, code or logic", () -> prefill("Help me solve: ")), cardLp(true));
        e.addView(r1, Theme.lp(Theme.MATCH, Theme.WRAP, dp(26)));
        e.addView(r2, Theme.lp(Theme.MATCH, Theme.WRAP, dp(10)));
        for (int i = 0; i < e.getChildCount(); i++) Theme.enter(e.getChildAt(i), 60L * i);
        return e;
    }

    private LinearLayout.LayoutParams cardLp(boolean right) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Theme.WRAP, 1);
        if (right) p.leftMargin = dp(10);
        return p;
    }

    private View card(int icon, String title, String sub, Runnable action) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        c.setMinimumHeight(dp(104));
        c.setBackground(Theme.ripple(Theme.rounded(Theme.SURFACE, dp(20), Theme.HAIRLINE, Math.max(1, dp(0.8f))), dp(20)));
        FrameLayout ic = new FrameLayout(this);
        ic.setBackground(Theme.rounded(Theme.FILL, dp(10)));
        ic.addView(Theme.icon(this, icon, Theme.ACCENT, 17));
        c.addView(ic, new LinearLayout.LayoutParams(dp(32), dp(32)));
        c.addView(Theme.text(this, title, Theme.Type.CALLOUT), Theme.lp(Theme.MATCH, Theme.WRAP, dp(12)));
        TextView st = Theme.text(this, sub, Theme.Type.LABEL);
        st.setTextColor(Theme.TEXT_3);
        c.addView(st, Theme.lp(Theme.MATCH, Theme.WRAP, dp(4)));
        c.setClickable(true);
        c.setOnClickListener(v -> {
            Theme.tap(v);
            action.run();
        });
        Theme.pressable(c, 0.96f);
        return c;
    }

    private void prefill(String s) {
        composer.setText(s);
        composer.input().requestFocus();
        android.view.inputmethod.InputMethodManager imm = getSystemService(android.view.inputmethod.InputMethodManager.class);
        if (imm != null) imm.showSoftInput(composer.input(), 0);
    }

    private View userView(Chats.Msg m) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.END);
        int maxW = Math.round(getResources().getDisplayMetrics().widthPixels * 0.8f);
        for (String img : m.images) {
            ImageView iv = new ImageView(this);
            iv.setAdjustViewBounds(true);
            iv.setMaxWidth(dp(230));
            iv.setMaxHeight(dp(300));
            Bitmap b = Composer.thumb(this, img, 700);
            if (b != null) iv.setImageBitmap(b);
            Theme.elevate(iv, 0, 18);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Theme.WRAP, Theme.WRAP);
            ip.bottomMargin = dp(6);
            col.addView(iv, ip);
        }
        if (!m.text.isEmpty()) {
            TextView t = Theme.text(this, m.text, Theme.Type.BODY_M);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
            t.setTypeface(Fonts.weight(this, 400));
            t.setLineSpacing(dp(4), 1f);
            t.setMaxWidth(maxW);
            t.setPadding(dp(16), dp(11), dp(16), dp(11));
            float R = dp(22), r = dp(7);
            android.graphics.drawable.GradientDrawable ub = new android.graphics.drawable.GradientDrawable();
            ub.setColor(ToggleView.blend(0xFF232323, Theme.ACCENT, Theme.CLASSIC ? 0f : 0.22f));
            ub.setCornerRadii(new float[]{R, R, R, R, r, r, R, R});
            t.setBackground(ub);
            t.setOnLongClickListener(v -> {
                Theme.tick(v);
                messageMenu(m, false);
                return true;
            });
            col.addView(t);
        }
        return col;
    }

    private View assistantView(Chats.Msg m, boolean last) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(aiHeader(m), Theme.lp(Theme.MATCH, Theme.WRAP));

        if (!m.thinking.isEmpty()) col.addView(thinkingBlock(m), Theme.lp(Theme.MATCH, Theme.WRAP, dp(10)));
        if (m.searches > 0) {
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout ic = new FrameLayout(this);
            ic.addView(Theme.icon(this, R.drawable.ic_globe, Theme.TEXT_2, 15));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(16), dp(16));
            ip.rightMargin = dp(8);
            chip.addView(ic, ip);
            chip.addView(Theme.text(this, "Searched the web · " + m.searches + (m.searches == 1 ? " search" : " searches"), Theme.Type.LABEL));
            col.addView(chip, Theme.lp(Theme.MATCH, Theme.WRAP, col.getChildCount() == 0 ? 0 : dp(10)));
        }

        TextView t = Theme.text(this, Md.render(this, m.text), Theme.Type.BODY_M);
        t.setTextColor(Theme.TEXT);
        t.setTypeface(Fonts.weight(this, 400));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        t.setLineSpacing(dp(6), 1f);
        t.setTextIsSelectable(true);
        col.addView(t, Theme.lp(Theme.MATCH, Theme.WRAP, dp(10)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.addView(smallIcon(R.drawable.ic_copy, () -> copy(m.text)));
        View share = smallIcon(R.drawable.ic_external, () -> share(m.text));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(32), dp(32));
        sp.leftMargin = dp(2);
        actions.addView(share, sp);
        if (last) {
            View retry = smallIcon(R.drawable.ic_replay, this::retryLast);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(32), dp(32));
            rp.leftMargin = dp(2);
            actions.addView(retry, rp);
        }
        col.addView(actions, Theme.lp(Theme.MATCH, Theme.WRAP, dp(8)));
        col.setOnLongClickListener(v -> {
            Theme.tick(v);
            messageMenu(m, last);
            return true;
        });
        return col;
    }

    private View aiHeader(Chats.Msg m) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR, Theme.ORB);
        d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        d.setStroke(Math.max(1, dp(0.8f)), 0x40FFFFFF);
        dot.setBackground(d);
        LinearLayout.LayoutParams dp20 = new LinearLayout.LayoutParams(dp(20), dp(20));
        dp20.rightMargin = dp(8);
        row.addView(dot, dp20);
        Provider p = m.provider == null || m.provider.isEmpty() ? Prefs.provider(this) : Provider.fromId(m.provider);
        TextView name = Theme.text(this, p.label, Theme.Type.CALLOUT);
        name.setTypeface(Fonts.weight(this, 600));
        row.addView(name);
        if (m.model != null && !m.model.isEmpty()) {
            TextView model = Theme.oneLine(Theme.text(this, "  " + Provider.modelLabel(m.model), Theme.Type.LABEL));
            model.setTextColor(Theme.TEXT_3);
            row.addView(model);
        }
        return row;
    }

    /** Press-and-hold on a message: copy, share, retry. */
    private void messageMenu(Chats.Msg m, boolean canRetry) {
        Sheet s = new Sheet(this);
        String preview = MainActivity.firstLine(m.text);
        s.title(m.isUser() ? "Your message" : "Reply", preview.length() > 90 ? preview.substring(0, 89) + "\u2026" : preview);
        LinearLayout g = Rows.group(this);
        Rows.add(g, Rows.row(this, R.drawable.ic_copy, "Copy", null, null, () -> {
            s.close();
            copy(m.text);
        }));
        Rows.add(g, Rows.row(this, R.drawable.ic_external, "Share", null, null, () -> {
            s.close();
            share(m.text);
        }));
        if (m.isUser()) {
            Rows.add(g, Rows.row(this, R.drawable.ic_edit, "Edit in message box", null, null, () -> {
                s.close();
                prefill(m.text);
            }));
        } else if (canRetry) {
            Rows.add(g, Rows.row(this, R.drawable.ic_replay, "Try again", null, null, () -> {
                s.close();
                retryLast();
            }));
        }
        s.add(g, 16);
        s.show();
    }

    private void share(String text) {
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(i, "Share"));
        } catch (Exception ignored) {
        }
    }

    private View thinkingBlock(Chats.Msg m) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(12), dp(9), dp(10), dp(9));
        head.setBackground(Theme.ripple(Theme.rounded(Theme.SURFACE, dp(14), Theme.HAIRLINE, Math.max(1, dp(0.8f))), dp(14)));
        FrameLayout ic = new FrameLayout(this);
        ic.addView(Theme.icon(this, R.drawable.ic_bulb, Theme.TEXT_2, 15));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(16), dp(16));
        ip.rightMargin = dp(8);
        head.addView(ic, ip);
        String label = m.seconds > 0 ? "Thought for " + m.seconds + "s" : "Thoughts";
        head.addView(Theme.text(this, label, Theme.Type.LABEL), new LinearLayout.LayoutParams(0, Theme.WRAP, 1));
        ImageView chev = Theme.icon(this, R.drawable.ic_chevron_down, Theme.TEXT_2, 16);
        head.addView(chev, new LinearLayout.LayoutParams(dp(16), dp(16)));
        box.addView(head, new LinearLayout.LayoutParams(Theme.WRAP, Theme.WRAP));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        View line = new View(this);
        line.setBackground(Theme.rounded(Theme.HAIRLINE_STRONG, dp(1)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(2), Theme.MATCH);
        lp.leftMargin = dp(10);
        lp.rightMargin = dp(12);
        body.addView(line, lp);
        TextView tt = Theme.text(this, Md.render(this, m.thinking), Theme.Type.CAPTION);
        tt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        tt.setLineSpacing(dp(4), 1f);
        tt.setTextIsSelectable(true);
        body.addView(tt, new LinearLayout.LayoutParams(0, Theme.WRAP, 1));
        body.setVisibility(View.GONE);
        box.addView(body, Theme.lp(Theme.MATCH, Theme.WRAP, dp(10)));

        head.setClickable(true);
        head.setOnClickListener(v -> {
            Theme.tick(v);
            boolean open = body.getVisibility() != View.VISIBLE;
            body.setVisibility(open ? View.VISIBLE : View.GONE);
            chev.animate().rotation(open ? 180f : 0f).setDuration(240).setInterpolator(Theme.EMPHASIZED).start();
            if (open) {
                body.setAlpha(0f);
                body.animate().alpha(1f).setDuration(260).start();
            }
        });
        return box;
    }

    private View smallIcon(int icon, Runnable action) {
        FrameLayout b = new FrameLayout(this);
        b.setBackground(Theme.ripple(Theme.rounded(0x00000000, dp(16)), dp(16)));
        b.addView(Theme.icon(this, icon, Theme.TEXT_2, 16));
        b.setClickable(true);
        b.setOnClickListener(v -> {
            Theme.tap(v);
            action.run();
        });
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(32), dp(32)));
        return b;
    }

    private void scrollToEnd(boolean smooth) {
        scroll.post(() -> {
            if (smooth) scroll.smoothScrollTo(0, list.getHeight());
            else scroll.scrollTo(0, list.getHeight());
            scroll.postDelayed(this::updateJump, 350);
        });
    }

    private void animateIn(View v) {
        v.setAlpha(0f);
        v.setTranslationY(dp(14));
        v.animate().alpha(1f).translationY(0).setDuration(420).setInterpolator(Theme.DECELERATE).start();
    }

    // ---------------------------------------------------------------- sending

    private void sendNew(String text, List<String> imgs) {
        if (busy) return;
        if (chat.messages.isEmpty()) list.removeAllViews();
        if (errorRow != null) {
            list.removeView(errorRow);
            errorRow = null;
        }
        // the previous reply is no longer the last one: re-render so its retry button goes away
        if (!chat.messages.isEmpty()) renderAll();
        Chats.Msg user = new Chats.Msg("user", text, imgs, "", "", null, System.currentTimeMillis());
        chat.messages.add(user);
        if (chat.title.isEmpty()) {
            String t = text.isEmpty() ? "Screenshot" : MainActivity.firstLine(text);
            chat.title = t.length() > 48 ? t.substring(0, 47) + "…" : t;
        }
        chat.updated = System.currentTimeMillis();
        Chats.save(this, chat);
        composer.clear();
        draftText = "";
        draftImages.clear();
        updateHeader();
        View v = userView(user);
        list.addView(v, rowLp());
        animateIn(v);
        startRequest();
    }

    private void retryLast() {
        if (busy || chat.messages.isEmpty()) return;
        Chats.Msg last = chat.messages.get(chat.messages.size() - 1);
        if (!last.isUser()) {
            chat.messages.remove(chat.messages.size() - 1);
            Chats.save(this, chat);
        }
        renderAll();
        startRequest();
    }

    private void startRequest() {
        busy = true;
        composer.setBusy(true);
        requestStart = System.currentTimeMillis();
        pendingRow = pendingView();
        list.addView(pendingRow, rowLp());
        animateIn(pendingRow);
        scrollToEnd(true);
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
                composer.setBusy(false);
                if (pendingRow != null) list.removeView(pendingRow);
                pendingRow = null;
                if (r != null) {
                    chat.messages.add(r);
                    chat.updated = System.currentTimeMillis();
                    Chats.save(this, chat);
                    View v = assistantView(r, true);
                    list.addView(v, rowLp());
                    animateIn(v);
                    Theme.confirm(v);
                } else {
                    showError(err);
                }
                scrollToEnd(true);
            });
        });
    }

    private View pendingView() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        OrbView orb = new OrbView(this, 4);
        orb.setDrawShadow(false);
        orb.setEnergy(1f);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(dp(28), dp(28));
        op.rightMargin = dp(10);
        row.addView(orb, op);
        Shimmer label = new Shimmer(this);
        Theme.style(label, Theme.Type.CALLOUT);
        row.addView(label);
        boolean think = Prefs.thinking(this);
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (pendingRow != row) return;
                long s = (System.currentTimeMillis() - requestStart) / 1000;
                String what = think ? "Thinking" : "Writing";
                label.setText(what + (s >= 1 ? " · " + s + "s" : ""));
                main.postDelayed(this, 1000);
            }
        };
        tick.run();
        return row;
    }

    private void showError(String msg) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(10), dp(12));
        row.setBackground(Theme.rounded(0x14FF8A80, dp(18), 0x33FF8A80, Math.max(1, dp(0.8f))));
        FrameLayout ic = new FrameLayout(this);
        ic.addView(Theme.icon(this, R.drawable.ic_alert, 0xFFFF8A80, 18));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(20), dp(20));
        ip.rightMargin = dp(12);
        row.addView(ic, ip);
        TextView t = Theme.text(this, msg, Theme.Type.CAPTION);
        t.setTextColor(Theme.TEXT);
        row.addView(t, new LinearLayout.LayoutParams(0, Theme.WRAP, 1));
        PillButton retry = new PillButton(this, "Retry", PillButton.Kind.SECONDARY, 0, true);
        retry.setOnClickListener(v -> {
            list.removeView(row);
            errorRow = null;
            startRequest();
        });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(Theme.WRAP, Theme.WRAP);
        rp.leftMargin = dp(10);
        row.addView(retry, rp);
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

    private void attachMenu() {
        Sheet s = new Sheet(this);
        s.title("Add to chat", null);
        LinearLayout g = Rows.group(this);
        Rows.add(g, Rows.row(this, R.drawable.ic_scan, "Capture screen", "Crop part of what's behind this chat", Rows.value(this, null), () -> {
            s.close();
            main.postDelayed(this::capture, 280);
        }));
        Rows.add(g, Rows.row(this, R.drawable.ic_image, "Photo library", "Pick an image from your phone", Rows.value(this, null), () -> {
            s.close();
            main.postDelayed(this::pickImage, 280);
        }));
        s.add(g, 16);
        s.show();
    }

    /** Leave the chat, let the user crop part of the screen, then come back with it attached. */
    private void capture() {
        if (busy) return;
        BubbleService svc = BubbleService.instance;
        if (svc == null) {
            Snack.show(this, "Turn on screen access in My App first", R.drawable.ic_alert);
            return;
        }
        draftText = composer.text();
        draftImages.clear();
        draftImages.addAll(composer.images());
        if (!chat.messages.isEmpty()) Chats.save(this, chat);
        String id = chat.id;
        finish();
        overridePendingTransition(0, R.anim.fade_out);
        svc.captureForChat(id);
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
            String name = saveFromUri(this, uri);
            main.post(() -> {
                if (name == null) Snack.show(this, "Couldn't open that image", R.drawable.ic_alert);
                else composer.addImage(name);
            });
        });
    }

    /** Decode a picked image (downsampled) and store it for the chat. */
    static String saveFromUri(Context c, Uri uri) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            int s = 1;
            while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= 1600) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            Bitmap b;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                b = BitmapFactory.decodeStream(in, null, o2);
            }
            if (b == null) return null;
            String name = Chats.saveImage(c, b);
            b.recycle();
            return name;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- drawer (chat list)

    private void buildDrawer(FrameLayout root) {
        drawerScrim = new View(this);
        drawerScrim.setBackgroundColor(Theme.SCRIM);
        drawerScrim.setAlpha(0f);
        drawerScrim.setVisibility(View.GONE);
        drawerScrim.setOnClickListener(v -> closeDrawer());
        root.addView(drawerScrim, new FrameLayout.LayoutParams(Theme.MATCH, Theme.MATCH));

        drawer = new FrameLayout(this);
        drawer.setBackgroundColor(0xFF0B0B0B);
        drawer.setClickable(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(14), dp(16), dp(16));
        Theme.insets(col, true, true, false);
        drawer.addView(col, new FrameLayout.LayoutParams(Theme.MATCH, Theme.MATCH));

        col.addView(Theme.text(this, "Chats", Theme.Type.HEADLINE), Theme.lp(Theme.MATCH, Theme.WRAP, dp(6)));
        PillButton fresh = new PillButton(this, "New chat", PillButton.Kind.PRIMARY, R.drawable.ic_edit, true);
        fresh.setOnClickListener(v -> {
            closeDrawer();
            newChat();
        });
        col.addView(fresh, Theme.lp(Theme.MATCH, Theme.WRAP, dp(16)));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        drawerList = new LinearLayout(this);
        drawerList.setOrientation(LinearLayout.VERTICAL);
        sv.addView(drawerList);
        col.addView(sv, new LinearLayout.LayoutParams(Theme.MATCH, 0, 1));

        LinearLayout settings = new LinearLayout(this);
        settings.setOrientation(LinearLayout.HORIZONTAL);
        settings.setGravity(Gravity.CENTER_VERTICAL);
        settings.setPadding(dp(12), dp(12), dp(12), dp(12));
        settings.setBackground(Theme.ripple(Theme.rounded(Theme.FILL, dp(16)), dp(16)));
        FrameLayout ic = new FrameLayout(this);
        ic.addView(Theme.icon(this, R.drawable.ic_sliders, Theme.TEXT, 18));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(20), dp(20));
        ip.rightMargin = dp(12);
        settings.addView(ic, ip);
        settings.addView(Theme.text(this, "My App settings", Theme.Type.CALLOUT));
        settings.setClickable(true);
        settings.setOnClickListener(v -> {
            closeDrawer();
            startActivity(new Intent(this, MainActivity.class));
        });
        col.addView(settings, Theme.lp(Theme.MATCH, Theme.WRAP, dp(10)));

        int w = Math.round(getResources().getDisplayMetrics().widthPixels * 0.84f);
        root.addView(drawer, new FrameLayout.LayoutParams(w, Theme.MATCH, Gravity.START));
        drawer.setTranslationX(-w);
        drawer.setVisibility(View.INVISIBLE);
    }

    private void fillDrawer() {
        drawerList.removeAllViews();
        List<Chats.Chat> all = Chats.list(this);
        if (all.isEmpty()) {
            TextView t = Theme.text(this, "No chats yet. Your conversations will show up here.", Theme.Type.CAPTION);
            drawerList.addView(t, Theme.lp(Theme.MATCH, Theme.WRAP, dp(20)));
            return;
        }
        String day = null;
        for (Chats.Chat c : all) {
            String d = History.day(c.updated);
            if (!d.equals(day)) {
                day = d;
                TextView h = Theme.text(this, d, Theme.Type.OVERLINE);
                h.setPadding(dp(10), 0, 0, 0);
                drawerList.addView(h, Theme.lp(Theme.MATCH, Theme.WRAP, dp(20)));
            }
            boolean current = chat != null && c.id.equals(chat.id);
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.VERTICAL);
            r.setPadding(dp(12), dp(10), dp(12), dp(10));
            r.setBackground(Theme.ripple(Theme.rounded(current ? Theme.FILL_PRESSED : 0x00000000, dp(14)), dp(14)));
            r.addView(Theme.oneLine(Theme.text(this, c.title.isEmpty() ? "Chat" : c.title, Theme.Type.CALLOUT)));
            TextView sub = Theme.oneLine(Theme.text(this, c.preview(), Theme.Type.LABEL));
            sub.setTextColor(Theme.TEXT_3);
            r.addView(sub, Theme.lp(Theme.MATCH, Theme.WRAP, dp(3)));
            r.setClickable(true);
            r.setOnClickListener(v -> {
                Theme.tap(v);
                closeDrawer();
                if (busy) return;
                Chats.Chat loaded = Chats.load(this, c.id);
                if (loaded != null) {
                    chat = loaded;
                    renderAll();
                    updateHeader();
                }
            });
            r.setOnLongClickListener(v -> {
                Theme.tick(v);
                Sheets.confirm(this, "Delete this chat?", c.title, "Delete", () -> {
                    Chats.delete(this, c.id);
                    if (chat != null && chat.id.equals(c.id)) newChat();
                    fillDrawer();
                    Snack.show(this, "Chat deleted", R.drawable.ic_check);
                });
                return true;
            });
            drawerList.addView(r, Theme.lp(Theme.MATCH, Theme.WRAP, dp(4)));
        }
    }

    private void openDrawer() {
        if (drawerOpen) return;
        drawerOpen = true;
        fillDrawer();
        android.view.inputmethod.InputMethodManager imm = getSystemService(android.view.inputmethod.InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(composer.input().getWindowToken(), 0);
        drawer.setVisibility(View.VISIBLE);
        drawerScrim.setVisibility(View.VISIBLE);
        drawer.animate().translationX(0).setDuration(420).setInterpolator(Theme.DECELERATE).start();
        drawerScrim.animate().alpha(1f).setDuration(300).start();
    }

    private void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        drawer.animate().translationX(-drawer.getWidth()).setDuration(280).setInterpolator(Theme.ACCELERATE)
                .withEndAction(() -> drawer.setVisibility(View.INVISIBLE)).start();
        drawerScrim.animate().alpha(0f).setDuration(260).withEndAction(() -> drawerScrim.setVisibility(View.GONE)).start();
    }

    private void newChat() {
        if (busy) return;
        chat = Chats.create();
        composer.clear();
        draftText = "";
        draftImages.clear();
        renderAll();
        updateHeader();
    }

    private int dp(float v) {
        return Theme.dp(this, v);
    }

    /** Text with a soft highlight sweeping across it. */
    static final class Shimmer extends TextView {
        private final Matrix m = new Matrix();
        private LinearGradient shader;
        private float x;
        private ValueAnimator anim;

        Shimmer(Context c) {
            super(c);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            shader = new LinearGradient(0, 0, w * 0.6f, 0,
                    new int[]{0x66FFFFFF, 0xFFFFFFFF, 0x66FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            getPaint().setShader(shader);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            anim = ValueAnimator.ofFloat(-1f, 2f);
            anim.setDuration(1600);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.addUpdateListener(a -> {
                x = (float) a.getAnimatedValue();
                invalidate();
            });
            anim.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            if (anim != null) anim.cancel();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (shader != null) {
                m.setTranslate(x * getWidth() * 0.6f - getWidth() * 0.3f, 0);
                shader.setLocalMatrix(m);
            }
            super.onDraw(canvas);
        }
    }
}
