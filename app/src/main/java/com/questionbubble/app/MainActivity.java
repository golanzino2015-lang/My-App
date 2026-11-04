package com.questionbubble.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Setup screen: asks for the accessibility permission and stores the API key. */
public class MainActivity extends Activity {

    private TextView status;
    private Button grantButton;
    private boolean askedThisLaunch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Question Bubble");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTextColor(Color.WHITE);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        status.setPadding(0, dp(12), 0, dp(12));
        root.addView(status);

        grantButton = new Button(this);
        grantButton.setText("1. Give permission (Accessibility)");
        grantButton.setOnClickListener(v -> openAccessibilitySettings());
        root.addView(grantButton);

        Button restricted = new Button(this);
        restricted.setText("Permission greyed out? Allow restricted settings");
        restricted.setOnClickListener(v -> showRestrictedHelp());
        root.addView(restricted);

        root.addView(label("2. OpenAI API key"));
        EditText keyInput = new EditText(this);
        keyInput.setHint("sk-...");
        keyInput.setSingleLine(true);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setText(Prefs.apiKey(this));
        root.addView(keyInput);

        root.addView(label("Model (needs image support)"));
        EditText modelInput = new EditText(this);
        modelInput.setSingleLine(true);
        modelInput.setHint(Prefs.DEFAULT_MODEL);
        modelInput.setText(Prefs.model(this));
        root.addView(modelInput);

        Button save = new Button(this);
        save.setText("Save");
        save.setOnClickListener(v -> {
            Prefs.save(this, keyInput.getText().toString(), modelInput.getText().toString());
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        });
        root.addView(save);

        TextView help = new TextView(this);
        help.setPadding(0, dp(16), 0, 0);
        help.setText("How to use:\n"
                + "• Once permission is on, a green bubble floats over every app. Drag it anywhere.\n"
                + "• Tap the bubble to open the menu.\n"
                + "• Select Question → drag a box around the question, OR long-press the question "
                + "and it will auto-detect it.\n"
                + "• Type Question → type it in yourself.\n"
                + "• The answer is copied to your clipboard (and shown briefly).\n"
                + "• To hide the bubble, turn the app off in Accessibility settings.");
        root.addView(help);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean on = isServiceEnabled();
        status.setText(on
                ? "✅ Permission granted. The bubble is running."
                : "⚠️ Permission needed: turn on \"Question Bubble\" in Accessibility settings so "
                  + "the bubble can show over other apps.");
        status.setTextColor(on ? 0xFF4CAF50 : 0xFFFFB300);
        grantButton.setEnabled(!on);

        if (!on && !askedThisLaunch) {
            askedThisLaunch = true;
            new AlertDialog.Builder(this)
                    .setTitle("Permission required")
                    .setMessage("Question Bubble needs the Accessibility permission (like auto-clicker "
                            + "apps) so it can show a floating bubble over other apps and read the "
                            + "part of the screen you select.\n\nOn the next screen open "
                            + "\"Installed apps\" / \"Downloaded apps\" → Question Bubble → turn it on.")
                    .setPositiveButton("Open settings", (d, w) -> openAccessibilitySettings())
                    .setNegativeButton("Later", null)
                    .show();
        }
    }

    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void showRestrictedHelp() {
        new AlertDialog.Builder(this)
                .setTitle("Allow restricted settings")
                .setMessage("Android 13+ blocks Accessibility for apps installed from an APK file.\n\n"
                        + "1. Tap \"Open App info\".\n"
                        + "2. Tap the ⋮ menu (top-right) → \"Allow restricted settings\".\n"
                        + "3. Come back and tap \"Give permission\" again.\n\n"
                        + "(If you don't see the option, first try turning the permission on once "
                        + "so Android shows the 'Restricted setting' message, then come back.)")
                .setPositiveButton("Open App info", (d, w) -> startActivity(
                        new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + getPackageName()))))
                .setNegativeButton("Close", null)
                .show();
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName cn = new ComponentName(this, BubbleService.class);
        return enabled.contains(cn.flattenToString()) || enabled.contains(cn.flattenToShortString());
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setPadding(0, dp(16), 0, 0);
        t.setGravity(Gravity.START);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
