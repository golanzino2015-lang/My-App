package com.questionbubble.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/** First-launch flow: welcome, permission, API key, done. */
public class OnboardingActivity extends Activity {

    static final String EXTRA_REPLAY = "replay";
    private static final int PAGES = 4;

    private final Handler main = new Handler(Looper.getMainLooper());
    private OrbView orb;
    private FrameLayout holder;
    private PageDots dots;
    private PillButton primary;
    private PillButton secondary;
    private int page = -1;
    private View current;

    // page 1
    private LinearLayout grantedRow;
    private boolean accessShownGranted;
    // page 2
    private KeyField keyField;
    private Provider chosen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setDecorFitsSystemWindows(false);
        Fonts.init(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(Theme.background(this));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, dp(12), 0, dp(14));
        root.addView(column, new FrameLayout.LayoutParams(Theme.MATCH, Theme.MATCH));
        Theme.insets(column, true, true, true);

        // orb stage
        FrameLayout stage = new FrameLayout(this);
        View glow = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setColors(new int[]{0x1FFFFFFF, 0x08FFFFFF, 0x00FFFFFF});
        g.setGradientRadius(dp(220));
        g.setShape(GradientDrawable.OVAL);
        glow.setBackground(g);
        stage.addView(glow, new FrameLayout.LayoutParams(dp(440), dp(440), Gravity.CENTER));
        orb = new OrbView(this, 40);
        orb.setGlow(0.35f);
        stage.addView(orb, new FrameLayout.LayoutParams(dp(250), dp(250), Gravity.CENTER));
        stage.setClipChildren(false);
        column.addView(stage, new LinearLayout.LayoutParams(Theme.MATCH, 0, 1));

        holder = new FrameLayout(this);
        holder.setPadding(dp(28), 0, dp(28), 0);
        holder.setClipChildren(false);
        android.animation.LayoutTransition lt = new android.animation.LayoutTransition();
        lt.enableTransitionType(android.animation.LayoutTransition.CHANGING);
        lt.setDuration(420);
        lt.setInterpolator(android.animation.LayoutTransition.CHANGING, Theme.EMPHASIZED);
        lt.setAnimator(android.animation.LayoutTransition.APPEARING, null);
        lt.setAnimator(android.animation.LayoutTransition.DISAPPEARING, null);
        holder.setLayoutTransition(lt);
        android.animation.LayoutTransition lt2 = new android.animation.LayoutTransition();
        lt2.enableTransitionType(android.animation.LayoutTransition.CHANGING);
        lt2.setDuration(420);
        lt2.setInterpolator(android.animation.LayoutTransition.CHANGING, Theme.EMPHASIZED);
        column.setLayoutTransition(lt2);
        column.addView(holder, Theme.lp(Theme.MATCH, Theme.WRAP));

        dots = new PageDots(this, PAGES);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Theme.WRAP, dp(8));
        dlp.topMargin = dp(30);
        dlp.gravity = Gravity.CENTER_HORIZONTAL;
        column.addView(dots, dlp);

        primary = new PillButton(this, "Get started", PillButton.Kind.PRIMARY);
        LinearLayout.LayoutParams pp = Theme.lp(Theme.MATCH, Theme.WRAP, dp(26));
        pp.leftMargin = pp.rightMargin = dp(24);
        column.addView(primary, pp);
        primary.setOnClickListener(v -> onPrimary());

        secondary = new PillButton(this, " ", PillButton.Kind.GHOST, 0, true);
        LinearLayout.LayoutParams sp = Theme.lp(Theme.MATCH, Theme.WRAP, dp(6));
        sp.leftMargin = sp.rightMargin = dp(24);
        column.addView(secondary, sp);
        secondary.setOnClickListener(v -> onSecondary());

        setContentView(root);

        orb.setScaleX(0.6f);
        orb.setScaleY(0.6f);
        orb.setAlpha(0f);
        orb.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(900)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f)).start();
        glow.setAlpha(0f);
        glow.animate().alpha(1f).setDuration(1400).start();
        Theme.enter(dots, 520);
        Theme.enter(primary, 580);
        goTo(0, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (page == 1) refreshAccess(true);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (page > 0) goTo(page - 1, false);
        else super.onBackPressed();
    }

    // ---------------------------------------------------------------- navigation

    private void goTo(int idx, boolean forward) {
        if (idx == page) return;
        int dir = forward ? 1 : -1;
        View old = current;
        if (old != null) {
            old.animate().alpha(0f).translationX(-dp(36) * dir).setDuration(200)
                    .setInterpolator(Theme.ACCELERATE).withEndAction(() -> holder.removeView(old)).start();
        }
        page = idx;
        List<View> parts = new ArrayList<>();
        LinearLayout p = build(idx, parts);
        current = p;
        holder.addView(p, new FrameLayout.LayoutParams(Theme.MATCH, Theme.WRAP, Gravity.BOTTOM));
        for (int i = 0; i < parts.size(); i++) {
            View v = parts.get(i);
            v.setAlpha(0f);
            v.setTranslationX(dp(40) * dir);
            v.animate().alpha(1f).translationX(0).setStartDelay(old == null ? 260 + 70L * i : 120 + 55L * i)
                    .setDuration(560).setInterpolator(Theme.DECELERATE).start();
        }
        dots.select(idx);
        updateButtons();
        if (old != null) {
            orb.setEnergy(0.7f);
            main.postDelayed(() -> orb.setEnergy(0f), 450);
        }
        if (idx == 3) main.postDelayed(orb::ripple, 300);
        if (idx == 2) main.postDelayed(() -> {
            if (page != 2 || keyField == null) return;
            keyField.editText().requestFocus();
            android.view.inputmethod.InputMethodManager imm =
                    getSystemService(android.view.inputmethod.InputMethodManager.class);
            if (imm != null) imm.showSoftInput(keyField.editText(), 0);
        }, 650);
        else hideKeyboard();
    }

    private void updateButtons() {
        switch (page) {
            case 0:
                setLabels("Get started", null);
                break;
            case 1:
                boolean on = Access.enabled(this);
                setLabels(on ? "Continue" : "Open settings", on ? null : "Switch greyed out?");
                break;
            case 2:
                setLabels("Verify key", "Skip for now");
                break;
            default:
                setLabels("Start using My App", null);
        }
    }

    private void setLabels(String p, String s) {
        primary.labelView().animate().alpha(0f).setDuration(90).withEndAction(() -> {
            primary.setText(p);
            primary.labelView().animate().alpha(1f).setDuration(160).start();
        }).start();
        if (s == null) {
            secondary.animate().alpha(0f).setDuration(150).start();
            secondary.setClickable(false);
        } else {
            secondary.setText(s);
            secondary.setClickable(true);
            secondary.animate().alpha(1f).setDuration(200).start();
        }
    }

    private void onPrimary() {
        switch (page) {
            case 0:
                goTo(1, true);
                break;
            case 1:
                if (Access.enabled(this)) goTo(2, true);
                else startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                break;
            case 2:
                verifyKey();
                break;
            default:
                Prefs.setOnboarded(this, true);
                if (!getIntent().getBooleanExtra(EXTRA_REPLAY, false)) {
                    startActivity(new Intent(this, MainActivity.class));
                }
                finish();
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        }
    }

    private void onSecondary() {
        if (page == 1) Sheets.restricted(this);
        else if (page == 2) goTo(3, true);
    }

    private void verifyKey() {
        String key = keyField.text();
        if (key.isEmpty()) {
            keyField.setError("Paste your API key first.");
            return;
        }
        Provider provider = chosen;
        primary.setLoading(true);
        Executors.newSingleThreadExecutor().execute(() -> {
            String err = null;
            try {
                Ai.verify(provider, key);
            } catch (Exception e) {
                err = e.getMessage();
            }
            String error = err;
            main.post(() -> {
                if (isFinishing()) return;
                primary.setLoading(false);
                if (error != null) {
                    keyField.setError(error);
                } else {
                    Prefs.setApiKey(this, provider, key);
                    Prefs.setProvider(this, provider);
                    keyField.setOk("Key verified. " + provider.label + " is connected.");
                    Theme.confirm(keyField);
                    orb.ripple();
                    main.postDelayed(() -> goTo(3, true), 700);
                }
            });
        });
    }

    private void refreshAccess(boolean animate) {
        boolean on = Access.enabled(this);
        updateButtons();
        if (on && grantedRow != null && !accessShownGranted) {
            accessShownGranted = true;
            grantedRow.setVisibility(View.VISIBLE);
            if (animate) {
                Theme.enter(grantedRow, 0);
                CheckView cv = (CheckView) grantedRow.getChildAt(0);
                cv.play();
                Theme.confirm(grantedRow);
                orb.ripple();
            }
        }
    }

    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager imm = getSystemService(android.view.inputmethod.InputMethodManager.class);
        if (imm != null && getCurrentFocus() != null) imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
    }

    // ---------------------------------------------------------------- pages

    private LinearLayout build(int idx, List<View> parts) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        switch (idx) {
            case 0: {
                parts.add(add(p, Theme.text(this, "My App", Theme.Type.OVERLINE), 0));
                parts.add(add(p, Theme.text(this, "Answers that float above everything.", Theme.Type.DISPLAY), 14));
                parts.add(add(p, Theme.text(this, "Point at any question on your screen. The answer lands on your clipboard in seconds.", Theme.Type.BODY), 14));
                break;
            }
            case 1: {
                parts.add(add(p, Theme.text(this, "Step 1 of 2", Theme.Type.OVERLINE), 0));
                parts.add(add(p, Theme.text(this, "Allow screen access", Theme.Type.HEADLINE), 12));
                parts.add(add(p, Theme.text(this, "Turn on My App in Accessibility, the same permission auto-clickers use.", Theme.Type.BODY), 10));
                parts.add(add(p, feature(R.drawable.ic_layers, "Floats over every app", "Reach it from anywhere"), 22));
                parts.add(add(p, feature(R.drawable.ic_scan, "Sees only what you select", "Nothing is captured until you scan"), 14));
                parts.add(add(p, feature(R.drawable.ic_lock, "Private by design", "Images go only to the AI you pick, with your key"), 14));
                grantedRow = new LinearLayout(this);
                grantedRow.setOrientation(LinearLayout.HORIZONTAL);
                grantedRow.setGravity(Gravity.CENTER_VERTICAL);
                grantedRow.addView(new CheckView(this), new LinearLayout.LayoutParams(dp(22), dp(22)));
                TextView gt = Theme.text(this, "Access granted", Theme.Type.CALLOUT);
                LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(Theme.WRAP, Theme.WRAP);
                gp.leftMargin = dp(10);
                grantedRow.addView(gt, gp);
                accessShownGranted = Access.enabled(this);
                grantedRow.setVisibility(accessShownGranted ? View.VISIBLE : View.GONE);
                add(p, grantedRow, 20);
                if (accessShownGranted) parts.add(grantedRow);
                break;
            }
            case 2: {
                chosen = Prefs.provider(this);
                parts.add(add(p, Theme.text(this, "Step 2 of 2", Theme.Type.OVERLINE), 0));
                parts.add(add(p, Theme.text(this, "Choose your AI", Theme.Type.HEADLINE), 12));
                TextView body = Theme.text(this, "", Theme.Type.BODY);
                parts.add(add(p, body, 10));
                Provider[] all = Provider.values();
                String[] names = new String[all.length];
                for (int i = 0; i < all.length; i++) names[i] = all[i].label;
                SegmentedView picker = new SegmentedView(this, names, chosen.ordinal());
                parts.add(add(p, picker, 20));
                keyField = new KeyField(this);
                parts.add(add(p, keyField, 12));
                TextView link = Theme.text(this, "", Theme.Type.LABEL);
                link.setPaintFlags(link.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
                link.setPadding(dp(6), dp(4), 0, dp(4));
                link.setOnClickListener(v -> Sheets.openUrl(this, chosen.keyUrl));
                parts.add(add(p, link, 4));
                Runnable apply = () -> {
                    body.setText("Paste your " + chosen.label + " API key. It stays on this phone and is only sent to "
                            + chosen.company + ".");
                    keyField.setHint(chosen.keyHint);
                    keyField.setText(Prefs.apiKey(this, chosen));
                    keyField.clearStatus();
                    link.setText("Get a key at " + chosen.keySite);
                };
                apply.run();
                picker.setListener(i -> {
                    chosen = all[i];
                    apply.run();
                });
                break;
            }
            default: {
                parts.add(add(p, Theme.text(this, "Ready", Theme.Type.OVERLINE), 0));
                parts.add(add(p, Theme.text(this, "You're all set.", Theme.Type.DISPLAY), 12));
                parts.add(add(p, tip("Tap the orb", "Open the menu"), 22));
                parts.add(add(p, tip("Hold the orb", "Start a scan right away"), 12));
                parts.add(add(p, tip("Drag it to the X", "Hide it until you need it"), 12));
            }
        }
        return p;
    }

    private View add(LinearLayout p, View v, int topDp) {
        p.addView(v, Theme.lp(Theme.MATCH, Theme.WRAP, dp(topDp)));
        return v;
    }

    private View feature(int icon, String title, String sub) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout tile = Theme.iconTile(this, icon, 40);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(40), dp(40));
        tp.rightMargin = dp(14);
        r.addView(tile, tp);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Theme.text(this, title, Theme.Type.BODY_M));
        col.addView(Theme.text(this, sub, Theme.Type.CAPTION), Theme.lp(Theme.WRAP, Theme.WRAP, dp(3)));
        r.addView(col);
        return r;
    }

    private View tip(String title, String sub) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), dp(14), dp(16), dp(14));
        r.setBackground(Theme.card(this));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Theme.text(this, title, Theme.Type.BODY_M));
        col.addView(Theme.text(this, sub, Theme.Type.CAPTION), Theme.lp(Theme.WRAP, Theme.WRAP, dp(3)));
        r.addView(col, new LinearLayout.LayoutParams(0, Theme.WRAP, 1));
        return r;
    }

    private int dp(float v) {
        return Theme.dp(this, v);
    }

    /** Dots where the active one stretches into a pill. */
    static final class PageDots extends View {
        private final int count;
        private final Paint on = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint off = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float pos;
        private ValueAnimator anim;

        PageDots(Context c, int count) {
            super(c);
            this.count = count;
            on.setColor(Theme.ACCENT);
            off.setColor(0x38FFFFFF);
        }

        void select(int i) {
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(pos, i);
            anim.setDuration(460);
            anim.setInterpolator(Theme.EMPHASIZED);
            anim.addUpdateListener(a -> {
                pos = (float) a.getAnimatedValue();
                invalidate();
            });
            anim.start();
        }

        @Override
        protected void onMeasure(int w, int h) {
            float d = getResources().getDisplayMetrics().density;
            setMeasuredDimension(Math.round((count - 1) * 14 * d + 26 * d), Math.round(8 * d));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float d = getResources().getDisplayMetrics().density;
            float h = getHeight(), x = 0;
            for (int i = 0; i < count; i++) {
                float k = Math.max(0f, 1f - Math.abs(pos - i));
                float w = 6 * d + 20 * d * k;
                r.set(x, (h - 6 * d) / 2, x + w, (h + 6 * d) / 2);
                off.setAlpha(Math.round(0x38 + (0xFF - 0x38) * k));
                canvas.drawRoundRect(r, 3 * d, 3 * d, off);
                x += w + 8 * d;
            }
        }
    }
}
