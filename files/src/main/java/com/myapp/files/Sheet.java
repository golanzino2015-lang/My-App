package com.myapp.files;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Bottom sheet that slides up over a dimmed scrim, plus the menus and prompts built on it. */
class Sheet extends Dialog {
    final Activity act;
    final FrameLayout root;
    final View scrim;
    final LinearLayout panel;
    boolean closing, dismissible = true;

    Sheet(Activity a) {
        super(a, R.style.Sheet);
        act = a;
        Context c = a;
        root = new FrameLayout(c);
        scrim = new View(c);
        scrim.setBackgroundColor(0x59000000);
        scrim.setOnClickListener(v -> {
            if (dismissible) dismiss();
        });
        root.addView(scrim, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        panel = Ui.col(c);
        float r = Ui.dp(c, 30);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFFFFFFF, 0xFFFAFAFB, 0xFFF3F3F6});
        g.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        g.setStroke(Ui.stroke(c), Ui.LINE);
        panel.setBackground(g);
        panel.setPadding(Ui.dp(c, 20), Ui.dp(c, 10), Ui.dp(c, 20), Ui.dp(c, 22));
        panel.setClickable(true);
        View handle = new View(c);
        handle.setBackground(Ui.rounded(0xFFCFCFD5, Ui.dp(c, 3)));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 5));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.bottomMargin = Ui.dp(c, 14);
        panel.addView(handle, hp);
        FrameLayout.LayoutParams lp = Ui.flp(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM);
        lp.topMargin = Ui.dp(c, 56);
        lp.leftMargin = lp.rightMargin = -Ui.stroke(c);
        lp.bottomMargin = -Ui.dp(c, 2); // hide the bottom stroke
        root.addView(panel, lp);
        setContentView(root);
        Ui.insets(panel, false, true, true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Window w = getWindow();
        if (w != null) {
            w.setLayout(Ui.MATCH, Ui.MATCH);
            w.setDecorFitsSystemWindows(false);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(260).start();
        panel.setTranslationY(Ui.dp(getContext(), 600));
        panel.post(() -> {
            panel.setTranslationY(panel.getHeight());
            panel.animate().translationY(0f).setDuration(460).setInterpolator(Ui.DECELERATE).start();
        });
    }

    @Override
    public void onBackPressed() {
        if (dismissible) dismiss();
    }

    @Override
    public void dismiss() {
        if (closing) return;
        closing = true;
        View f = getCurrentFocus();
        if (f != null) {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
        }
        scrim.animate().alpha(0f).setDuration(220).start();
        panel.animate().translationY(panel.getHeight() + Ui.dp(getContext(), 24)).setDuration(240)
                .setInterpolator(Ui.ACCELERATE).withEndAction(() -> {
                    try {
                        Sheet.super.dismiss();
                    } catch (Exception ignored) {
                    }
                }).start();
    }

    void dismissNow() {
        closing = true;
        try {
            super.dismiss();
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- building blocks
    Sheet title(String title, String subtitle) {
        Context c = getContext();
        TextView t = Ui.oneLine(Ui.text(c, title, Ui.Type.TITLE));
        panel.addView(t);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = Ui.middle(Ui.text(c, subtitle, Ui.Type.CAPTION));
            panel.addView(s, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 6)));
        }
        return this;
    }

    /** Header with a leading tile (icon or thumbnail). */
    Sheet header(View tile, String title, String subtitle) {
        Context c = getContext();
        LinearLayout row = Ui.row(c);
        row.addView(tile, new LinearLayout.LayoutParams(Ui.dp(c, 52), Ui.dp(c, 52)));
        LinearLayout col = Ui.col(c);
        col.setPadding(Ui.dp(c, 14), 0, 0, 0);
        col.addView(Ui.middle(Ui.text(c, title, Ui.Type.TITLE_S)));
        if (subtitle != null) {
            TextView s = Ui.oneLine(Ui.text(c, subtitle, Ui.Type.CAPTION));
            col.addView(s, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 5)));
        }
        row.addView(col, Ui.weight(Ui.WRAP));
        panel.addView(row);
        return this;
    }

    static final class Action {
        final int icon;
        final String label;
        final Runnable run;
        boolean danger, checked, keepOpen;

        Action(int icon, String label, Runnable run) {
            this.icon = icon;
            this.label = label;
            this.run = run;
        }

        Action danger() {
            danger = true;
            return this;
        }

        Action checked(boolean v) {
            checked = v;
            return this;
        }
    }

    /** A card of tappable rows. */
    Sheet actions(List<Action> actions) {
        Context c = getContext();
        LinearLayout card = Ui.col(c);
        card.setBackground(Ui.card(c, 22));
        card.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4));
        for (int k = 0; k < actions.size(); k++) {
            Action a = actions.get(k);
            if (k > 0) {
                View d = new View(c);
                d.setBackgroundColor(Ui.LINE_SOFT);
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.MATCH, Math.max(1, Ui.dp(c, 0.7f)));
                dl.leftMargin = Ui.dp(c, 64);
                card.addView(d, dl);
            }
            LinearLayout row = Ui.row(c);
            row.setPadding(Ui.dp(c, 12), Ui.dp(c, 9), Ui.dp(c, 16), Ui.dp(c, 9));
            row.setBackground(Ui.ripple(null, Ui.dp(c, 18)));
            int color = a.danger ? Ui.DANGER : Ui.INK;
            FrameLayout tile = new FrameLayout(c);
            tile.setBackground(Ui.tile(c, 38));
            if (a.danger) ((GradientDrawable) tile.getBackground()).setStroke(Ui.stroke(c), Ui.DANGER);
            tile.addView(Ui.icon(c, a.icon, color, 19));
            row.addView(tile, new LinearLayout.LayoutParams(Ui.dp(c, 38), Ui.dp(c, 38)));
            TextView t = Ui.text(c, a.label, Ui.Type.BODY_M);
            t.setTextColor(color);
            t.setPadding(Ui.dp(c, 14), 0, 0, 0);
            row.addView(t, Ui.weight(Ui.WRAP));
            if (a.checked) {
                ImageView ck = Ui.icon(c, R.drawable.ic_check, Ui.INK, 20);
                row.addView(ck, new LinearLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20)));
            }
            row.setOnClickListener(v -> {
                Ui.tap(v);
                if (!a.keepOpen) dismiss();
                a.run.run();
            });
            card.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        ScrollView sv = new ScrollView(c);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(card);
        LinearLayout.LayoutParams lp = Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 18));
        panel.addView(sv, lp);
        return this;
    }

    Sheet buttons(String secondary, Runnable onSecondary, String primary, Runnable onPrimary) {
        Context c = getContext();
        LinearLayout row = Ui.row(c);
        if (secondary != null) {
            TextView s = Ui.pill(c, secondary, false, v -> {
                dismiss();
                if (onSecondary != null) onSecondary.run();
            });
            row.addView(s, Ui.weight(Ui.dp(c, 52)));
        }
        if (primary != null) {
            if (secondary != null) row.addView(Ui.space(c, 10, 1));
            TextView p = Ui.pill(c, primary, true, v -> {
                if (onPrimary != null) onPrimary.run();
            });
            row.addView(p, Ui.weight(Ui.dp(c, 52)));
        }
        panel.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 22)));
        return this;
    }

    /** Label / value pairs in a card. */
    Sheet details(String[][] rows) {
        Context c = getContext();
        LinearLayout card = Ui.col(c);
        card.setBackground(Ui.card(c, 22));
        card.setPadding(Ui.dp(c, 18), Ui.dp(c, 6), Ui.dp(c, 18), Ui.dp(c, 6));
        for (int k = 0; k < rows.length; k++) {
            if (k > 0) {
                View d = new View(c);
                d.setBackgroundColor(Ui.LINE_SOFT);
                card.addView(d, new LinearLayout.LayoutParams(Ui.MATCH, Math.max(1, Ui.dp(c, 0.7f))));
            }
            LinearLayout row = Ui.col(c);
            row.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 12));
            row.addView(Ui.text(c, rows[k][0], Ui.Type.OVERLINE));
            TextView v = Ui.text(c, rows[k][1], Ui.Type.CALLOUT);
            v.setTextIsSelectable(true);
            row.addView(v, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 6)));
            row.setTag(rows[k][0]);
            card.addView(row);
        }
        ScrollView sv = new ScrollView(c);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(card);
        panel.addView(sv, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 18)));
        return this;
    }

    // ---------------------------------------------------------------- ready-made sheets
    interface Input {
        /** Return an error message to keep the sheet open, or null to close it. */
        String submit(String text);
    }

    static void input(Activity a, String title, String initial, String hint, String button, boolean selectBase, Input done) {
        Sheet s = new Sheet(a);
        Context c = a;
        s.title(title, null);
        EditText e = new EditText(c);
        Ui.style(e, Ui.Type.BODY_M);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setHint(hint);
        e.setHintTextColor(Ui.INK_3);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        e.setBackground(Ui.card(c, 18));
        e.setPadding(Ui.dp(c, 18), 0, Ui.dp(c, 18), 0);
        e.setText(initial);
        s.panel.addView(e, Ui.lp(Ui.MATCH, Ui.dp(c, 56), Ui.dp(c, 18)));
        TextView err = Ui.text(c, "", Ui.Type.CAPTION);
        err.setTextColor(Ui.DANGER);
        err.setVisibility(View.GONE);
        s.panel.addView(err, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 10)));
        Runnable submit = () -> {
            String v = e.getText().toString().trim();
            String bad = v.isEmpty() ? "Enter a name" : v.contains("/") ? "Names can't contain /"
                    : v.equals(".") || v.equals("..") ? "That name isn't allowed" : done.submit(v);
            if (bad == null) {
                s.dismiss();
            } else {
                err.setText(bad);
                err.setVisibility(View.VISIBLE);
                Ui.shake(e);
            }
        };
        e.setOnEditorActionListener((v, id, ev) -> {
            submit.run();
            return true;
        });
        s.buttons("Cancel", null, button, submit);
        s.show();
        e.requestFocus();
        if (initial != null) {
            int dot = initial.lastIndexOf('.');
            e.setSelection(0, selectBase && dot > 0 ? dot : initial.length());
        }
        e.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(e, InputMethodManager.SHOW_IMPLICIT);
        }, 200);
    }

    static void confirm(Activity a, String title, String message, String button, Runnable yes) {
        Sheet s = new Sheet(a);
        s.title(title, null);
        TextView m = Ui.text(a, message, Ui.Type.BODY);
        s.panel.addView(m, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(a, 10)));
        s.buttons("Cancel", null, button, () -> {
            s.dismiss();
            yes.run();
        });
        s.show();
    }

    /** Progress for long jobs (copy, move, delete, zip). */
    static final class Busy implements Fs.Progress {
        final Sheet sheet;
        final TextView detail;
        final Meter meter;
        volatile boolean cancelled;
        private int done;
        private volatile int total;
        private long lastPost;

        Busy(Activity a, String title) {
            sheet = new Sheet(a);
            sheet.dismissible = false;
            sheet.setCancelable(false);
            sheet.title(title, null);
            detail = Ui.middle(Ui.text(a, "Starting…", Ui.Type.CAPTION));
            sheet.panel.addView(detail, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(a, 8)));
            meter = new Meter(a, false);
            sheet.panel.addView(meter, Ui.lp(Ui.MATCH, Ui.dp(a, 12), Ui.dp(a, 18)));
            meter.indeterminate();
            sheet.buttons(null, null, "Cancel", () -> {
                cancelled = true;
                detail.setText("Stopping…");
            });
            sheet.show();
        }

        /** Number of steps (files) the job will report; until set, the bar just sweeps. */
        void total(int n) {
            total = n;
        }

        @Override
        public void step(String name) {
            done++;
            long now = System.currentTimeMillis();
            if (now - lastPost < 60) return;
            lastPost = now;
            final int d = done;
            Bg.main(() -> {
                if (!cancelled) detail.setText(name);
                if (total > 0 && d <= total) meter.set(d / (float) total, true);
            });
        }

        @Override
        public boolean cancelled() {
            return cancelled;
        }

        void close() {
            sheet.dismiss();
        }
    }
}
