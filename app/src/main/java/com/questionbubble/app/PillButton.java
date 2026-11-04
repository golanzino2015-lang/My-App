package com.questionbubble.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Full-pill button with primary / secondary / ghost styles, optional icon and a loading state. */
@SuppressLint("ViewConstructor")
final class PillButton extends FrameLayout {

    enum Kind { PRIMARY, SECONDARY, GHOST }

    private final LinearLayout content;
    private final TextView label;
    private final Spinner spinner;
    private ImageView iconView;
    private boolean loading;

    PillButton(Context c, String text, Kind kind) {
        this(c, text, kind, 0, false);
    }

    PillButton(Context c, String text, Kind kind, int iconRes, boolean small) {
        super(c);
        int h = Theme.dp(c, small ? 42 : 56);
        setMinimumHeight(h);
        int fg = kind == Kind.PRIMARY ? Theme.BLACK : (kind == Kind.GHOST ? Theme.TEXT_2 : Theme.TEXT);

        android.graphics.drawable.GradientDrawable bg;
        if (kind == Kind.PRIMARY) bg = Theme.rounded(Theme.WHITE, h / 2f);
        else if (kind == Kind.SECONDARY) bg = Theme.rounded(Theme.FILL, h / 2f, Theme.HAIRLINE, Math.max(1, Theme.dp(c, 0.8f)));
        else bg = Theme.rounded(0x00000000, h / 2f);
        setBackground(new RippleDrawable(ColorStateList.valueOf(kind == Kind.PRIMARY ? 0x1F000000 : 0x1AFFFFFF),
                bg, Theme.rounded(Theme.WHITE, h / 2f)));

        content = new LinearLayout(c);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER);
        if (iconRes != 0) {
            iconView = new ImageView(c);
            iconView.setImageResource(iconRes);
            iconView.setImageTintList(ColorStateList.valueOf(fg));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Theme.dp(c, 18), Theme.dp(c, 18));
            ip.rightMargin = Theme.dp(c, 8);
            content.addView(iconView, ip);
        }
        label = Theme.text(c, text, small ? Theme.Type.CALLOUT : Theme.Type.BODY_M);
        label.setTypeface(Fonts.weight(c, 600));
        label.setTextColor(fg);
        content.addView(label);
        int padH = Theme.dp(c, small ? 18 : 24);
        content.setPadding(padH, 0, padH, 0);
        addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, h, Gravity.CENTER));

        spinner = new Spinner(c, fg);
        spinner.setAlpha(0f);
        spinner.setVisibility(GONE);
        addView(spinner, new LayoutParams(Theme.dp(c, 22), Theme.dp(c, 22), Gravity.CENTER));

        setClickable(true);
        setFocusable(true);
        Theme.pressable(this, 0.965f);
    }

    void setText(String t) {
        label.setText(t);
    }

    @Override
    public void setOnClickListener(OnClickListener l) {
        super.setOnClickListener(l == null ? null : v -> {
            if (loading) return;
            Theme.tap(v);
            l.onClick(v);
        });
    }

    void setLoading(boolean on) {
        if (loading == on) return;
        loading = on;
        if (on) {
            spinner.setVisibility(VISIBLE);
            spinner.animate().alpha(1f).setDuration(200).start();
            content.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(160).start();
        } else {
            spinner.animate().alpha(0f).setDuration(140).withEndAction(() -> spinner.setVisibility(GONE)).start();
            content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260)
                    .setInterpolator(Theme.EMPHASIZED).start();
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        animate().alpha(enabled ? 1f : 0.35f).setDuration(200).start();
    }

    View labelView() {
        return label;
    }
}
