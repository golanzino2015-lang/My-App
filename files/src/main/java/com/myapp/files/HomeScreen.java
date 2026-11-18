package com.myapp.files;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Dashboard: storage, categories, places, and recent files. */
final class HomeScreen extends Screen {
    private LinearLayout content;
    private TextView usedText, totalText, freeText, pctText;
    private Meter meter;
    private View permCard;
    private final Map<Media.Cat, TextView> counts = new EnumMap<>(Media.Cat.class);
    private TextView appsCount;
    private LinearLayout recentRow, placesCard;
    private View recentSection;
    private boolean built;

    HomeScreen(MainActivity a) {
        super(a);
    }

    @Override
    View build() {
        ScrollView sv = new ScrollView(c);
        sv.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        content = Ui.col(c);
        content.setPadding(Ui.dp(c, 20), Ui.dp(c, 14), Ui.dp(c, 20), Ui.dp(c, 130));
        sv.addView(content);
        Ui.insets(content, true, true, false);

        // header
        LinearLayout head = Ui.row(c);
        LinearLayout titles = Ui.col(c);
        titles.addView(Ui.text(c, greeting(), Ui.Type.OVERLINE));
        TextView big = Ui.text(c, "My Files", Ui.Type.DISPLAY);
        titles.addView(big, Ui.lp(Ui.WRAP, Ui.WRAP, Ui.dp(c, 8)));
        head.addView(titles, Ui.weight(Ui.WRAP));
        head.addView(Ui.circleButton(c, R.drawable.ic_search, 48, v -> a.push(new SearchScreen(a, Loc.root()))));
        content.addView(head);
        Ui.enter(head, 0);

        View hero = hero();
        content.addView(hero, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 22)));
        Ui.enter(hero, 60);

        permCard = permissionCard();
        content.addView(permCard, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 14)));

        content.addView(section("Categories", null, null), Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 28)));
        View grid = categories();
        content.addView(grid, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 14)));
        Ui.enter(grid, 120);

        content.addView(section("Places", null, null), Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 30)));
        placesCard = Ui.col(c);
        placesCard.setBackground(Ui.card(c, 24));
        placesCard.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4));
        content.addView(placesCard, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 14)));
        buildPlaces();
        Ui.enter(placesCard, 180);

        LinearLayout rs = Ui.col(c);
        rs.addView(section("Recent", "See all", () -> a.push(new CategoryScreen(a, Media.Cat.RECENT))));
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recentRow = Ui.row(c);
        recentRow.setPadding(Ui.dp(c, 20), Ui.dp(c, 2), Ui.dp(c, 20), Ui.dp(c, 16));
        hs.addView(recentRow);
        LinearLayout.LayoutParams hp = Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 14));
        hp.leftMargin = hp.rightMargin = -Ui.dp(c, 20);
        rs.addView(hs, hp);
        recentSection = rs;
        recentSection.setVisibility(View.GONE);
        content.addView(rs, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 30)));
        built = true;
        return sv;
    }

    private static String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h < 5 ? "Good night" : h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
    }

    // ---------------------------------------------------------------- hero
    private View hero() {
        LinearLayout card = Ui.col(c);
        card.setBackground(Ui.hero(c, 28));
        card.setPadding(Ui.dp(c, 22), Ui.dp(c, 20), Ui.dp(c, 22), Ui.dp(c, 22));
        Ui.elevate(card, 16, 28);

        LinearLayout top = Ui.row(c);
        FrameLayout t = new FrameLayout(c);
        t.setBackground(Ui.rounded(0x1FFFFFFF, Ui.dp(c, 13), 0x40FFFFFF, Ui.stroke(c)));
        t.addView(Ui.icon(c, R.drawable.ic_phone, Ui.WHITE, 20));
        top.addView(t, new LinearLayout.LayoutParams(Ui.dp(c, 42), Ui.dp(c, 42)));
        LinearLayout tt = Ui.col(c);
        tt.setPadding(Ui.dp(c, 12), 0, 0, 0);
        TextView name = Ui.text(c, "Internal storage", Ui.Type.TITLE_S);
        name.setTextColor(Ui.WHITE);
        tt.addView(name);
        TextView sub = Ui.text(c, "Tap to browse", Ui.Type.CAPTION);
        sub.setTextColor(0x99FFFFFF);
        tt.addView(sub, Ui.lp(Ui.WRAP, Ui.WRAP, Ui.dp(c, 4)));
        top.addView(tt, Ui.weight(Ui.WRAP));
        top.addView(Ui.icon(c, R.drawable.ic_chevron_right, 0xB3FFFFFF, 22), new LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22)));
        card.addView(top);

        LinearLayout nums = Ui.row(c);
        nums.setGravity(Gravity.BOTTOM);
        usedText = Ui.text(c, "—", Ui.Type.DISPLAY);
        usedText.setTextColor(Ui.WHITE);
        usedText.setTextSize(38);
        nums.addView(usedText);
        totalText = Ui.text(c, "", Ui.Type.BODY_M);
        totalText.setTextColor(0x99FFFFFF);
        totalText.setPadding(Ui.dp(c, 8), 0, 0, Ui.dp(c, 5));
        nums.addView(totalText, Ui.weight(Ui.WRAP));
        pctText = Ui.text(c, "", Ui.Type.LABEL);
        pctText.setTextColor(Ui.INK);
        pctText.setBackground(Ui.rounded(Ui.WHITE, Ui.dp(c, 12)));
        pctText.setPadding(Ui.dp(c, 10), Ui.dp(c, 5), Ui.dp(c, 10), Ui.dp(c, 5));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        pp.bottomMargin = Ui.dp(c, 6);
        nums.addView(pctText, pp);
        card.addView(nums, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 22)));

        meter = new Meter(c, true);
        card.addView(meter, Ui.lp(Ui.MATCH, Ui.dp(c, 10), Ui.dp(c, 16)));
        freeText = Ui.text(c, "", Ui.Type.CAPTION);
        freeText.setTextColor(0xB3FFFFFF);
        card.addView(freeText, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 12)));

        card.setOnClickListener(v -> {
            Ui.tap(v);
            a.setTab(MainActivity.TAB_FILES);
        });
        Ui.pressable(card, 0.98f);
        return card;
    }

    private void updateStorage() {
        try {
            StatFs st = new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());
            long total = st.getTotalBytes(), free = st.getAvailableBytes(), used = total - free;
            usedText.setText(Fs.size(used));
            totalText.setText("of " + Fs.size(total));
            int pct = total > 0 ? Math.round(used * 100f / total) : 0;
            pctText.setText(pct + "%");
            freeText.setText(Fs.size(free) + " free");
            meter.set(total > 0 ? used / (float) total : 0f, true);
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- permission
    private View permissionCard() {
        LinearLayout card = Ui.col(c);
        card.setBackground(Ui.card(c, 24));
        card.setPadding(Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18));
        LinearLayout row = Ui.row(c);
        row.addView(Ui.iconTile(c, R.drawable.ic_lock, 44), new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));
        LinearLayout col = Ui.col(c);
        col.setPadding(Ui.dp(c, 14), 0, 0, 0);
        col.addView(Ui.text(c, "Allow access to files", Ui.Type.TITLE_S));
        TextView m = Ui.text(c, "Needed to browse, copy, move and delete your files.", Ui.Type.CAPTION);
        col.addView(m, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 5)));
        row.addView(col, Ui.weight(Ui.WRAP));
        card.addView(row);
        TextView b = Ui.pill(c, "Allow access", true, v -> Fs.askAllFiles(a));
        card.addView(b, Ui.lp(Ui.MATCH, Ui.dp(c, 48), Ui.dp(c, 16)));
        return card;
    }

    // ---------------------------------------------------------------- categories
    private View categories() {
        LinearLayout grid = Ui.col(c);
        Object[][] cells = {
                {"Downloads", R.drawable.ic_download, (Runnable) () -> a.openFolder(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)), null},
                {"Images", R.drawable.ic_image, (Runnable) () -> a.push(new CategoryScreen(a, Media.Cat.IMAGES)), Media.Cat.IMAGES},
                {"Videos", R.drawable.ic_video, (Runnable) () -> a.push(new CategoryScreen(a, Media.Cat.VIDEOS)), Media.Cat.VIDEOS},
                {"Audio", R.drawable.ic_music, (Runnable) () -> a.push(new CategoryScreen(a, Media.Cat.AUDIO)), Media.Cat.AUDIO},
                {"Docs", R.drawable.ic_file_text, (Runnable) () -> a.push(new CategoryScreen(a, Media.Cat.DOCS)), Media.Cat.DOCS},
                {"APKs", R.drawable.ic_package, (Runnable) () -> a.push(new CategoryScreen(a, Media.Cat.APKS)), Media.Cat.APKS},
                {"Archives", R.drawable.ic_archive, (Runnable) () -> a.push(new CategoryScreen(a, Media.Cat.ARCHIVES)), Media.Cat.ARCHIVES},
                {"Apps", R.drawable.ic_grid, (Runnable) () -> a.setTab(MainActivity.TAB_APPS), "apps"},
        };
        LinearLayout row = null;
        for (int i = 0; i < cells.length; i++) {
            if (i % 4 == 0) {
                row = Ui.row(c);
                row.setGravity(Gravity.TOP);
                grid.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP, i == 0 ? 0 : Ui.dp(c, 12)));
            }
            Object[] cell = cells[i];
            LinearLayout box = Ui.col(c);
            box.setGravity(Gravity.CENTER_HORIZONTAL);
            box.setPadding(0, Ui.dp(c, 14), 0, Ui.dp(c, 12));
            box.setBackground(Ui.ripple(Ui.card(c, 22), Ui.dp(c, 22)));
            FrameLayout tile = new FrameLayout(c);
            tile.setBackground(i == 0 ? Ui.ink(c, 16) : Ui.tile(c, 48));
            tile.addView(Ui.icon(c, (Integer) cell[1], i == 0 ? Ui.WHITE : Ui.INK, 23));
            box.addView(tile, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
            TextView label = Ui.oneLine(Ui.text(c, (String) cell[0], Ui.Type.LABEL));
            label.setGravity(Gravity.CENTER);
            box.addView(label, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 10)));
            TextView count = Ui.oneLine(Ui.text(c, " ", Ui.Type.CAPTION));
            count.setTextSize(11);
            count.setGravity(Gravity.CENTER);
            box.addView(count, Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 3)));
            if (cell[3] instanceof Media.Cat) counts.put((Media.Cat) cell[3], count);
            else if ("apps".equals(cell[3])) appsCount = count;
            else count.setText("Folder");
            Runnable r = (Runnable) cell[2];
            box.setOnClickListener(v -> {
                Ui.tap(v);
                r.run();
            });
            Ui.pressable(box, 0.94f);
            LinearLayout.LayoutParams lp = Ui.weight(Ui.WRAP);
            if (i % 4 != 0) lp.leftMargin = Ui.dp(c, 10);
            row.addView(box, lp);
        }
        return grid;
    }

    private void updateCounts() {
        if (!Fs.hasAllFiles()) return;
        Bg.run(() -> {
            Map<Media.Cat, long[]> m = new EnumMap<>(Media.Cat.class);
            for (Media.Cat cat : counts.keySet()) m.put(cat, Media.stats(c, cat));
            return m;
        }, (m, err) -> {
            if (m == null) return;
            for (Map.Entry<Media.Cat, long[]> e : m.entrySet()) {
                TextView t = counts.get(e.getKey());
                if (t != null) t.setText(String.valueOf(e.getValue()[0]));
            }
        });
        Bg.run(() -> AppsScreen.countUserApps(c), (n, err) -> {
            if (n != null && appsCount != null) appsCount.setText(String.valueOf(n));
        });
    }

    // ---------------------------------------------------------------- places
    private void buildPlaces() {
        placesCard.removeAllViews();
        File root = Loc.root();
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{R.drawable.ic_phone, "Internal storage", "All your files", (Runnable) () -> a.openFolder(root)});
        StorageManager sm = (StorageManager) c.getSystemService(Context.STORAGE_SERVICE);
        if (sm != null) {
            for (StorageVolume v : sm.getStorageVolumes()) {
                File dir = v.getDirectory();
                if (v.isPrimary() || dir == null || !Environment.MEDIA_MOUNTED.equals(v.getState())) continue;
                String label = v.getDescription(c);
                rows.add(new Object[]{R.drawable.ic_sd, label == null ? "SD card" : label, dir.getAbsolutePath(),
                        (Runnable) () -> a.openFolder(dir)});
            }
        }
        rows.add(new Object[]{R.drawable.ic_download, "Downloads", "Download",
                (Runnable) () -> a.openFolder(new File(root, Environment.DIRECTORY_DOWNLOADS))});
        rows.add(new Object[]{R.drawable.ic_camera, "Camera", "DCIM/Camera",
                (Runnable) () -> a.openFolder(new File(root, "DCIM/Camera").isDirectory() ? new File(root, "DCIM/Camera") : new File(root, "DCIM"))});
        rows.add(new Object[]{R.drawable.ic_image, "Screenshots", "Pictures/Screenshots",
                (Runnable) () -> {
                    File s1 = new File(root, "Pictures/Screenshots"), s2 = new File(root, "DCIM/Screenshots");
                    a.openFolder(s1.isDirectory() ? s1 : s2.isDirectory() ? s2 : new File(root, "Pictures"));
                }});
        rows.add(new Object[]{R.drawable.ic_file_text, "Documents", "Documents",
                (Runnable) () -> a.openFolder(new File(root, Environment.DIRECTORY_DOCUMENTS))});
        rows.add(new Object[]{R.drawable.ic_layers, "App data", "Android/data",
                (Runnable) () -> a.openRestricted("data")});
        rows.add(new Object[]{R.drawable.ic_package, "Game data", "Android/obb",
                (Runnable) () -> a.openRestricted("obb")});
        rows.add(new Object[]{R.drawable.ic_external, "Other location", "USB drive, cloud folders…",
                (Runnable) () -> a.pickFolder()});
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            if (i > 0) {
                View d = new View(c);
                d.setBackgroundColor(Ui.LINE_SOFT);
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.MATCH, Math.max(1, Ui.dp(c, 0.7f)));
                dl.leftMargin = Ui.dp(c, 70);
                placesCard.addView(d, dl);
            }
            LinearLayout row = Ui.row(c);
            row.setPadding(Ui.dp(c, 14), Ui.dp(c, 11), Ui.dp(c, 14), Ui.dp(c, 11));
            row.setBackground(Ui.ripple(null, Ui.dp(c, 22)));
            row.addView(Ui.iconTile(c, (Integer) r[0], 42), new LinearLayout.LayoutParams(Ui.dp(c, 42), Ui.dp(c, 42)));
            LinearLayout col = Ui.col(c);
            col.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 8), 0);
            col.addView(Ui.oneLine(Ui.text(c, (String) r[1], Ui.Type.TITLE_S)));
            col.addView(Ui.oneLine(Ui.text(c, (String) r[2], Ui.Type.CAPTION)), Ui.lp(Ui.MATCH, Ui.WRAP, Ui.dp(c, 4)));
            row.addView(col, Ui.weight(Ui.WRAP));
            row.addView(Ui.icon(c, R.drawable.ic_chevron_right, Ui.INK_3, 18), new LinearLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18)));
            Runnable run = (Runnable) r[3];
            row.setOnClickListener(v -> {
                Ui.tap(v);
                run.run();
            });
            placesCard.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
    }

    // ---------------------------------------------------------------- recent
    private void updateRecent() {
        if (!Fs.hasAllFiles()) {
            recentSection.setVisibility(View.GONE);
            return;
        }
        Bg.run(() -> Media.list(c, Media.Cat.RECENT, 12), (items, err) -> {
            if (items == null || items.isEmpty()) {
                recentSection.setVisibility(View.GONE);
                return;
            }
            boolean first = recentSection.getVisibility() != View.VISIBLE;
            recentSection.setVisibility(View.VISIBLE);
            recentRow.removeAllViews();
            for (int i = 0; i < items.size(); i++) recentRow.addView(recentCard(items.get(i), i));
            if (first) Ui.enter(recentSection, 0);
        });
    }

    private View recentCard(Item it, int index) {
        LinearLayout card = Ui.col(c);
        card.setBackground(Ui.ripple(Ui.card(c, 22), Ui.dp(c, 22)));
        card.setPadding(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 12));
        FrameLayout pic = new FrameLayout(c);
        pic.setBackground(Ui.tile(c, 48));
        int in = Ui.stroke(c);
        pic.setPadding(in, in, in, in);
        pic.addView(Ui.icon(c, it.kind().icon, Ui.INK, 30));
        if (Thumbs.has(it)) {
            ImageView th = new ImageView(c);
            th.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Ui.clip(th, 15);
            th.setVisibility(View.GONE);
            pic.addView(th, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            boolean apk = it.kind() == Item.Kind.APK;
            Thumbs.load(th, it, Ui.dp(c, 160), b -> {
                if (b == null) return;
                if (apk) {
                    th.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    int p = Ui.dp(c, 22);
                    th.setPadding(p, p, p, p);
                }
                th.setImageBitmap(b);
                th.setVisibility(View.VISIBLE);
            });
        }
        card.addView(pic, new LinearLayout.LayoutParams(Ui.dp(c, 128), Ui.dp(c, 112)));
        TextView n = Ui.middle(Ui.text(c, it.name, Ui.Type.LABEL));
        n.setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
        card.addView(n, new LinearLayout.LayoutParams(Ui.dp(c, 128), Ui.WRAP));
        ((LinearLayout.LayoutParams) n.getLayoutParams()).topMargin = Ui.dp(c, 10);
        TextView m = Ui.oneLine(Ui.text(c, Fs.date(it.modified), Ui.Type.CAPTION));
        m.setTextSize(11);
        m.setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
        card.addView(m, Ui.lp(Ui.dp(c, 128), Ui.WRAP, Ui.dp(c, 3)));
        card.setOnClickListener(v -> {
            Ui.tap(v);
            if (!Fs.open(a, it, false)) a.snack("No app can open this file");
        });
        card.setOnLongClickListener(v -> {
            Ui.longPress(v);
            if (it.file.getParentFile() != null) a.openFolder(it.file.getParentFile());
            return true;
        });
        Ui.pressable(card, 0.95f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        if (index > 0) lp.leftMargin = Ui.dp(c, 12);
        card.setLayoutParams(lp);
        return card;
    }

    // ---------------------------------------------------------------- lifecycle
    private View section(String title, String action, Runnable r) {
        LinearLayout row = Ui.row(c);
        TextView t = Ui.text(c, title, Ui.Type.TITLE);
        t.setTypeface(Fonts.display(c));
        row.addView(t, Ui.weight(Ui.WRAP));
        if (action != null) {
            TextView a2 = Ui.text(c, action, Ui.Type.LABEL);
            a2.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
            a2.setBackground(Ui.ripple(Ui.rounded(Ui.WHITE, Ui.dp(c, 14), Ui.LINE, Ui.stroke(c)), Ui.dp(c, 14)));
            a2.setOnClickListener(v -> {
                Ui.tap(v);
                r.run();
            });
            row.addView(a2);
        }
        return row;
    }

    @Override
    void onShow() {
        if (!built) return;
        permCard.setVisibility(Fs.hasAllFiles() ? View.GONE : View.VISIBLE);
        updateStorage();
        updateCounts();
        updateRecent();
    }
}
