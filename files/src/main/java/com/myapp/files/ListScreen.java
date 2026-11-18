package com.myapp.files;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base for screens that show a FileList: header, selection mode with its action bar, the
 * per-item menu, and running file jobs with a progress sheet.
 */
abstract class ListScreen extends Screen implements FileList.Host {
    FrameLayout frame;
    LinearLayout column, header, selHeader, trailing;
    TextView title, subtitle, selTitle;
    FileList list;
    LinearLayout actionBar;
    FrameLayout back;
    private boolean loadedOnce;

    ListScreen(MainActivity a) {
        super(a);
    }

    abstract String titleText();

    /** Load (or reload) the list; implementations call {@link #show}. */
    abstract void reload(boolean animate);

    boolean showBack() {
        return true;
    }

    void onBackButton() {
        a.onBackPressed();
    }

    /** Extra views under the header (breadcrumbs, search field...). */
    void addBelowHeader(LinearLayout col) {}

    /** Buttons on the right of the header. */
    void addTrailing(LinearLayout row) {}

    /** Floating extras over the list (FAB, paste bar). */
    void addOverlays(FrameLayout frame) {}

    /** Where "Compress" and new things go; null when not a real folder. */
    File workDir() {
        return null;
    }

    @Override
    View build() {
        frame = new FrameLayout(c);
        column = Ui.col(c);
        column.setPadding(0, Ui.dp(c, 8), 0, 0);
        Ui.insets(column, true, false, false);

        header = Ui.row(c);
        header.setPadding(Ui.dp(c, 16), Ui.dp(c, 6), Ui.dp(c, 16), Ui.dp(c, 10));
        if (showBack()) {
            back = Ui.circleButton(c, R.drawable.ic_chevron_left, 44, v -> onBackButton());
            header.addView(back);
        }
        LinearLayout titles = Ui.col(c);
        titles.setPadding(Ui.dp(c, showBack() ? 14 : 4), 0, Ui.dp(c, 8), 0);
        title = Ui.oneLine(Ui.text(c, titleText(), Ui.Type.TITLE));
        title.setTextSize(22);
        title.setTypeface(Fonts.display(c));
        subtitle = Ui.oneLine(Ui.text(c, " ", Ui.Type.CAPTION));
        titles.addView(title);
        titles.addView(subtitle, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 4)));
        header.addView(titles, Ui.weight(Ui.WRAP));
        trailing = Ui.row(c);
        addTrailing(trailing);
        header.addView(trailing);
        column.addView(header);

        selHeader = Ui.row(c);
        selHeader.setPadding(Ui.dp(c, 16), Ui.dp(c, 6), Ui.dp(c, 16), Ui.dp(c, 10));
        selHeader.addView(Ui.circleButton(c, R.drawable.ic_x, 44, v -> list.clearSelection()));
        selTitle = Ui.text(c, "", Ui.Type.TITLE);
        selTitle.setTextSize(22);
        selTitle.setTypeface(Fonts.display(c));
        selTitle.setPadding(Ui.dp(c, 14), 0, 0, 0);
        selHeader.addView(selTitle, Ui.weight(Ui.WRAP));
        selHeader.addView(Ui.circleButton(c, R.drawable.ic_select_all, 44, v -> list.selectAll()));
        selHeader.setVisibility(View.GONE);
        column.addView(selHeader);

        addBelowHeader(column);

        list = new FileList(c, this);
        column.addView(list, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        frame.addView(column, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        actionBar = buildActionBar();
        FrameLayout.LayoutParams ap = Ui.flp(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM);
        ap.setMargins(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 18));
        FrameLayout barHost = new FrameLayout(c);
        barHost.addView(actionBar, ap);
        Ui.insets(barHost, false, true, false);
        frame.addView(barHost, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        barHost.setClickable(false);
        actionBar.setVisibility(View.GONE);

        addOverlays(frame);
        return frame;
    }

    @Override
    void onShow() {
        reload(!loadedOnce);
        loadedOnce = true;
    }

    void show(List<Item> items, boolean animate) {
        list.setItems(items, animate);
    }

    @Override
    boolean onBack() {
        if (list != null && list.selecting) {
            list.clearSelection();
            return true;
        }
        return false;
    }

    @Override
    boolean navVisible() {
        return list == null || !list.selecting;
    }

    // ---------------------------------------------------------------- selection
    @Override
    public void selectionChanged(int n) {
        boolean on = n > 0;
        if (on) selTitle.setText(n == 1 ? "1 selected" : n + " selected");
        boolean was = selHeader.getVisibility() == View.VISIBLE;
        if (on != was) {
            selHeader.setVisibility(on ? View.VISIBLE : View.GONE);
            header.setVisibility(on ? View.GONE : View.VISIBLE);
            (on ? selHeader : header).setAlpha(0f);
            (on ? selHeader : header).animate().alpha(1f).setDuration(200).start();
            if (on) {
                actionBar.setVisibility(View.VISIBLE);
                actionBar.setTranslationY(Ui.dp(c, 120));
                actionBar.animate().translationY(0).setDuration(380).setInterpolator(Ui.DECELERATE).start();
            } else {
                actionBar.animate().translationY(Ui.dp(c, 140)).setDuration(220).setInterpolator(Ui.ACCELERATE)
                        .withEndAction(() -> actionBar.setVisibility(View.GONE)).start();
            }
            a.suppressNav(on);
            onSelectionMode(on);
        }
    }

    void onSelectionMode(boolean on) {}

    private LinearLayout buildActionBar() {
        LinearLayout bar = Ui.row(c);
        bar.setBackground(Ui.ink(c, 28));
        bar.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
        Ui.elevate(bar, 16, 28);
        bar.addView(barButton(R.drawable.ic_copy, "Copy", () -> toClip(list.selection(), false)), Ui.weight(Ui.dp(c, 60)));
        bar.addView(barButton(R.drawable.ic_move, "Move", () -> toClip(list.selection(), true)), Ui.weight(Ui.dp(c, 60)));
        bar.addView(barButton(R.drawable.ic_share, "Share", () -> share(list.selection())), Ui.weight(Ui.dp(c, 60)));
        bar.addView(barButton(R.drawable.ic_archive, "Zip", () -> compress(list.selection())), Ui.weight(Ui.dp(c, 60)));
        bar.addView(barButton(R.drawable.ic_trash, "Delete", () -> delete(list.selection())), Ui.weight(Ui.dp(c, 60)));
        return bar;
    }

    private View barButton(int icon, String label, Runnable r) {
        LinearLayout b = Ui.col(c);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.rippleDark(null, Ui.dp(c, 22)));
        ImageView ic = Ui.icon(c, icon, Ui.WHITE, 21);
        b.addView(ic, new LinearLayout.LayoutParams(Ui.dp(c, 21), Ui.dp(c, 21)));
        TextView t = Ui.text(c, label, Ui.Type.LABEL);
        t.setTextSize(11.5f);
        t.setTextColor(0xCCFFFFFF);
        b.addView(t, Ui.lp(Ui.WRAP, Ui.WRAP, Ui.dp(c, 5)));
        b.setOnClickListener(v -> {
            Ui.tap(v);
            if (list.selected.isEmpty()) return;
            r.run();
        });
        Ui.pressable(b, 0.9f);
        return b;
    }

    // ---------------------------------------------------------------- item menu
    @Override
    public void open(Item i) {
        if (i.dir) {
            openDir(i);
            return;
        }
        if (!Fs.open(a, i, false)) a.snack("No app can open this file");
    }

    void openDir(Item i) {
        a.push(new BrowserScreen(a, i.asLoc()));
    }

    boolean showInFolderAction() {
        return false;
    }

    @Override
    public void more(Item i) {
        Sheet s = new Sheet(a);
        FrameLayout tile = new FrameLayout(c);
        tile.setBackground(Ui.tile(c, 52));
        int in = Ui.stroke(c);
        tile.setPadding(in, in, in, in);
        tile.addView(Ui.icon(c, i.kind().icon, Ui.INK, 24));
        if (Thumbs.has(i)) {
            ImageView th = new ImageView(c);
            th.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Ui.clip(th, 16);
            tile.addView(th, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            Thumbs.load(th, i, Ui.dp(c, 96), b -> {
                if (b != null) th.setImageBitmap(b);
            });
        }
        s.header(tile, i.name, i.dir ? Fs.meta(i) : i.kind().label + "  ·  " + Fs.size(i.size));

        List<Sheet.Action> acts = new ArrayList<>();
        List<Item> one = Collections.singletonList(i);
        Item.Kind k = i.kind();
        if (i.dir) {
            acts.add(new Sheet.Action(R.drawable.ic_folder, "Open", () -> open(i)));
        } else if (k == Item.Kind.APK && "apk".equals(i.ext())) {
            acts.add(new Sheet.Action(R.drawable.ic_download, "Install", () -> open(i)));
        } else {
            acts.add(new Sheet.Action(R.drawable.ic_external, "Open", () -> open(i)));
            acts.add(new Sheet.Action(R.drawable.ic_layers, "Open with…", () -> {
                if (!Fs.open(a, i, true)) a.snack("No app can open this file");
            }));
        }
        if (!i.dir) acts.add(new Sheet.Action(R.drawable.ic_share, "Share", () -> share(one)));
        if (showInFolderAction() && i.isFile() && i.file.getParentFile() != null)
            acts.add(new Sheet.Action(R.drawable.ic_folder, "Show in folder", () -> a.openFolder(i.file.getParentFile())));
        acts.add(new Sheet.Action(R.drawable.ic_copy, "Copy", () -> toClip(one, false)));
        acts.add(new Sheet.Action(R.drawable.ic_move, "Move", () -> toClip(one, true)));
        acts.add(new Sheet.Action(R.drawable.ic_edit, "Rename", () -> rename(i)));
        if (i.isFile() && k == Item.Kind.ARCHIVE && "zip".equals(i.ext()))
            acts.add(new Sheet.Action(R.drawable.ic_archive, "Extract here", () -> extract(i)));
        else if (i.isFile())
            acts.add(new Sheet.Action(R.drawable.ic_archive, "Compress to zip", () -> compress(one)));
        acts.add(new Sheet.Action(R.drawable.ic_info, "Details", () -> details(i)));
        if (i.isFile()) acts.add(new Sheet.Action(R.drawable.ic_link, "Copy path", () -> {
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("path", i.file.getAbsolutePath()));
            a.snack("Path copied");
        }));
        acts.add(new Sheet.Action(R.drawable.ic_trash, "Delete", () -> delete(one)).danger());
        s.actions(acts);
        s.show();
    }

    // ---------------------------------------------------------------- operations
    void toClip(List<Item> items, boolean move) {
        if (items.isEmpty()) return;
        Clip.set(items, move);
        if (list.selecting) list.clearSelection();
        String what = items.size() == 1 ? "\"" + items.get(0).name + "\"" : items.size() + " items";
        onClipChanged();
        a.snack(what + (move ? " ready to move" : " copied") + " — open a folder and tap Paste");
    }

    void onClipChanged() {}

    void share(List<Item> items) {
        boolean anyDir = false;
        for (Item i : items) anyDir |= i.dir;
        if (anyDir && items.size() == 1) {
            a.snack("Folders can't be shared — compress it to a zip first");
            return;
        }
        Fs.share(a, items);
        if (list.selecting) list.clearSelection();
    }

    void rename(Item i) {
        Sheet.input(a, i.dir ? "Rename folder" : "Rename file", i.name, "Name", "Rename", !i.dir, name -> {
            if (name.equals(i.name)) return null;
            try {
                Fs.rename(c, i, name);
                Thumbs.forget();
                reload(false);
                a.snack("Renamed to \"" + name + "\"");
                return null;
            } catch (Exception e) {
                return e.getMessage();
            }
        });
    }

    void delete(List<Item> items) {
        if (items.isEmpty()) return;
        String what = items.size() == 1 ? "\"" + items.get(0).name + "\"" : items.size() + " items";
        Sheet.confirm(a, "Delete " + what + "?", "This can't be undone. Folders are deleted with everything inside them.",
                "Delete", () -> work("Deleting", b -> {
                    b.total(countSteps(items, true));
                    for (Item i : items) {
                        if (b.cancelled()) break;
                        Fs.delete(c, i, b);
                    }
                    return "Deleted " + what;
                }));
    }

    void compress(List<Item> items) {
        List<Item> files = new ArrayList<>();
        for (Item i : items) if (i.isFile()) files.add(i);
        if (files.isEmpty()) {
            a.snack("Only files on this phone can be zipped");
            return;
        }
        File dir = workDir() != null ? workDir() : files.get(0).file.getParentFile();
        String base = files.size() == 1 ? stripExt(files.get(0).name) : "Archive";
        Sheet.input(a, "Compress to zip", base + ".zip", "Archive name", "Compress", true, name -> {
            work("Compressing", b -> {
                b.total(countSteps(files, false));
                File z = Fs.compress(c, files, dir, name, b);
                return "Created " + z.getName();
            });
            return null;
        });
    }

    void extract(Item i) {
        work("Extracting", b -> {
            File d = Fs.extract(c, i.file, b);
            return "Extracted to " + d.getName();
        });
    }

    void details(Item i) {
        Sheet s = new Sheet(a);
        s.title(i.name, null);
        String where = i.isFile() ? i.file.getParent() : String.valueOf(i.doc);
        String size = i.dir ? "Calculating…" : Fs.size(i.size) + "  (" + String.format(java.util.Locale.US, "%,d", i.size) + " bytes)";
        String access = i.isFile() ? (i.file.canRead() ? "Read" : "No read") + (i.file.canWrite() ? " · Write" : "")
                + (i.hidden() ? " · Hidden" : "") : "Granted folder";
        s.details(new String[][]{
                {"Type", i.dir ? "Folder" : i.kind().label + "  ·  " + (i.mime == null ? "unknown" : i.mime)},
                {"Size", size},
                {"Location", where == null ? "/" : where},
                {"Modified", Fs.fullDate(i.modified)},
                {"Access", access},
        });
        s.show();
        if (i.dir && i.isFile()) {
            Bg.run(() -> Fs.dirStats(i.file), (st, err) -> {
                if (st == null) return;
                View row = s.panel.findViewWithTag("Size");
                if (row instanceof LinearLayout && ((LinearLayout) row).getChildCount() > 1) {
                    ((TextView) ((LinearLayout) row).getChildAt(1)).setText(Fs.size(st[0]) + "  ·  "
                            + Fs.count((int) st[1], "file") + ", " + Fs.count((int) st[2], "folder"));
                }
            });
        }
    }

    static String stripExt(String n) {
        int d = n.lastIndexOf('.');
        return d > 0 ? n.substring(0, d) : n;
    }

    /** Count files (and folders when deleting) so the progress bar can fill. Docs: unknown. */
    static int countSteps(List<Item> items, boolean dirsToo) {
        int n = 0;
        for (Item i : items) {
            if (!i.isFile()) return 0;
            if (!i.dir) {
                n++;
                continue;
            }
            if (dirsToo) n++;
            ArrayDeque<File> q = new ArrayDeque<>();
            q.add(i.file);
            while (!q.isEmpty() && n < 200000) {
                File[] kids = q.poll().listFiles();
                if (kids == null) continue;
                for (File k : kids) {
                    if (k.isDirectory()) {
                        if (dirsToo) n++;
                        if (!Files.isSymbolicLink(k.toPath())) q.add(k);
                    } else n++;
                }
            }
        }
        return n;
    }

    interface Work {
        String run(Sheet.Busy b) throws Exception;
    }

    void work(String title, Work w) {
        Sheet.Busy b = new Sheet.Busy(a, title + "…");
        if (list.selecting) list.clearSelection();
        Bg.run(() -> w.run(b), (msg, err) -> {
            b.close();
            Thumbs.forget();
            if (b.cancelled) a.snack("Stopped");
            else if (err != null) a.snack(err.getMessage() == null ? "Something went wrong" : err.getMessage());
            else if (msg != null) a.snack(msg);
            reload(false);
        });
    }
}
