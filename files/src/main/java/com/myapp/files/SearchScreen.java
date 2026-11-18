package com.myapp.files;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Searches file and folder names under a folder (and everything inside it). */
final class SearchScreen extends ListScreen {
    private static final int MAX = 500;
    private final File scope;
    private EditText field;
    private volatile int token;
    private String query = "";
    private final Runnable run = () -> reload(true);

    SearchScreen(MainActivity a, File scope) {
        super(a);
        this.scope = scope;
    }

    @Override
    String titleText() {
        return "Search";
    }

    @Override
    int tab() {
        return MainActivity.TAB_FILES;
    }

    @Override
    boolean showInFolderAction() {
        return true;
    }

    @Override
    void addBelowHeader(LinearLayout col) {
        LinearLayout box = Ui.row(c);
        box.setBackground(Ui.card(c, 22));
        box.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 6), 0);
        box.addView(Ui.icon(c, R.drawable.ic_search, Ui.INK, 20), new LinearLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20)));
        field = new EditText(c);
        Ui.style(field, Ui.Type.BODY_M);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        field.setHint("Search in " + Loc.displayName(scope));
        field.setHintTextColor(Ui.INK_3);
        field.setBackground(null);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        field.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 4), 0);
        box.addView(field, Ui.weight(Ui.dp(c, 52)));
        FrameLayout clear = new FrameLayout(c);
        clear.addView(Ui.icon(c, R.drawable.ic_x, Ui.INK_2, 18));
        clear.setBackground(Ui.ripple(null, Ui.dp(c, 20)));
        clear.setOnClickListener(v -> field.setText(""));
        box.addView(clear, new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40)));
        LinearLayout.LayoutParams lp = Ui.lp(Ui.MATCH, Ui.dp(c, 54));
        lp.setMargins(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 10));
        col.addView(box, lp);

        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int d) {}

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString().trim();
                field.removeCallbacks(run);
                field.postDelayed(run, 280);
            }
        });
        field.setOnEditorActionListener((v, id, e) -> {
            hideKeyboard();
            return true;
        });
        field.postDelayed(() -> {
            field.requestFocus();
            InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
        }, 350);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
    }

    @Override
    public void open(Item i) {
        hideKeyboard();
        super.open(i);
    }

    @Override
    void onDestroy() {
        token++;
        hideKeyboard();
    }

    @Override
    void reload(boolean animate) {
        final String q = query.toLowerCase(Locale.ROOT);
        final int t = ++token;
        if (q.isEmpty()) {
            subtitle.setText("In " + Loc.displayName(scope));
            list.showEmpty(R.drawable.ic_search, "Find anything", "Type a file or folder name.", null, null);
            return;
        }
        subtitle.setText("Searching…");
        final boolean hidden = Prefs.hidden(c);
        final String rootPath = scope.getAbsolutePath();
        Bg.run(() -> {
            List<Item> out = new ArrayList<>();
            ArrayDeque<File> dirs = new ArrayDeque<>();
            dirs.add(scope);
            while (!dirs.isEmpty() && out.size() < MAX) {
                if (t != token) return null;
                File[] kids = dirs.poll().listFiles();
                if (kids == null) continue;
                for (File k : kids) {
                    String n = k.getName();
                    if (!hidden && n.startsWith(".")) continue;
                    boolean dir = k.isDirectory();
                    if (dir && !Files.isSymbolicLink(k.toPath())) dirs.add(k);
                    if (n.toLowerCase(Locale.ROOT).contains(q)) {
                        Item i = Item.of(k);
                        String parent = k.getParent() == null ? "" : k.getParent();
                        String rel = parent.startsWith(rootPath) ? parent.substring(rootPath.length()) : parent;
                        i.note = (dir ? "Folder" : Fs.size(i.size)) + "  ·  " + Loc.displayName(scope) + rel;
                        out.add(i);
                        if (out.size() >= MAX) break;
                    }
                }
            }
            Fs.sort(out, Prefs.SORT_NAME, false);
            return out;
        }, (items, err) -> {
            if (t != token || items == null) return;
            subtitle.setText(items.isEmpty() ? "No results" : items.size() >= MAX ? "First " + MAX + " results"
                    : Fs.count(items.size(), "result"));
            if (items.isEmpty()) {
                list.showEmpty(R.drawable.ic_search, "No matches", "Nothing named \"" + query + "\" here.", null, null);
                return;
            }
            show(items, animate);
        });
    }

    @Override
    void onShow() {
        // results stay as they are when coming back; refresh only if something may have changed
        if (!query.isEmpty()) reload(false);
        else reload(true);
    }
}
