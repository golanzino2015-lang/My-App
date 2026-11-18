package com.myapp.files;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** App manager: every installed app with open, info, uninstall, extract and share. */
final class AppsScreen extends Screen {
    private static final int FILTER_USER = 0, FILTER_SYSTEM = 1, FILTER_ALL = 2;

    static final class App {
        ApplicationInfo ai;
        String label, pkg, version;
        long size, installed, updated;
        boolean system;
    }

    private final List<App> all = new ArrayList<>();
    private final List<App> shown = new ArrayList<>();
    private final Adapter adapter = new Adapter();
    private ListView list;
    private TextView subtitle;
    private final TextView[] chips = new TextView[3];
    private LinearLayout emptyBox;
    private int filter = FILTER_USER;
    private String query = "";
    private EditText field;
    private int token;
    private boolean loaded;

    AppsScreen(MainActivity a) {
        super(a);
    }

    @Override
    int tab() {
        return MainActivity.TAB_APPS;
    }

    @Override
    View build() {
        FrameLayout frame = new FrameLayout(c);
        LinearLayout col = Ui.col(c);
        col.setPadding(0, Ui.dp(c, 14), 0, 0);
        Ui.insets(col, true, false, false);

        LinearLayout head = Ui.row(c);
        head.setPadding(Ui.dp(c, 20), 0, Ui.dp(c, 16), 0);
        LinearLayout titles = Ui.col(c);
        titles.addView(Ui.text(c, "App manager", Ui.Type.OVERLINE));
        titles.addView(Ui.text(c, "Apps", Ui.Type.DISPLAY), Ui.lp(Ui.WRAP, Ui.WRAP, Ui.dp(c, 8)));
        subtitle = Ui.oneLine(Ui.text(c, "Loading…", Ui.Type.CAPTION));
        titles.addView(subtitle, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 6)));
        head.addView(titles, Ui.weight(Ui.WRAP));
        head.addView(Ui.circleButton(c, R.drawable.ic_sort, 48, v -> sortMenu()));
        col.addView(head);

        // search
        LinearLayout box = Ui.row(c);
        box.setBackground(Ui.card(c, 22));
        box.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 6), 0);
        box.addView(Ui.icon(c, R.drawable.ic_search, Ui.INK, 20), new LinearLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20)));
        field = new EditText(c);
        Ui.style(field, Ui.Type.BODY_M);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        field.setHint("Search apps");
        field.setHintTextColor(Ui.INK_3);
        field.setBackground(null);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        field.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 4), 0);
        box.addView(field, Ui.weight(Ui.dp(c, 52)));
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a1, int b, int d) {}

            @Override
            public void onTextChanged(CharSequence s, int a1, int b, int d) {}

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                apply(false);
            }
        });
        field.setOnEditorActionListener((v, id, e) -> {
            hideKeyboard();
            return true;
        });
        LinearLayout.LayoutParams bp = Ui.lp(Ui.MATCH, Ui.dp(c, 54), Ui.dp(c, 18));
        bp.leftMargin = bp.rightMargin = Ui.dp(c, 16);
        col.addView(box, bp);

        // filter chips
        LinearLayout chipRow = Ui.row(c);
        chipRow.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), 0);
        String[] names = {"Installed", "System", "All"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView t = Ui.chip(c, names[i], i == filter);
            t.setOnClickListener(v -> {
                Ui.tick(v);
                setFilter(idx);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.dp(c, 38));
            if (i > 0) lp.leftMargin = Ui.dp(c, 8);
            chipRow.addView(t, lp);
            chips[i] = t;
        }
        col.addView(chipRow, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 14)));

        FrameLayout listHost = new FrameLayout(c);
        list = new ListView(c);
        list.setDivider(null);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        list.setClipToPadding(false);
        list.setVerticalScrollBarEnabled(false);
        list.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 130));
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            Ui.tap(v);
            hideKeyboard();
            appSheet(shown.get(pos));
        });
        list.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(android.widget.AbsListView v, int state) {
                if (state != SCROLL_STATE_IDLE) hideKeyboard();
            }

            @Override
            public void onScroll(android.widget.AbsListView v, int f, int n, int t) {}
        });
        listHost.addView(list, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        emptyBox = Ui.col(c);
        emptyBox.setGravity(Gravity.CENTER_HORIZONTAL);
        emptyBox.setPadding(Ui.dp(c, 36), Ui.dp(c, 60), Ui.dp(c, 36), 0);
        emptyBox.setVisibility(View.GONE);
        FrameLayout et = Ui.iconTile(c, R.drawable.ic_grid, 72);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(Ui.dp(c, 72), Ui.dp(c, 72));
        ep.gravity = Gravity.CENTER_HORIZONTAL;
        emptyBox.addView(et, ep);
        TextView em = Ui.text(c, "No apps match", Ui.Type.TITLE);
        em.setGravity(Gravity.CENTER);
        emptyBox.addView(em, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 20)));
        listHost.addView(emptyBox, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        col.addView(listHost, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        frame.addView(col, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        return frame;
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
        field.clearFocus();
    }

    @Override
    void onShow() {
        load(!loaded);
    }

    @Override
    void onDestroy() {
        hideKeyboard();
    }

    // ---------------------------------------------------------------- data
    static boolean isSystem(ApplicationInfo ai) {
        return (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    static int countUserApps(Context c) {
        int n = 0;
        for (ApplicationInfo ai : c.getPackageManager().getInstalledApplications(0)) if (!isSystem(ai)) n++;
        return n;
    }

    @SuppressWarnings("deprecation")
    private void load(boolean animate) {
        final int t = ++token;
        final PackageManager pm = c.getPackageManager();
        Bg.run(() -> {
            List<App> out = new ArrayList<>();
            for (PackageInfo pi : pm.getInstalledPackages(0)) {
                ApplicationInfo ai = pi.applicationInfo;
                if (ai == null) continue;
                App ap = new App();
                ap.ai = ai;
                ap.pkg = pi.packageName;
                ap.label = String.valueOf(ai.loadLabel(pm));
                ap.version = pi.versionName == null ? "" : pi.versionName;
                ap.installed = pi.firstInstallTime;
                ap.updated = pi.lastUpdateTime;
                ap.system = isSystem(ai);
                long size = ai.sourceDir == null ? 0 : new File(ai.sourceDir).length();
                if (ai.splitSourceDirs != null) for (String s : ai.splitSourceDirs) size += new File(s).length();
                ap.size = size;
                out.add(ap);
            }
            return out;
        }, (apps, err) -> {
            if (t != token || apps == null) return;
            all.clear();
            all.addAll(apps);
            loaded = true;
            apply(animate);
        });
    }

    private void setFilter(int f) {
        filter = f;
        for (int i = 0; i < 3; i++) Ui.setChip(chips[i], i == f);
        apply(true);
    }

    private void apply(boolean animate) {
        shown.clear();
        long bytes = 0;
        for (App ap : all) {
            if (filter == FILTER_USER && ap.system) continue;
            if (filter == FILTER_SYSTEM && !ap.system) continue;
            if (!query.isEmpty() && !ap.label.toLowerCase(Locale.ROOT).contains(query)
                    && !ap.pkg.toLowerCase(Locale.ROOT).contains(query)) continue;
            shown.add(ap);
            bytes += ap.size;
        }
        int sort = Prefs.appSort(c);
        Comparator<App> byName = (x, y) -> x.label.compareToIgnoreCase(y.label);
        Comparator<App> cmp = sort == 1 ? Comparator.<App>comparingLong(x -> x.size).reversed().thenComparing(byName)
                : sort == 2 ? Comparator.<App>comparingLong(x -> x.updated).reversed().thenComparing(byName) : byName;
        Collections.sort(shown, cmp);
        subtitle.setText(Fs.count(shown.size(), "app") + "  ·  " + Fs.size(bytes));
        adapter.notifyDataSetChanged();
        emptyBox.setVisibility(shown.isEmpty() && loaded ? View.VISIBLE : View.GONE);
        if (animate) {
            list.setAlpha(0f);
            list.setTranslationY(Ui.dp(c, 12));
            list.animate().alpha(1f).translationY(0).setDuration(380).setInterpolator(Ui.DECELERATE).start();
            list.setSelection(0);
        }
    }

    private void sortMenu() {
        Sheet s = new Sheet(a);
        s.title("Sort apps", null);
        int cur = Prefs.appSort(c);
        List<Sheet.Action> acts = new ArrayList<>();
        String[] names = {"Name", "Size (largest first)", "Recently updated"};
        int[] icons = {R.drawable.ic_file_text, R.drawable.ic_drive, R.drawable.ic_clock};
        for (int i = 0; i < 3; i++) {
            final int m = i;
            acts.add(new Sheet.Action(icons[i], names[i], () -> {
                Prefs.setAppSort(c, m);
                apply(true);
            }).checked(cur == i));
        }
        s.actions(acts);
        s.show();
    }

    // ---------------------------------------------------------------- app sheet
    private void appSheet(App ap) {
        Sheet s = new Sheet(a);
        ImageView icon = new ImageView(c);
        Thumbs.appIcon(icon, ap.ai, Ui.dp(c, 104));
        s.header(icon, ap.label, ap.pkg);

        LinearLayout stats = Ui.row(c);
        stats.addView(stat("Version", ap.version.isEmpty() ? "—" : ap.version), Ui.weight(Ui.WRAP));
        stats.addView(Ui.space(c, 10, 1));
        stats.addView(stat("Size", Fs.size(ap.size)), Ui.weight(Ui.WRAP));
        stats.addView(Ui.space(c, 10, 1));
        stats.addView(stat("Updated", Fs.date(ap.updated)), Ui.weight(Ui.WRAP));
        s.panel.addView(stats, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 18)));

        Intent launch = c.getPackageManager().getLaunchIntentForPackage(ap.pkg);
        LinearLayout grid = Ui.col(c);
        Object[][] acts = {
                {R.drawable.ic_play, "Open", (Runnable) () -> {
                    if (launch == null) a.snack(ap.label + " can't be opened directly");
                    else {
                        s.dismiss();
                        a.startActivity(launch);
                    }
                }, launch != null},
                {R.drawable.ic_info, "App info", (Runnable) () -> {
                    s.dismiss();
                    a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ap.pkg)));
                }, true},
                {R.drawable.ic_trash, "Uninstall", (Runnable) () -> {
                    s.dismiss();
                    uninstall(ap);
                }, true},
                {R.drawable.ic_download, "Save APK", (Runnable) () -> {
                    s.dismiss();
                    extract(ap, false);
                }, true},
                {R.drawable.ic_share, "Share APK", (Runnable) () -> {
                    s.dismiss();
                    extract(ap, true);
                }, true},
                {R.drawable.ic_external, "Play Store", (Runnable) () -> {
                    s.dismiss();
                    store(ap);
                }, true},
        };
        LinearLayout row = null;
        for (int i = 0; i < acts.length; i++) {
            if (i % 3 == 0) {
                row = Ui.row(c);
                grid.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP, i == 0 ? 0 : Ui.dp(c, 10)));
            }
            Object[] ac = acts[i];
            boolean enabled = (Boolean) ac[3];
            boolean primary = i == 0;
            LinearLayout b = Ui.col(c);
            b.setGravity(Gravity.CENTER);
            b.setPadding(0, Ui.dp(c, 14), 0, Ui.dp(c, 12));
            b.setBackground(primary ? Ui.rippleDark(Ui.ink(c, 20), Ui.dp(c, 20)) : Ui.ripple(Ui.card(c, 20), Ui.dp(c, 20)));
            int color = primary ? Ui.WHITE : (i == 2 ? Ui.DANGER : Ui.INK);
            b.addView(Ui.icon(c, (Integer) ac[0], color, 22), new LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22)));
            TextView t = Ui.text(c, (String) ac[1], Ui.Type.LABEL);
            t.setTextColor(color);
            b.addView(t, Ui.lp(Ui.WRAP, Ui.WRAP, Ui.dp(c, 8)));
            b.setAlpha(enabled ? 1f : 0.4f);
            Runnable r = (Runnable) ac[2];
            b.setOnClickListener(v -> {
                Ui.tap(v);
                r.run();
            });
            Ui.pressable(b, 0.94f);
            LinearLayout.LayoutParams lp = Ui.weight(Ui.WRAP);
            if (i % 3 != 0) lp.leftMargin = Ui.dp(c, 10);
            row.addView(b, lp);
        }
        s.panel.addView(grid, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 18)));

        TextView foot = Ui.oneLine(Ui.text(c, (ap.system ? "System app" : "Installed app") + "  ·  Installed "
                + Fs.date(ap.installed) + "  ·  Target SDK " + ap.ai.targetSdkVersion, Ui.Type.CAPTION));
        foot.setGravity(Gravity.CENTER);
        s.panel.addView(foot, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 16)));
        s.show();
    }

    private View stat(String label, String value) {
        LinearLayout b = Ui.col(c);
        b.setBackground(Ui.card(c, 18));
        b.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 12));
        b.addView(Ui.oneLine(Ui.text(c, label, Ui.Type.OVERLINE)));
        b.addView(Ui.oneLine(Ui.text(c, value, Ui.Type.CALLOUT)), Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 6)));
        return b;
    }

    @SuppressWarnings("deprecation")
    private void uninstall(App ap) {
        try {
            a.startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + ap.pkg)));
        } catch (ActivityNotFoundException e) {
            a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ap.pkg)));
        }
    }

    private void store(App ap) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + ap.pkg)));
        } catch (ActivityNotFoundException e) {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + ap.pkg)));
        }
    }

    /** Copy the app's APK to Download/My Files/Apps (or to cache, to share it). */
    private void extract(App ap, boolean share) {
        String safe = (ap.label + (ap.version.isEmpty() ? "" : "_" + ap.version)).replaceAll("[\\\\/:*?\"<>|]", "_");
        Sheet.Busy b = new Sheet.Busy(a, share ? "Preparing APK…" : "Saving APK…");
        Bg.run(() -> {
            File dir = share ? new File(c.getCacheDir(), "share")
                    : new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "My Files/Apps");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Couldn't create " + dir.getName());
            File out = share ? new File(dir, safe + ".apk") : Fs.unique(dir, safe + ".apk");
            b.total(1);
            b.step(out.getName());
            try (InputStream in = new FileInputStream(ap.ai.sourceDir); OutputStream o = new FileOutputStream(out)) {
                byte[] buf = new byte[128 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (b.cancelled()) throw new IOException("Cancelled");
                    o.write(buf, 0, n);
                }
            }
            if (!share) Fs.scan(c, out);
            return out;
        }, (out, err) -> {
            b.close();
            if (err != null || out == null) {
                a.snack(err != null && err.getMessage() != null ? err.getMessage() : "Couldn't copy the APK");
                return;
            }
            boolean split = ap.ai.splitSourceDirs != null && ap.ai.splitSourceDirs.length > 0;
            if (share) {
                Item it = Item.of(out);
                List<Item> one = new ArrayList<>();
                one.add(it);
                Fs.share(a, one);
            } else {
                a.snack("Saved to Download/My Files/Apps" + (split ? " (base APK only)" : ""), "Show",
                        () -> a.openFolder(out.getParentFile()));
            }
        });
    }

    // ---------------------------------------------------------------- rows
    private final class Holder {
        final LinearLayout row;
        final ImageView icon;
        final TextView name, meta, badge;

        Holder() {
            row = Ui.row(c);
            row.setPadding(Ui.dp(c, 10), Ui.dp(c, 9), Ui.dp(c, 10), Ui.dp(c, 9));
            row.setMinimumHeight(Ui.dp(c, 70));
            row.setBackground(Ui.ripple(null, Ui.dp(c, 20)));
            icon = new ImageView(c);
            row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
            LinearLayout col = Ui.col(c);
            col.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 8), 0);
            name = Ui.oneLine(Ui.text(c, "", Ui.Type.TITLE_S));
            name.setTextSize(15.5f);
            meta = Ui.oneLine(Ui.text(c, "", Ui.Type.CAPTION));
            col.addView(name);
            col.addView(meta, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 5)));
            row.addView(col, Ui.weight(Ui.WRAP));
            badge = Ui.text(c, "", Ui.Type.LABEL);
            badge.setTextSize(11);
            badge.setPadding(Ui.dp(c, 10), Ui.dp(c, 5), Ui.dp(c, 10), Ui.dp(c, 5));
            badge.setBackground(Ui.rounded(Ui.WHITE, Ui.dp(c, 12), Ui.LINE, Ui.stroke(c)));
            row.addView(badge);
            row.addView(Ui.icon(c, R.drawable.ic_chevron_right, Ui.INK_3, 18), new LinearLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 18)));
            row.setTag(this);
        }

        void bind(App ap) {
            name.setText(ap.label);
            meta.setText((ap.version.isEmpty() ? "" : "v" + ap.version + "  ·  ") + ap.pkg);
            badge.setText(Fs.size(ap.size));
            Thumbs.appIcon(icon, ap.ai, Ui.dp(c, 96));
        }
    }

    private final class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int p) {
            return shown.get(p);
        }

        @Override
        public long getItemId(int p) {
            return p;
        }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            Holder h = convert != null ? (Holder) convert.getTag() : new Holder();
            h.bind(shown.get(pos));
            return h.row;
        }
    }
}
