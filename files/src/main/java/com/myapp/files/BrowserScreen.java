package com.myapp.files;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Folder browser: breadcrumbs, sort, new folder/file, paste, and folder-to-folder navigation. */
final class BrowserScreen extends ListScreen {
    private final ArrayList<Loc> trail = new ArrayList<>();
    private final Map<String, int[]> scroll = new HashMap<>();
    private HorizontalScrollView crumbScroll;
    private LinearLayout crumbs;
    private FrameLayout fab;
    private LinearLayout pasteBar;
    private TextView pasteText;
    private int loadToken;

    BrowserScreen(MainActivity a, Loc start) {
        super(a);
        trail.add(start);
    }

    Loc loc() {
        return trail.get(trail.size() - 1);
    }

    boolean isStorageRoot() {
        return trail.size() == 1 && loc().isFile() && loc().file.equals(Loc.root());
    }

    @Override
    int tab() {
        return MainActivity.TAB_FILES;
    }

    @Override
    String titleText() {
        return loc().name;
    }

    @Override
    boolean showBack() {
        return true;
    }

    @Override
    File workDir() {
        return loc().isFile() ? loc().file : null;
    }

    // ---------------------------------------------------------------- layout
    @Override
    void addTrailing(LinearLayout row) {
        row.addView(Ui.circleButton(c, R.drawable.ic_search, 44, v -> {
            File scope = loc().isFile() ? loc().file : Loc.root();
            a.push(new SearchScreen(a, scope));
        }));
        row.addView(Ui.space(c, 8, 1));
        row.addView(Ui.circleButton(c, R.drawable.ic_more, 44, v -> menu()));
    }

    @Override
    void addBelowHeader(LinearLayout col) {
        crumbScroll = new HorizontalScrollView(c);
        crumbScroll.setHorizontalScrollBarEnabled(false);
        crumbScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        crumbs = Ui.row(c);
        crumbs.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 8));
        crumbScroll.addView(crumbs);
        col.addView(crumbScroll, Ui.lp(Ui.MATCH, Ui.WRAP));
    }

    @Override
    void addOverlays(FrameLayout frame) {
        FrameLayout host = new FrameLayout(c);
        host.setClipChildren(false);
        LinearLayout stackCol = Ui.col(c);
        stackCol.setGravity(Gravity.END);

        fab = new FrameLayout(c);
        fab.setBackground(Ui.rippleDark(Ui.ink(c, 30), Ui.dp(c, 30)));
        fab.addView(Ui.icon(c, R.drawable.ic_plus, Ui.WHITE, 26));
        Ui.elevate(fab, 14, 30);
        fab.setOnClickListener(v -> {
            Ui.tap(v);
            createMenu();
        });
        Ui.pressable(fab, 0.9f);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(Ui.dp(c, 60), Ui.dp(c, 60));
        fp.rightMargin = Ui.dp(c, 20);
        stackCol.addView(fab, fp);

        pasteBar = Ui.row(c);
        pasteBar.setBackground(Ui.card(c, 26));
        pasteBar.setPadding(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8));
        Ui.elevate(pasteBar, 14, 26);
        FrameLayout ptile = Ui.iconTile(c, R.drawable.ic_paste, 40);
        pasteBar.addView(ptile, new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40)));
        pasteText = Ui.oneLine(Ui.text(c, "", Ui.Type.CALLOUT));
        pasteText.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 8), 0);
        pasteBar.addView(pasteText, Ui.weight(Ui.WRAP));
        FrameLayout x = Ui.circleButton(c, R.drawable.ic_x, 40, v -> {
            Clip.clear();
            updatePaste();
        });
        pasteBar.addView(x);
        pasteBar.addView(Ui.space(c, 8, 1));
        TextView paste = Ui.pill(c, "Paste", true, v -> paste());
        paste.setMinHeight(Ui.dp(c, 42));
        pasteBar.addView(paste, new LinearLayout.LayoutParams(Ui.WRAP, Ui.dp(c, 42)));
        LinearLayout.LayoutParams pp = Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 14));
        pp.leftMargin = pp.rightMargin = Ui.dp(c, 16);
        stackCol.addView(pasteBar, pp);

        FrameLayout.LayoutParams sp = Ui.flp(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM);
        sp.bottomMargin = Ui.dp(c, 92);
        host.addView(stackCol, sp);
        Ui.insets(host, false, true, false);
        frame.addView(host, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        updatePaste();
    }

    private void updatePaste() {
        boolean show = Clip.has() && !(list != null && list.selecting);
        if (show) {
            int n = Clip.items.size();
            pasteText.setText((Clip.move ? "Move " : "Copy ") + (n == 1 ? "\"" + Clip.items.get(0).name + "\"" : n + " items") + " here");
        }
        if (show && pasteBar.getVisibility() != View.VISIBLE) {
            pasteBar.setVisibility(View.VISIBLE);
            pasteBar.setAlpha(0f);
            pasteBar.setTranslationY(Ui.dp(c, 20));
            pasteBar.animate().alpha(1f).translationY(0).setDuration(360).setInterpolator(Ui.DECELERATE).start();
        } else if (!show) {
            pasteBar.setVisibility(View.GONE);
        }
        list.setBottomPadding(Ui.dp(c, show ? 220 : 170));
    }

    @Override
    void onClipChanged() {
        updatePaste();
    }

    @Override
    void onSelectionMode(boolean on) {
        fab.animate().scaleX(on ? 0f : 1f).scaleY(on ? 0f : 1f).setDuration(220).start();
        updatePaste();
    }

    private void buildCrumbs() {
        crumbs.removeAllViews();
        List<String> names = new ArrayList<>();
        List<Loc> locs = new ArrayList<>();
        Loc cur = loc();
        if (cur.isFile()) {
            File root = Loc.root();
            File f = cur.file;
            List<File> chain = new ArrayList<>();
            while (f != null) {
                chain.add(0, f);
                if (f.equals(root) || "/storage".equals(f.getParent()) || f.getParent() == null) break;
                f = f.getParentFile();
            }
            for (File ff : chain) {
                locs.add(Loc.of(ff));
                names.add(ff.equals(root) ? "Internal" : "/storage".equals(ff.getParent()) ? "SD card"
                        : ff.getName().isEmpty() ? "/" : ff.getName());
            }
        } else {
            for (Loc l : trail) {
                locs.add(l);
                names.add(l.name);
            }
        }
        for (int i = 0; i < names.size(); i++) {
            boolean last = i == names.size() - 1;
            if (i > 0) {
                ImageView sep = Ui.icon(c, R.drawable.ic_chevron_right, Ui.INK_3, 14);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 14), Ui.dp(c, 14));
                lp.leftMargin = lp.rightMargin = Ui.dp(c, 4);
                crumbs.addView(sep, lp);
            }
            TextView t = Ui.text(c, names.get(i), Ui.Type.LABEL);
            t.setGravity(Gravity.CENTER);
            t.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0);
            t.setMinHeight(Ui.dp(c, 32));
            if (last) {
                t.setTextColor(Ui.WHITE);
                t.setBackground(Ui.ink(c, 16));
            } else {
                t.setTextColor(Ui.INK);
                t.setBackground(Ui.ripple(Ui.rounded(Ui.WHITE, Ui.dp(c, 16), Ui.LINE, Ui.stroke(c)), Ui.dp(c, 16)));
                final Loc target = locs.get(i);
                final int idx = i;
                t.setOnClickListener(v -> {
                    Ui.tap(v);
                    if (loc().isFile()) navigate(target, false);
                    else jumpTrail(idx);
                });
                Ui.pressable(t, 0.94f);
            }
            crumbs.addView(t, new LinearLayout.LayoutParams(Ui.WRAP, Ui.dp(c, 32)));
        }
        crumbScroll.post(() -> crumbScroll.fullScroll(View.FOCUS_RIGHT));
    }

    // ---------------------------------------------------------------- navigation
    @Override
    void openDir(Item i) {
        navigate(i.asLoc(), true);
    }

    private void saveScroll() {
        int first = list.list.getFirstVisiblePosition();
        View v = list.list.getChildAt(0);
        scroll.put(loc().key(), new int[]{first, v == null ? 0 : v.getTop() - list.list.getPaddingTop()});
    }

    void navigate(Loc l, boolean forward) {
        saveScroll();
        if (list.selecting) list.clearSelection();
        trail.add(l);
        slide(forward);
        reload(true);
    }

    private void jumpTrail(int idx) {
        saveScroll();
        while (trail.size() > idx + 1) trail.remove(trail.size() - 1);
        slide(false);
        reload(true);
    }

    private void slide(boolean forward) {
        float d = Ui.dp(c, 28) * (forward ? 1 : -1);
        list.setTranslationX(d);
        list.setAlpha(0f);
        list.animate().translationX(0).alpha(1f).setDuration(320).setInterpolator(Ui.DECELERATE).start();
    }

    @Override
    boolean onBack() {
        if (super.onBack()) return true;
        if (trail.size() > 1) {
            saveScroll();
            trail.remove(trail.size() - 1);
            slide(false);
            reload(true);
            return true;
        }
        return false;
    }

    @Override
    void onShow() {
        super.onShow();
        updatePaste();
    }

    // ---------------------------------------------------------------- loading
    @Override
    void reload(boolean animate) {
        final Loc l = loc();
        title.setText(l.name);
        buildCrumbs();
        if (l.isFile() && !Fs.hasAllFiles()) {
            subtitle.setText("Access needed");
            list.showEmpty(R.drawable.ic_lock, "Allow access to your files",
                    "My Files needs permission to show and manage the files on your phone.",
                    "Allow access", () -> Fs.askAllFiles(a));
            return;
        }
        final int token = ++loadToken;
        final boolean hidden = Prefs.hidden(c);
        final int sort = Prefs.sort(c);
        final boolean desc = Prefs.desc(c);
        Bg.run(() -> {
            List<Item> items = Fs.list(c, l, hidden);
            if (items != null) Fs.sort(items, sort, desc);
            return items;
        }, (items, err) -> {
            if (token != loadToken) return;
            if (items == null) {
                subtitle.setText("Can't open");
                String path = l.isFile() ? l.file.getAbsolutePath() : "";
                String rootPath = Loc.root().getAbsolutePath();
                if (path.equals(rootPath + "/Android/data") || path.equals(rootPath + "/Android/obb")) {
                    String sub = path.endsWith("data") ? "data" : "obb";
                    list.showEmpty(R.drawable.ic_lock, "Protected folder",
                            "Android keeps app data private.", "See options", () -> a.openRestricted(sub));
                } else {
                    list.showEmpty(R.drawable.ic_lock, "Can't open this folder",
                            "Android doesn't allow access to this location.", null, null);
                }
                return;
            }
            int folders = 0;
            for (Item i : items) if (i.dir) folders++;
            int files = items.size() - folders;
            subtitle.setText(items.isEmpty() ? "Empty" : (folders > 0 ? Fs.count(folders, "folder") : "")
                    + (folders > 0 && files > 0 ? "  ·  " : "") + (files > 0 ? Fs.count(files, "file") : ""));
            if (items.isEmpty()) {
                list.showEmpty(R.drawable.ic_folder, "This folder is empty",
                        Clip.has() ? "Tap Paste to put your items here." : "Tap + to create a folder or file.", null, null);
                return;
            }
            show(items, animate);
            int[] pos = scroll.remove(l.key());
            if (pos != null) list.list.setSelectionFromTop(pos[0], pos[1]);
            else if (animate) list.list.setSelection(0);
        });
    }

    // ---------------------------------------------------------------- menus
    private void menu() {
        Sheet s = new Sheet(a);
        s.title("View", loc().isFile() ? loc().file.getAbsolutePath() : loc().name);
        int sort = Prefs.sort(c);
        boolean desc = Prefs.desc(c);
        List<Sheet.Action> acts = new ArrayList<>();
        String[] names = {"Name", "Date modified", "Size", "Type"};
        int[] icons = {R.drawable.ic_file_text, R.drawable.ic_clock, R.drawable.ic_drive, R.drawable.ic_layers};
        for (int i = 0; i < 4; i++) {
            final int mode = i;
            acts.add(new Sheet.Action(icons[i], "Sort by " + names[i].toLowerCase(), () -> {
                // newest / biggest first by default
                Prefs.setSort(c, mode, mode == Prefs.SORT_DATE || mode == Prefs.SORT_SIZE);
                reload(true);
            }).checked(sort == i));
        }
        acts.add(new Sheet.Action(R.drawable.ic_sort, desc ? "Order: descending" : "Order: ascending", () -> {
            Prefs.setSort(c, sort, !desc);
            reload(true);
        }));
        acts.add(new Sheet.Action(R.drawable.ic_eye, "Show hidden files", () -> {
            Prefs.setHidden(c, !Prefs.hidden(c));
            reload(true);
        }).checked(Prefs.hidden(c)));
        acts.add(new Sheet.Action(R.drawable.ic_select_all, "Select all", () -> {
            if (!list.items().isEmpty()) list.selectAll();
        }));
        acts.add(new Sheet.Action(R.drawable.ic_refresh, "Refresh", () -> {
            Thumbs.forget();
            reload(true);
        }));
        s.actions(acts);
        s.show();
    }

    private void createMenu() {
        Sheet s = new Sheet(a);
        s.title("Create", "in " + loc().name);
        List<Sheet.Action> acts = new ArrayList<>();
        acts.add(new Sheet.Action(R.drawable.ic_folder_plus, "New folder", () ->
                Sheet.input(a, "New folder", "", "Folder name", "Create", false, name -> {
                    try {
                        Loc made = Fs.mkdir(c, loc(), name);
                        reload(false);
                        a.snack("Created \"" + name + "\"", "Open", () -> navigate(made, true));
                        return null;
                    } catch (Exception e) {
                        return e.getMessage();
                    }
                })));
        acts.add(new Sheet.Action(R.drawable.ic_file_plus, "New text file", () ->
                Sheet.input(a, "New text file", "Untitled.txt", "File name", "Create", true, name -> {
                    try {
                        Fs.newFile(c, loc(), name);
                        reload(false);
                        a.snack("Created \"" + name + "\"");
                        return null;
                    } catch (Exception e) {
                        return e.getMessage();
                    }
                })));
        if (Clip.has()) acts.add(new Sheet.Action(R.drawable.ic_paste, "Paste here", this::paste));
        s.actions(acts);
        s.show();
    }

    private void paste() {
        if (!Clip.has()) return;
        final List<Item> items = new ArrayList<>(Clip.items);
        final boolean move = Clip.move;
        final Loc dest = loc();
        work(move ? "Moving" : "Copying", b -> {
            b.total(countSteps(items, false));
            Fs.transfer(c, items, dest, move, b);
            return (move ? "Moved " : "Copied ") + (items.size() == 1 ? "\"" + items.get(0).name + "\"" : items.size() + " items");
        });
        if (move) Clip.clear();
        updatePaste();
    }
}
