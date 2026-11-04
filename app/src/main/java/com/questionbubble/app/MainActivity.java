package com.questionbubble.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Home screen: access status, API key, and a short guide. */
public class MainActivity extends Activity {

    private TextView statusTitle, statusSub, accessButton;
    private View statusDot;
    private boolean askedThisLaunch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(36), dp(20), dp(28));

        // ---- header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
        lp.rightMargin = dp(14);
        header.addView(logo, lp);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(Ui.text(this, "Question Bubble", 22, 700, Ui.TEXT));
        titles.addView(Ui.text(this, "Answers from your screen, one tap away.", 13, 400, Ui.DIM),
                Ui.lp(WRAP, WRAP, dp(5)));
        header.addView(titles);
        root.addView(header);

        // ---- access card
        LinearLayout access = card();
        access.addView(Ui.label(this, "Access"));
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusDot = new View(this);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(10), dp(10));
        dlp.rightMargin = dp(10);
        statusRow.addView(statusDot, dlp);
        statusTitle = Ui.text(this, "", 16, 600, Ui.TEXT);
        statusRow.addView(statusTitle);
        access.addView(statusRow, Ui.lp(MATCH, WRAP, dp(14)));
        statusSub = Ui.text(this, "", 13, 400, Ui.DIM);
        statusSub.setLineSpacing(dp(3), 1f);
        access.addView(statusSub, Ui.lp(MATCH, WRAP, dp(8)));
        accessButton = Ui.primaryButton(this, "Grant access");
        accessButton.setOnClickListener(v -> openAccessibilitySettings());
        access.addView(accessButton, Ui.lp(MATCH, WRAP, dp(16)));
        TextView greyed = Ui.text(this, "Switch greyed out?", 12.5f, 500, Ui.DIM);
        greyed.setGravity(Gravity.CENTER);
        greyed.setPadding(0, dp(12), 0, dp(2));
        greyed.setOnClickListener(v -> showRestrictedHelp());
        access.addView(greyed, Ui.lp(MATCH, WRAP, 0));
        root.addView(access, Ui.lp(MATCH, WRAP, dp(24)));

        // ---- OpenAI card
        LinearLayout ai = card();
        ai.addView(Ui.label(this, "OpenAI"));
        ai.addView(Ui.text(this, "API key", 13, 500, Ui.DIM), Ui.lp(WRAP, WRAP, dp(14)));
        EditText keyInput = Ui.input(this, "sk-...", true);
        keyInput.setText(Prefs.apiKey(this));
        ai.addView(keyInput, Ui.lp(MATCH, WRAP, dp(8)));
        ai.addView(Ui.text(this, "Model", 13, 500, Ui.DIM), Ui.lp(WRAP, WRAP, dp(14)));
        EditText modelInput = Ui.input(this, Prefs.DEFAULT_MODEL, false);
        modelInput.setSingleLine(true);
        modelInput.setText(Prefs.model(this));
        ai.addView(modelInput, Ui.lp(MATCH, WRAP, dp(8)));
        TextView save = Ui.primaryButton(this, "Save");
        save.setOnClickListener(v -> {
            Prefs.save(this, keyInput.getText().toString(), modelInput.getText().toString());
            keyInput.clearFocus();
            modelInput.clearFocus();
            save.setText("Saved");
            save.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).withEndAction(() ->
                    save.animate().scaleX(1f).scaleY(1f).setDuration(200).start()).start();
            save.postDelayed(() -> save.setText("Save"), 1600);
        });
        ai.addView(save, Ui.lp(MATCH, WRAP, dp(18)));
        TextView keyNote = Ui.text(this, "Stored only on this device. Sent only to OpenAI.", 11.5f, 400, Ui.FAINT);
        keyNote.setGravity(Gravity.CENTER);
        ai.addView(keyNote, Ui.lp(MATCH, WRAP, dp(12)));
        root.addView(ai, Ui.lp(MATCH, WRAP, dp(14)));

        // ---- guide card
        LinearLayout guide = card();
        guide.addView(Ui.label(this, "How it works"));
        step(guide, "01", "Tap the bubble", "It floats over every app. Drag it and it snaps to the edges and corners.");
        step(guide, "02", "Select question", "Drag a box around it, or press and hold on it to auto-detect.");
        step(guide, "03", "Paste the answer", "The answer is copied to your clipboard automatically.");
        root.addView(guide, Ui.lp(MATCH, WRAP, dp(14)));

        TextView footer = Ui.text(this, "QUESTION BUBBLE  ·  V1.0", 10, 700, Ui.FAINT);
        footer.setLetterSpacing(0.14f);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, Ui.lp(MATCH, WRAP, dp(24)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(root);
        setContentView(scroll);

        Ui.enter(header, 0);
        Ui.enter(access, 70);
        Ui.enter(ai, 140);
        Ui.enter(guide, 210);
        Ui.enter(footer, 280);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean on = isServiceEnabled();
        statusTitle.setText(on ? "Active" : "Not enabled");
        statusSub.setText(on
                ? "The bubble is running over your apps."
                : "Turn on Question Bubble in Accessibility settings so the bubble can float over "
                  + "other apps and read the area you select.");
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        if (on) dot.setColor(Ui.WHITE);
        else dot.setStroke(dp(2), Ui.FAINT);
        statusDot.setBackground(dot);
        if (on) {
            accessButton.setText("Accessibility settings");
            accessButton.setTextColor(Ui.WHITE);
            accessButton.setBackground(Ui.rounded(0x00000000, dp(12), Ui.SEP, dp(1)));
        } else {
            accessButton.setText("Grant access");
            accessButton.setTextColor(Ui.WIN);
            accessButton.setBackground(Ui.rounded(Ui.WHITE, dp(12), 0, 0));
        }

        if (!on && !askedThisLaunch) {
            askedThisLaunch = true;
            statusTitle.postDelayed(() -> {
                if (isFinishing()) return;
                Ui.dialog(this, "Permission required",
                        "Question Bubble needs Accessibility access, the same permission auto-clickers "
                                + "use, so it can float over other apps and capture the part of the screen "
                                + "you select.\n\nOn the next screen open Installed apps, choose Question "
                                + "Bubble and turn it on.",
                        "Open settings", this::openAccessibilitySettings, "Later");
            }, 450);
        }
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(18), dp(18), dp(18));
        c.setBackground(Ui.card(this));
        return c;
    }

    private void step(LinearLayout parent, String num, String title, String body) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView n = Ui.text(this, num, 12, 700, Ui.FAINT);
        n.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(dp(30), ViewGroup.LayoutParams.WRAP_CONTENT);
        np.topMargin = dp(1);
        row.addView(n, np);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.text(this, title, 14, 600, Ui.TEXT));
        TextView b = Ui.text(this, body, 12.5f, 400, Ui.DIM);
        b.setLineSpacing(dp(3), 1f);
        col.addView(b, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, dp(4)));
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        parent.addView(row, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, dp(16)));
    }

    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void showRestrictedHelp() {
        Ui.dialog(this, "Allow restricted settings",
                "Android 13 and newer block Accessibility for apps installed from a file.\n\n"
                        + "1. Tap Open App info.\n"
                        + "2. Tap the menu in the top-right corner, then Allow restricted settings.\n"
                        + "3. Come back and tap Grant access again.\n\n"
                        + "If the option is missing, try turning access on once first so Android "
                        + "shows its Restricted setting message, then come back.",
                "Open App info", () -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()))), "Close");
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName cn = new ComponentName(this, BubbleService.class);
        return enabled.contains(cn.flattenToString()) || enabled.contains(cn.flattenToShortString());
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
