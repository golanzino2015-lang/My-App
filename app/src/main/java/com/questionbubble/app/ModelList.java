package com.questionbubble.app;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Every model from every AI, grouped like Claude's model menu, plus Think / Search switches. */
final class ModelList {

    interface Callback {
        void onPicked();
        void onNeedsKey(Provider p, String model);
    }

    static View build(Context c, boolean withToggles, Callback cb) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        Provider currentP = Prefs.provider(c);
        String currentM = Prefs.model(c, currentP);
        boolean first = true;
        for (Provider p : Provider.values()) {
            boolean hasKey = !Prefs.apiKey(c, p).isEmpty();
            LinearLayout head = new LinearLayout(c);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(Theme.dp(c, 6), 0, Theme.dp(c, 6), 0);
            head.addView(Theme.text(c, p.label + " · " + p.company, Theme.Type.OVERLINE),
                    new LinearLayout.LayoutParams(0, Theme.WRAP, 1));
            if (!hasKey) {
                TextView add = Theme.text(c, "Add key", Theme.Type.LABEL);
                add.setTextColor(Theme.TEXT);
                head.addView(add);
            }
            root.addView(head, Theme.lp(Theme.MATCH, Theme.WRAP, Theme.dp(c, first ? 4 : 20)));
            first = false;

            LinearLayout group = Rows.group(c);
            boolean customShown = false;
            for (String[] m : p.models) {
                boolean on = p == currentP && m[0].equals(currentM);
                Rows.add(group, row(c, p, m[0], Provider.modelLabel(m[0]), m[1], on, hasKey, cb));
            }
            String saved = Prefs.model(c, p);
            boolean known = false;
            for (String[] m : p.models) known |= m[0].equals(saved);
            if (!known) {
                boolean on = p == currentP;
                Rows.add(group, row(c, p, saved, saved, "Custom model", on, hasKey, cb));
            }
            root.addView(group, Theme.lp(Theme.MATCH, Theme.WRAP, Theme.dp(c, 8)));
        }

        if (withToggles) {
            root.addView(Theme.text(c, "Options", Theme.Type.OVERLINE), Theme.lp(Theme.MATCH, Theme.WRAP, Theme.dp(c, 22)));
            LinearLayout g = Rows.group(c);
            ToggleView think = new ToggleView(c, Prefs.thinking(c));
            think.setListener(on -> Prefs.setThinking(c, on));
            Rows.add(g, Rows.row(c, R.drawable.ic_bulb, "Extended thinking", "Think longer and show the reasoning", think, null));
            root.addView(g, Theme.lp(Theme.MATCH, Theme.WRAP, Theme.dp(c, 8)));
        }
        return root;
    }

    private static View row(Context c, Provider p, String id, String title, String sub, boolean on,
                            boolean hasKey, Callback cb) {
        View trailing = null;
        if (on) {
            FrameLayout f = new FrameLayout(c);
            f.setBackground(Theme.rounded(Theme.ACCENT, Theme.dp(c, 11)));
            f.addView(Theme.icon(c, R.drawable.ic_check, Theme.BLACK, 14));
            f.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(c, 22), Theme.dp(c, 22)));
            trailing = f;
        }
        LinearLayout r = Rows.row(c, 0, title, sub, trailing, () -> {
            if (!hasKey) {
                cb.onNeedsKey(p, id);
                return;
            }
            Prefs.setProvider(c, p);
            Prefs.setModel(c, p, id);
            cb.onPicked();
        });
        r.setMinimumHeight(Theme.dp(c, 58));
        if (!hasKey) r.setAlpha(0.55f);
        return r;
    }

    private ModelList() {}
}
