package com.questionbubble.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.Executors;

/** The app's bottom sheets. */
final class Sheets {

    /** Edit + verify the API key. */
    static void apiKey(Activity a, Runnable onSaved) {
        Sheet s = new Sheet(a);
        s.title("OpenAI API key", "Stored only on this phone. Sent only to OpenAI when you ask a question.");
        KeyField field = new KeyField(a);
        field.setText(Prefs.apiKey(a));
        s.add(field, 20);
        PillButton save = new PillButton(a, "Verify & save", PillButton.Kind.PRIMARY);
        s.add(save, 18);
        Handler main = new Handler(Looper.getMainLooper());
        save.setOnClickListener(v -> {
            String key = field.text();
            save.setLoading(true);
            Executors.newSingleThreadExecutor().execute(() -> {
                String err = null;
                try {
                    OpenAiClient.verifyKey(key);
                } catch (Exception e) {
                    err = e.getMessage();
                }
                String error = err;
                main.post(() -> {
                    save.setLoading(false);
                    if (error != null) {
                        field.setError(error);
                    } else {
                        Prefs.setApiKey(a, key);
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

    /** Choose the model. */
    static void model(Activity a, Runnable onChanged) {
        Sheet s = new Sheet(a);
        s.title("Model", "Must support images. Faster models answer quicker; larger ones are more accurate.");
        String[][] options = {
                {"gpt-4o", "Balanced. Great with screenshots"},
                {"gpt-4o-mini", "Fastest and cheapest"},
                {"gpt-4.1", "Strong reasoning"},
                {"gpt-5-mini", "Newer, efficient"},
                {"gpt-5", "Most capable, slower"},
        };
        String current = Prefs.model(a);
        LinearLayout group = Rows.group(a);
        for (String[] o : options) {
            View radio = radio(a, o[0].equals(current));
            Rows.add(group, Rows.row(a, 0, o[0], o[1], radio, () -> {
                Prefs.setModel(a, o[0]);
                Theme.tick(radio);
                s.close();
                if (onChanged != null) onChanged.run();
            }));
        }
        s.add(group, 18);
        s.show();
    }

    private static View radio(Activity a, boolean on) {
        FrameLayout f = new FrameLayout(a);
        f.setBackground(on ? Theme.rounded(Theme.WHITE, Theme.dp(a, 11))
                : Theme.rounded(0x00000000, Theme.dp(a, 11), Theme.TEXT_3, Theme.dp(a, 1.5f)));
        if (on) f.addView(Theme.icon(a, R.drawable.ic_check, Theme.BLACK, 14));
        f.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(a, 22), Theme.dp(a, 22)));
        return f;
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
