package com.myapp.files;

import java.util.List;

/** Every image / video / document / APK... on the phone, newest first. */
final class CategoryScreen extends ListScreen {
    private final Media.Cat cat;
    private int token;

    CategoryScreen(MainActivity a, Media.Cat cat) {
        super(a);
        this.cat = cat;
    }

    @Override
    String titleText() {
        return cat.title;
    }

    @Override
    boolean showInFolderAction() {
        return true;
    }

    @Override
    void reload(boolean animate) {
        if (!Fs.hasAllFiles()) {
            subtitle.setText("Access needed");
            list.showEmpty(R.drawable.ic_lock, "Allow access to your files",
                    "My Files needs permission to find your " + cat.title.toLowerCase() + ".",
                    "Allow access", () -> Fs.askAllFiles(a));
            return;
        }
        final int t = ++token;
        if (animate) subtitle.setText("Loading…");
        Bg.run(() -> Media.list(c, cat, cat == Media.Cat.RECENT ? 300 : 5000), (items, err) -> {
            if (t != token || items == null) return;
            long total = 0;
            for (Item i : items) total += i.size;
            subtitle.setText(items.isEmpty() ? "Nothing here yet" : Fs.count(items.size(), "file") + "  ·  " + Fs.size(total));
            if (items.isEmpty()) {
                list.showEmpty(cat.icon, "No " + cat.title.toLowerCase() + " found",
                        "Files you download or create will show up here.", null, null);
                return;
            }
            show(items, animate);
        });
    }
}
