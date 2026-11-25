package com.questionbubble.app;

import android.app.Activity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** Shared bottom sheets and small controls. */
final class Sheets {

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

    static View radio(Activity a, boolean on) {
        FrameLayout f = new FrameLayout(a);
        f.setBackground(on ? Theme.rounded(Theme.ACCENT, Theme.dp(a, 11))
                : Theme.rounded(0x00000000, Theme.dp(a, 11), Theme.TEXT_3, Theme.dp(a, 1.5f)));
        if (on) f.addView(Theme.icon(a, R.drawable.ic_check, Theme.BLACK, 14));
        f.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(a, 22), Theme.dp(a, 22)));
        return f;
    }

    private Sheets() {}
}
