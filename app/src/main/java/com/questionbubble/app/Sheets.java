package com.questionbubble.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.Executors;

/** The app's bottom sheets. */
final class Sheets {

    /** Pick ChatGPT / Claude / Gemini. Picking one without a key asks for it. */
    static void provider(Activity a, Runnable onChanged) {
        Sheet s = new Sheet(a);
        s.title("Choose your AI", "Each one uses its own API key. You can switch any time.");
        Provider current = Prefs.provider(a);
        LinearLayout group = Rows.group(a);
        for (Provider p : Provider.values()) {
            boolean hasKey = !Prefs.apiKey(a, p).isEmpty();
            View radio = radio(a, p == current);
            Rows.add(group, Rows.row(a, 0, p.label, p.company + " · " + (hasKey ? "Key added" : "No key yet"), radio, () -> {
                Prefs.setProvider(a, p);
                Theme.tick(radio);
                s.close();
                if (onChanged != null) onChanged.run();
                if (!hasKey) new Handler(Looper.getMainLooper()).postDelayed(() -> apiKey(a, p, onChanged), 320);
            }));
        }
        s.add(group, 18);
        s.show();
    }

    /** Edit + verify the API key for one provider. */
    static void apiKey(Activity a, Provider p, Runnable onSaved) {
        Sheet s = new Sheet(a);
        s.title(p.label + " API key", "Stored only on this phone. Sent only to " + p.company + " when you ask a question.");
        KeyField field = new KeyField(a);
        field.setHint(p.keyHint);
        field.setText(Prefs.apiKey(a, p));
        s.add(field, 20);
        TextView link = Theme.text(a, "Get a key at " + p.keySite, Theme.Type.LABEL);
        link.setPaintFlags(link.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        link.setPadding(Theme.dp(a, 6), Theme.dp(a, 2), 0, Theme.dp(a, 2));
        link.setOnClickListener(v -> openUrl(a, p.keyUrl));
        s.add(link, 2);
        PillButton save = new PillButton(a, "Verify & save", PillButton.Kind.PRIMARY);
        s.add(save, 18);
        Handler main = new Handler(Looper.getMainLooper());
        save.setOnClickListener(v -> {
            String key = field.text();
            save.setLoading(true);
            Executors.newSingleThreadExecutor().execute(() -> {
                String err = null;
                try {
                    Ai.verify(p, key);
                } catch (Exception e) {
                    err = e.getMessage();
                }
                String error = err;
                main.post(() -> {
                    save.setLoading(false);
                    if (error != null) {
                        field.setError(error);
                    } else {
                        Prefs.setApiKey(a, p, key);
                        field.setOk("Key verified");
                        Theme.confirm(field);
                        main.postDelayed(() -> {
                            s.close();
                            if (onSaved != null) onSaved.run();
                        }, 550);
                    }
                });
            });
        });
        s.show();
        field.editText().requestFocus();
    }

    /** Choose the model for the selected provider, or type a custom one. */
    static void model(Activity a, Runnable onChanged) {
        Provider p = Prefs.provider(a);
        Sheet s = new Sheet(a);
        s.title(p.label + " model", "Must support images. Faster models answer quicker; larger ones are more accurate.");
        String current = Prefs.model(a, p);
        boolean known = false;
        LinearLayout group = Rows.group(a);
        for (String[] o : p.models) {
            boolean on = o[0].equals(current);
            known |= on;
            View radio = radio(a, on);
            Rows.add(group, Rows.row(a, 0, o[0], o[1], radio, () -> {
                Prefs.setModel(a, p, o[0]);
                Theme.tick(radio);
                s.close();
                if (onChanged != null) onChanged.run();
            }));
        }
        String customSub = known ? "Type any model name" : current;
        Rows.add(group, Rows.row(a, 0, "Custom model", customSub, radio(a, !known), () -> {
            s.close();
            new Handler(Looper.getMainLooper()).postDelayed(() -> customModel(a, p, onChanged), 320);
        }));
        s.add(group, 18);
        s.show();
    }

    private static void customModel(Activity a, Provider p, Runnable onChanged) {
        Sheet s = new Sheet(a);
        s.title("Custom model", "Enter the exact model name from " + p.company + ".");
        EditText in = new EditText(a);
        in.setSingleLine(true);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        in.setHint(p.defaultModel);
        in.setHintTextColor(Theme.TEXT_3);
        in.setTextColor(Theme.TEXT);
        in.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        in.setTypeface(Fonts.weight(a, 500));
        in.setPadding(Theme.dp(a, 18), Theme.dp(a, 18), Theme.dp(a, 18), Theme.dp(a, 18));
        in.setBackground(Theme.rounded(Theme.SURFACE_3, Theme.dp(a, 18), Theme.HAIRLINE_STRONG, Math.max(1, Theme.dp(a, 1))));
        in.setText(Prefs.model(a, p));
        s.add(in, 20);
        PillButton save = new PillButton(a, "Use this model", PillButton.Kind.PRIMARY);
        save.setOnClickListener(v -> {
            String m = in.getText().toString().trim();
            if (m.isEmpty()) {
                Theme.shake(in);
                return;
            }
            Prefs.setModel(a, p, m);
            s.close();
            if (onChanged != null) onChanged.run();
        });
        s.add(save, 18);
        s.show();
        in.requestFocus();
    }

    private static View radio(Activity a, boolean on) {
        FrameLayout f = new FrameLayout(a);
        f.setBackground(on ? Theme.rounded(Theme.WHITE, Theme.dp(a, 11))
                : Theme.rounded(0x00000000, Theme.dp(a, 11), Theme.TEXT_3, Theme.dp(a, 1.5f)));
        if (on) f.addView(Theme.icon(a, R.drawable.ic_check, Theme.BLACK, 14));
        f.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(a, 22), Theme.dp(a, 22)));
        return f;
    }

    static void openUrl(Activity a, String url) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }

    /** Yes / no confirmation. */
    static void confirm(Activity a, String title, String body, String action, Runnable onConfirm) {
        Sheet s = new Sheet(a);
        s.title(title, body);
        PillButton yes = new PillButton(a, action, PillButton.Kind.PRIMARY);
        yes.setOnClickListener(v -> {
            s.close();
            onConfirm.run();
        });
        s.add(yes, 24);
        PillButton no = new PillButton(a, "Cancel", PillButton.Kind.GHOST);
        no.setOnClickListener(v -> s.close());
        s.add(no, 6);
        s.show();
    }

    /** Android 13+ "restricted setting" walkthrough. */
    static void restricted(Activity a) {
        Sheet s = new Sheet(a);
        s.title("Switch greyed out?", "Android blocks Accessibility for apps installed from a file until you allow it.");
        LinearLayout steps = new LinearLayout(a);
        steps.setOrientation(LinearLayout.VERTICAL);
        step(a, steps, "1", "Open App info below");
        step(a, steps, "2", "Tap the ⋮ menu in the top-right corner");
        step(a, steps, "3", "Choose Allow restricted settings");
        step(a, steps, "4", "Come back and turn Question Bubble on");
        s.add(steps, 18);
        TextView note = Theme.text(a, "Don't see the option? Try switching access on once so Android shows its warning, then check again.", Theme.Type.CAPTION);
        s.add(note, 14);
        PillButton open = new PillButton(a, "Open App info", PillButton.Kind.PRIMARY, R.drawable.ic_external, false);
        open.setOnClickListener(v -> {
            s.close();
            a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + a.getPackageName())));
        });
        s.add(open, 22);
        s.show();
    }

    private static void step(Activity a, LinearLayout parent, String n, String text) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView num = Theme.text(a, n, Theme.Type.LABEL);
        num.setGravity(Gravity.CENTER);
        num.setTextColor(Theme.BLACK);
        num.setTypeface(Fonts.weight(a, 700));
        num.setBackground(Theme.rounded(Theme.WHITE, Theme.dp(a, 12)));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(Theme.dp(a, 24), Theme.dp(a, 24));
        np.rightMargin = Theme.dp(a, 14);
        row.addView(num, np);
        row.addView(Theme.text(a, text, Theme.Type.BODY_M));
        parent.addView(row, Theme.lp(Theme.MATCH, Theme.WRAP, parent.getChildCount() == 0 ? 0 : Theme.dp(a, 14)));
    }

    private Sheets() {}
}
