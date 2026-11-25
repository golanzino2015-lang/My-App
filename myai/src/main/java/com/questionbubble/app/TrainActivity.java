package com.questionbubble.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/** Create, train and teach My AI: the transformer that learns from scratch on this phone. */
public class TrainActivity extends Activity implements LocalAI.Listener {

    private LinearLayout content;
    private LocalAI ai;
    private boolean first = true;
    private String shownState = "";
    private LocalAI.Size pickedSize = LocalAI.Size.DAY;

    // live views
    private TextView status, statusSub, vWords, vSpeed, vTime, vLoss, sampleQ, sampleA;
    private PillButton trainBtn;
    private LossChart chart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setDecorFitsSystemWindows(false);
        Fonts.init(this);
        ai = LocalAI.get(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Theme.background(this));
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(10), dp(20), dp(36));
        scroll.addView(content);
        Theme.insets(scroll, true, true, true);
        setContentView(scroll);
        render();
        new Thread(ai::ensureLoaded).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ai.addListener(this);
        onUpdate();
    }

    @Override
    protected void onPause() {
        ai.removeListener(this);
        super.onPause();
    }

    private String state() {
        if (ai.busy() != null) return "busy:" + ai.busy();
        if (!ai.exists()) return "none:" + ai.error();
        return "brain:" + ai.lessons().size() + ":" + ai.backupSetUp() + ":" + ai.backupNote() + ":" + ai.lastBackup();
    }

    @Override
    public void onUpdate() {
        if (!state().equals(shownState)) render();
        else live();
    }

    // ---------------------------------------------------------------- screens

    private void render() {
        shownState = state();
        content.removeAllViews();
        int step = 0;
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        add(bar, 6, step++);

        if (ai.busy() != null) {
            add(Theme.text(this, "My AI", Theme.Type.DISPLAY), 22, step++);
            add(Theme.text(this, ai.busy(), Theme.Type.BODY), 10, step++);
            first = false;
            return;
        }
        if (!ai.exists()) renderCreate(step);
        else renderBrain(step);
        first = false;
    }

    private void renderCreate(int step) {
        add(Theme.text(this, "My AI", Theme.Type.DISPLAY), 22, step++);
        add(Theme.text(this, "Build an AI from nothing. It starts as random numbers that know no words at all, "
                + "then learns to talk by training on example chats, right here on your phone.", Theme.Type.BODY), 10, step++);
        if (ai.error() != null) {
            TextView err = Theme.text(this, ai.error(), Theme.Type.CAPTION);
            err.setTextColor(0xFFFF8A80);
            add(err, 12, step++);
        }

        add(Rows.section(this, "Name"), 28, step++);
        EditText name = field("Pip");
        name.setText("Pip");
        add(name, 10, step++);

        add(Rows.section(this, "Brain size"), 28, step++);
        LinearLayout sizes = Rows.group(this);
        for (LocalAI.Size s : LocalAI.Size.values()) {
            View radio = Sheets.radio(this, s == pickedSize);
            Rows.add(sizes, Rows.row(this, 0, s.label, s.about, radio, () -> {
                pickedSize = s;
                render();
            }));
        }
        add(sizes, 10, step++);

        PillButton restore = new PillButton(this, "Restore from cloud", PillButton.Kind.SECONDARY);
        restore.setOnClickListener(v -> backupSheet(true));

        PillButton create = new PillButton(this, "Create brain", PillButton.Kind.PRIMARY);
        create.setOnClickListener(v -> {
            create.setLoading(true);
            String n = name.getText().toString();
            new Thread(() -> ai.create(n, pickedSize)).start();
        });
        add(create, 28, step++);
        add(restore, 10, step++);
    }

    private void renderBrain(int step) {
        add(Theme.text(this, ai.name(), Theme.Type.DISPLAY), 22, step++);
        add(Theme.text(this, String.format(Locale.US, "%s brain · %s numbers · %,d words · %d cores",
                ai.size().label, compact(ai.params()), ai.words(), ai.threads()), Theme.Type.BODY), 8, step++);
        if (ai.error() != null) {
            TextView err = Theme.text(this, ai.error(), Theme.Type.CAPTION);
            err.setTextColor(0xFFFF8A80);
            add(err, 10, step++);
        }

        // ---- training card
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        hero.setBackground(Theme.rounded(Theme.SURFACE, dp(28), Theme.HAIRLINE, Math.max(1, dp(0.8f))));
        status = Theme.text(this, "", Theme.Type.TITLE);
        hero.addView(status);
        statusSub = Theme.text(this, "", Theme.Type.CAPTION);
        hero.addView(statusSub, Theme.lp(Theme.MATCH, Theme.WRAP, dp(5)));
        trainBtn = new PillButton(this, "Start training", PillButton.Kind.PRIMARY);
        trainBtn.setOnClickListener(v -> toggleTraining());
        hero.addView(trainBtn, Theme.lp(Theme.MATCH, Theme.WRAP, dp(18)));
        add(hero, 22, step++);

        // ---- numbers
        LinearLayout row1 = new LinearLayout(this), row2 = new LinearLayout(this);
        vWords = Theme.text(this, "", Theme.Type.HEADLINE);
        vSpeed = Theme.text(this, "", Theme.Type.HEADLINE);
        vTime = Theme.text(this, "", Theme.Type.HEADLINE);
        vLoss = Theme.text(this, "", Theme.Type.HEADLINE);
        pair(row1, tile(vWords, "Words read"), tile(vSpeed, "Words per second"));
        pair(row2, tile(vTime, "Time trained"), tile(vLoss, "Mistakes per word"));
        add(row1, 10, step++);
        add(row2, 10, step++);

        // ---- chart
        LinearLayout chartCard = new LinearLayout(this);
        chartCard.setOrientation(LinearLayout.VERTICAL);
        chartCard.setPadding(dp(16), dp(16), dp(16), dp(12));
        chartCard.setBackground(Theme.card(this));
        chartCard.addView(Theme.text(this, "Mistakes per word · lower is smarter", Theme.Type.OVERLINE));
        chart = new LossChart(this);
        chartCard.addView(chart, new LinearLayout.LayoutParams(Theme.MATCH, dp(170)));
        add(chartCard, 10, step++);

        // ---- latest reply
        LinearLayout said = new LinearLayout(this);
        said.setOrientation(LinearLayout.VERTICAL);
        said.setPadding(dp(16), dp(16), dp(16), dp(16));
        said.setBackground(Theme.card(this));
        said.addView(Theme.text(this, "Latest reply while training", Theme.Type.OVERLINE));
        sampleQ = Theme.text(this, "", Theme.Type.CAPTION);
        said.addView(sampleQ, Theme.lp(Theme.MATCH, Theme.WRAP, dp(10)));
        sampleA = Theme.text(this, "", Theme.Type.BODY_M);
        said.addView(sampleA, Theme.lp(Theme.MATCH, Theme.WRAP, dp(6)));
        add(said, 10, step++);

        // ---- talk
        PillButton chat = new PillButton(this, "Chat with " + ai.name(), PillButton.Kind.SECONDARY, R.drawable.ic_message, false);
        chat.setOnClickListener(v -> {
            startActivity(new Intent(this, ChatActivity.class));
            overridePendingTransition(R.anim.screen_in, R.anim.screen_out);
        });
        add(chat, 14, step++);

        // ---- teach
        List<String[]> lessons = ai.lessons();
        add(Rows.section(this, "Your lessons"), 30, step++);
        LinearLayout teach = Rows.group(this);
        Rows.add(teach, Rows.row(this, R.drawable.ic_plus, "Teach a new answer",
                "When you say this, it answers that", Rows.value(this, null), this::teachSheet));
        for (int i = 0; i < lessons.size(); i++) {
            String[] l = lessons.get(i);
            int idx = i;
            View del = Rows.iconButton(this, R.drawable.ic_trash, () -> {
                List<String[]> next = ai.lessons();
                next.remove(idx);
                new Thread(() -> ai.setLessons(next)).start();
            });
            Rows.add(teach, Rows.row(this, 0, "“" + l[0] + "”", l[1], del, null));
        }
        add(teach, 10, step++);

        // ---- keep it running
        add(Rows.section(this, "Brain"), 30, step++);
        LinearLayout more = Rows.group(this);
        PowerManager pm = getSystemService(PowerManager.class);
        boolean free = pm.isIgnoringBatteryOptimizations(getPackageName());
        Rows.add(more, Rows.row(this, R.drawable.ic_shield, "Train with the screen off",
                free ? "Allowed. Training keeps going in the background" : "Tap to stop Android from pausing training",
                Rows.value(this, free ? "On" : "Off"), () -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Exception e) {
                        startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                    }
                }));
        String bsub;
        if (!ai.backupSetUp()) bsub = "Keep a copy online, so a reinstall doesn't lose it";
        else if (!ai.backupNote().isEmpty()) bsub = ai.backupNote();
        else if (ai.lastBackup() > 0) bsub = "Last backup " + ago(ai.lastBackup()) + " · every 30 min on Wi-Fi";
        else bsub = "Backs up every 30 minutes on Wi-Fi while training";
        Rows.add(more, Rows.row(this, R.drawable.ic_shield, "Cloud backup", bsub,
                Rows.value(this, ai.backupSetUp() ? "On" : "Off"), () -> backupSheet(false)));
        Rows.add(more, Rows.row(this, R.drawable.ic_trash, "Delete brain", "Erases everything it learned",
                null, () -> Sheets.confirm(this, "Delete " + ai.name() + "?",
                        "Everything it learned will be erased. This can't be undone.", "Delete brain", () -> {
                            TrainService.stop(this);
                            new Thread(ai::delete).start();
                        })));
        add(more, 10, step++);

        live();
    }

    /** Updates the numbers without rebuilding the screen. */
    private void live() {
        if (status == null || !ai.exists()) return;
        boolean on = ai.isTraining();
        status.setText(on ? "Training" : ai.step() == 0 ? "Ready to train" : "Paused");
        statusSub.setText(on ? "Keeps going with the screen off. Plug in your phone for long sessions."
                : ai.step() == 0 ? "It knows nothing yet. Start training and watch it learn."
                : String.format(Locale.US, "%,d learning steps so far", ai.step()));
        trainBtn.setText(on ? "Pause training" : ai.step() == 0 ? "Start training" : "Continue training");
        vWords.setText(compact(ai.tokens()));
        vSpeed.setText(on ? String.format(Locale.US, "%,.0f", ai.speed()) : "–");
        vTime.setText(duration(ai.seconds()));
        float loss = ai.lastLoss();
        vLoss.setText(Float.isNaN(loss) ? "–" : String.format(Locale.US, "%.2f", loss));
        sampleQ.setText(ai.sampleQ().isEmpty() ? "Appears after a few minutes of training" : "You: " + ai.sampleQ());
        sampleA.setText(ai.sampleA());
        chart.setData(ai.lossHistory());
    }

    private void toggleTraining() {
        if (ai.isTraining()) {
            TrainService.stop(this);
            trainBtn.setLoading(true);
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        TrainService.start(this);
    }

    private void teachSheet() {
        Sheet s = new Sheet(this);
        s.title("Teach " + ai.name(), "Short, simple sentences work best. Train for a few minutes afterwards so it sinks in.");
        EditText q = field("When I say… (what is my dog called)");
        EditText a = field("It answers… (your dog is called rex.)");
        s.add(q, 18);
        s.add(a, 10);
        PillButton add = new PillButton(this, "Add lesson", PillButton.Kind.PRIMARY);
        add.setOnClickListener(v -> {
            String qs = q.getText().toString().trim(), as = a.getText().toString().trim();
            if (qs.isEmpty() || as.isEmpty()) {
                Theme.shake(add);
                return;
            }
            s.close();
            List<String[]> next = ai.lessons();
            next.add(new String[]{qs, as});
            new Thread(() -> ai.setLessons(next)).start();
            Snack.show(this, "Lesson added", R.drawable.ic_check);
        });
        s.add(add, 18);
        s.show();
    }

    /** Set up cloud backup (restore = false) or bring a brain back from the cloud (restore = true). */
    private void backupSheet(boolean restore) {
        Sheet s = new Sheet(this);
        s.title(restore ? "Restore from cloud" : "Cloud backup",
                "Saved in your own private GitHub repository. Enter its name and your access key "
                        + "(see the setup steps). Only you can see it.");
        EditText repo = field("Repository, like my-ai-brain");
        repo.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        repo.setText(Prefs.cloudRepo(this).isEmpty() ? "my-ai-brain" : Prefs.cloudRepo(this));
        EditText key = field("Access key (github_pat_…)");
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setText(Prefs.cloudToken(this));
        TextView msg = Theme.text(this, "", Theme.Type.CAPTION);
        s.add(repo, 18);
        s.add(key, 10);
        s.add(msg, 10);
        PillButton go = new PillButton(this, restore ? "Restore brain" : "Connect and back up now", PillButton.Kind.PRIMARY);
        go.setOnClickListener(v -> {
            String r0 = repo.getText().toString(), k = key.getText().toString().trim();
            if (r0.trim().isEmpty() || k.isEmpty()) {
                Theme.shake(go);
                return;
            }
            go.setLoading(true);
            msg.setTextColor(Theme.TEXT_2);
            msg.setText(restore ? "Downloading the brain…" : "Connecting…");
            new Thread(() -> {
                String result;
                try {
                    String r = Backup.normalize(r0, k);
                    org.json.JSONObject h = Backup.hello(r, k);
                    if (restore && !h.optBoolean("has_brain")) throw new Exception("No brain is saved in the cloud yet");
                    Prefs.setCloud(this, r, k);
                    if (restore) ai.restore(r, k);
                    else ai.backupNow(true);
                    result = null;
                } catch (Exception e) {
                    result = Backup.explain(e);
                }
                String res = result;
                runOnUiThread(() -> {
                    go.setLoading(false);
                    if (res != null) {
                        msg.setText(res);
                        msg.setTextColor(0xFFFF8A80);
                        return;
                    }
                    s.close();
                    Snack.show(this, restore ? "Brain restored" : "Connected. Backing up now", R.drawable.ic_check);
                    render();
                });
            }).start();
        });
        s.add(go, 16);
        if (!restore && ai.backupSetUp()) {
            PillButton off = new PillButton(this, "Turn off cloud backup", PillButton.Kind.GHOST);
            off.setOnClickListener(v -> {
                Prefs.setCloud(this, "", "");
                s.close();
                render();
            });
            s.add(off, 6);
        }
        s.show();
    }

    static String ago(long t) {
        long m = (System.currentTimeMillis() - t) / 60000;
        return m < 1 ? "just now" : m < 60 ? m + " min ago" : (m / 60) + " h ago";
    }

    // ---------------------------------------------------------------- pieces

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Theme.TEXT_3);
        e.setTextColor(Theme.TEXT);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setTypeface(Fonts.weight(this, 400));
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setPadding(dp(16), dp(14), dp(16), dp(14));
        e.setBackground(Theme.rounded(Theme.FILL, dp(16), Theme.HAIRLINE, Math.max(1, dp(0.8f))));
        return e;
    }

    private View tile(TextView value, String label) {
        LinearLayout s = new LinearLayout(this);
        s.setOrientation(LinearLayout.VERTICAL);
        s.setPadding(dp(18), dp(16), dp(18), dp(16));
        s.setBackground(Theme.card(this));
        value.setFontFeatureSettings("tnum");
        Theme.oneLine(value);
        s.addView(value);
        s.addView(Theme.text(this, label, Theme.Type.CAPTION), Theme.lp(Theme.WRAP, Theme.WRAP, dp(4)));
        return s;
    }

    private void pair(LinearLayout row, View a, View b) {
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(a, new LinearLayout.LayoutParams(0, Theme.WRAP, 1));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Theme.WRAP, 1);
        p.leftMargin = dp(10);
        row.addView(b, p);
    }

    static String compact(long n) {
        if (n >= 1_000_000_000L) return String.format(Locale.US, "%.1fB", n / 1e9);
        if (n >= 1_000_000L) return String.format(Locale.US, "%.1fM", n / 1e6);
        if (n >= 10_000L) return String.format(Locale.US, "%.0fk", n / 1e3);
        return String.format(Locale.US, "%,d", n);
    }

    static String duration(double s) {
        long m = (long) (s / 60);
        return m >= 60 ? (m / 60) + "h " + (m % 60) + "m" : m + "m " + ((long) s % 60) + "s";
    }

    private void add(View v, int topDp, int index) {
        content.addView(v, Theme.lp(Theme.MATCH, Theme.WRAP, dp(topDp)));
        if (first) Theme.enter(v, 35L * Math.min(index, 10));
    }

    private int dp(float v) {
        return Theme.dp(this, v);
    }

    /** Line chart of training loss over learning steps. */
    static final class LossChart extends View {
        private float[][] data = new float[0][];
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG),
                grid = new Paint(), label = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path(), area = new Path();

        LossChart(Context c) {
            super(c);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(Theme.dp(c, 2));
            line.setStrokeJoin(Paint.Join.ROUND);
            line.setColor(Theme.ACCENT);
            fill.setColor(Theme.ACCENT);
            fill.setAlpha(28);
            dot.setColor(Theme.ACCENT);
            grid.setColor(Theme.HAIRLINE);
            grid.setStrokeWidth(Math.max(1, Theme.dp(c, 0.8f)));
            label.setColor(Theme.TEXT_3);
            label.setTextSize(Theme.dp(c, 11));
            label.setTypeface(Fonts.weight(c, 500));
        }

        void setData(float[][] d) {
            data = d;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), left = Theme.dp(getContext(), 22), top = Theme.dp(getContext(), 12),
                    bottom = h - Theme.dp(getContext(), 18);
            float max = 8;
            for (float[] p : data) max = Math.max(max, (float) Math.ceil(p[1]));
            for (int i = 0; i <= 4; i++) {
                float v = max * i / 4f, y = bottom - (bottom - top) * (v / max);
                c.drawLine(left, y, w, y, grid);
                c.drawText(String.format(Locale.US, "%.0f", v), 0, y + Theme.dp(getContext(), 4), label);
            }
            if (data.length < 2) {
                c.drawText("Draws once training starts", left + Theme.dp(getContext(), 8), (top + bottom) / 2, label);
                return;
            }
            float maxX = data[data.length - 1][0], minX = data[0][0];
            path.reset();
            area.reset();
            for (int i = 0; i < data.length; i++) {
                float x = left + (w - left - Theme.dp(getContext(), 6)) * (data[i][0] - minX) / Math.max(1, maxX - minX);
                float y = bottom - (bottom - top) * Math.min(1, data[i][1] / max);
                if (i == 0) { path.moveTo(x, y); area.moveTo(x, bottom); }
                path.lineTo(x, y);
                area.lineTo(x, y);
                if (i == data.length - 1) {
                    area.lineTo(x, bottom);
                    area.close();
                    c.drawPath(area, fill);
                    c.drawPath(path, line);
                    c.drawCircle(x, y, Theme.dp(getContext(), 3.5f), dot);
                }
            }
            String end = "step " + compact((long) maxX);
            c.drawText(end, w - label.measureText(end), h - Theme.dp(getContext(), 2), label);
        }
    }
}
