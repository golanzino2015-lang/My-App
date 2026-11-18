package com.myapp.files;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Single activity; screens are views pushed onto a stack with slide transitions. */
public class MainActivity extends Activity {
    static final int TAB_HOME = 0, TAB_FILES = 1, TAB_APPS = 2;
    private static final int REQ_TREE = 41;

    FrameLayout root;
    private FrameLayout stage;
    private NavBar nav;
    private final List<Screen> stack = new ArrayList<>();
    private boolean navSuppressed;
    private String pendingTree;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setDecorFitsSystemWindows(false);
        WindowInsetsController ic = getWindow().getInsetsController();
        if (ic != null) {
            int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            ic.setSystemBarsAppearance(light, light);
        }
        root = new FrameLayout(this);
        root.setBackground(Ui.background(this));
        stage = new FrameLayout(this);
        root.addView(stage, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        nav = new NavBar(this, this::setTab);
        FrameLayout.LayoutParams np = Ui.flp(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM);
        np.bottomMargin = Ui.dp(this, 18);
        root.addView(nav, np);
        Ui.insets(nav, false, true, false);
        setContentView(root);
        setTab(TAB_HOME);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Screen t = top();
        if (t != null) t.onShow();
    }

    @Override
    protected void onDestroy() {
        for (Screen s : stack) s.onDestroy();
        super.onDestroy();
    }

    Screen top() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    // ---------------------------------------------------------------- navigation
    void setTab(int tab) {
        Screen t = top();
        if (t != null && stack.size() == 1 && t.tab() == tab && rootTab(t)) return;
        for (Screen s : stack) {
            s.onDestroy();
            stage.removeView(s.view);
        }
        stack.clear();
        navSuppressed = false;
        Screen s = tab == TAB_FILES ? new BrowserScreen(this, Loc.of(Loc.root()))
                : tab == TAB_APPS ? new AppsScreen(this) : new HomeScreen(this);
        s.view = s.build();
        stage.addView(s.view, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        stack.add(s);
        s.view.setAlpha(0f);
        s.view.setTranslationY(Ui.dp(this, 10));
        s.view.animate().alpha(1f).translationY(0f).setDuration(380).setInterpolator(Ui.DECELERATE).start();
        s.onShow();
        updateNav(true);
    }

    private boolean rootTab(Screen s) {
        return s instanceof HomeScreen || s instanceof AppsScreen
                || (s instanceof BrowserScreen && ((BrowserScreen) s).isStorageRoot());
    }

    void push(Screen s) {
        Screen prev = top();
        s.view = s.build();
        stage.addView(s.view, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        stack.add(s);
        navSuppressed = false;
        float w = getResources().getDisplayMetrics().widthPixels;
        s.view.setTranslationX(w * 0.25f);
        s.view.setAlpha(0f);
        s.view.animate().translationX(0).alpha(1f).setDuration(420).setInterpolator(Ui.DECELERATE).start();
        if (prev != null) {
            View pv = prev.view;
            pv.animate().translationX(-w * 0.08f).alpha(0f).setDuration(300).setInterpolator(Ui.EMPHASIZED)
                    .withEndAction(() -> {
                        if (top() != prev) pv.setVisibility(View.INVISIBLE);
                    }).start();
        }
        s.onShow();
        updateNav(true);
    }

    void pop() {
        if (stack.size() < 2) return;
        Screen s = stack.remove(stack.size() - 1);
        Screen prev = top();
        float w = getResources().getDisplayMetrics().widthPixels;
        View v = s.view;
        v.animate().translationX(w * 0.3f).alpha(0f).setDuration(280).setInterpolator(Ui.ACCELERATE)
                .withEndAction(() -> stage.removeView(v)).start();
        s.onDestroy();
        navSuppressed = false;
        if (prev != null) {
            prev.view.setVisibility(View.VISIBLE);
            prev.view.animate().translationX(0).alpha(1f).setDuration(380).setInterpolator(Ui.DECELERATE).start();
            prev.onShow();
        }
        updateNav(true);
    }

    void updateNav(boolean animate) {
        Screen t = top();
        if (t == null) return;
        nav.select(t.tab(), animate);
        nav.show(t.navVisible() && !navSuppressed);
    }

    /** Screens hide the tab bar while they show their own bottom bar (e.g. selection actions). */
    void suppressNav(boolean hide) {
        navSuppressed = hide;
        updateNav(true);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        Screen t = top();
        if (t != null && t.onBack()) return;
        if (stack.size() > 1) {
            pop();
            return;
        }
        if (t != null && !(t instanceof HomeScreen)) {
            setTab(TAB_HOME);
            return;
        }
        super.onBackPressed();
    }

    void snack(String msg) {
        Snack.show(root, msg);
    }

    void snack(String msg, String action, Runnable r) {
        Snack.show(root, msg, action, r);
    }

    // ---------------------------------------------------------------- folders
    void openFolder(File dir) {
        push(new BrowserScreen(this, Loc.of(dir)));
    }

    /**
     * Android/data and Android/obb are locked by Android 11+. Use a folder grant when we have
     * one, otherwise explain and offer the routes that work on this Android version.
     */
    void openRestricted(String sub) {
        Uri tree = Fs.restrictedTree(sub);
        if (Fs.hasTree(this, tree)) {
            push(new BrowserScreen(this, Fs.restrictedLoc(sub)));
            return;
        }
        File dir = new File(Loc.root(), "Android/" + sub);
        String[] kids = dir.list();
        if (kids != null && kids.length > 1) { // some phones still allow direct access
            openFolder(dir);
            return;
        }
        Sheet s = new Sheet(this);
        FrameLayout tile = Ui.iconTile(this, R.drawable.ic_lock, 52);
        s.header(tile, "Android/" + sub, "Protected by Android");
        android.widget.TextView m = Ui.text(this, Build.VERSION.SDK_INT < 33
                ? "Android keeps app data private. Tap Allow access, then \"Use this folder\" and \"Allow\" to browse it here."
                : "Android " + Build.VERSION.RELEASE + " blocks other apps from reading app data. You can still view it in the phone's built-in Files app.",
                Ui.Type.BODY);
        s.panel.addView(m, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(this, 16)));
        List<Sheet.Action> acts = new ArrayList<>();
        if (Build.VERSION.SDK_INT < 33) {
            acts.add(new Sheet.Action(R.drawable.ic_lock, "Allow access", () -> requestTree(sub)));
        }
        acts.add(new Sheet.Action(R.drawable.ic_external, "Open in system Files", () -> openInSystemFiles(sub)));
        acts.add(new Sheet.Action(R.drawable.ic_folder, "Browse what's visible", () -> openFolder(dir)));
        s.actions(acts);
        s.show();
    }

    private void requestTree(String sub) {
        pendingTree = sub;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                DocumentsContract.buildDocumentUri(Fs.EXT_AUTH, "primary:Android/" + sub));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_TREE);
        } catch (ActivityNotFoundException e) {
            snack("No folder picker on this phone");
        }
    }

    private void openInSystemFiles(String sub) {
        Uri u = DocumentsContract.buildDocumentUri(Fs.EXT_AUTH, "primary:Android/" + sub);
        String[] pkgs = {"com.google.android.documentsui", "com.android.documentsui", null};
        for (String p : pkgs) {
            Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(u, DocumentsContract.Document.MIME_TYPE_DIR)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (p != null) i.setPackage(p);
            try {
                startActivity(i);
                return;
            } catch (ActivityNotFoundException ignored) {
            }
        }
        snack("Couldn't open the system Files app");
    }

    /** Pick any folder through the system picker (USB drives, cloud folders...). */
    void pickFolder() {
        pendingTree = null;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_TREE);
        } catch (ActivityNotFoundException e) {
            snack("No folder picker on this phone");
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != REQ_TREE) return;
        if (result != RESULT_OK || data == null || data.getData() == null) {
            if (pendingTree != null) snack("Access wasn't granted");
            return;
        }
        Uri tree = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
        String id = DocumentsContract.getTreeDocumentId(tree);
        String name = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        if (name.isEmpty()) name = "Folder";
        push(new BrowserScreen(this, Loc.doc(tree, DocumentsContract.buildDocumentUriUsingTree(tree, id), name)));
        pendingTree = null;
    }
}
