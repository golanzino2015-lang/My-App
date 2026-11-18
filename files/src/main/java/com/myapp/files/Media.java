package com.myapp.files;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.MediaStore.Files.FileColumns;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Category lists (images, videos, documents, APKs...) from the system media index. */
final class Media {

    enum Cat {
        IMAGES("Images", R.drawable.ic_image),
        VIDEOS("Videos", R.drawable.ic_video),
        AUDIO("Audio", R.drawable.ic_music),
        DOCS("Documents", R.drawable.ic_file_text),
        APKS("APKs", R.drawable.ic_package),
        ARCHIVES("Archives", R.drawable.ic_archive),
        RECENT("Recent", R.drawable.ic_clock);

        final String title;
        final int icon;

        Cat(String title, int icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    static final String[] DOC_EXT = {"pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt",
            "ods", "odp", "csv", "epub", "md"};
    static final String[] APK_EXT = {"apk", "apks", "xapk", "apkm"};
    static final String[] ZIP_EXT = {"zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz"};

    private static String[] where(Cat cat, List<String> args) {
        String sel;
        switch (cat) {
            case IMAGES:
                sel = FileColumns.MEDIA_TYPE + "=" + FileColumns.MEDIA_TYPE_IMAGE;
                break;
            case VIDEOS:
                sel = FileColumns.MEDIA_TYPE + "=" + FileColumns.MEDIA_TYPE_VIDEO;
                break;
            case AUDIO:
                sel = FileColumns.MEDIA_TYPE + "=" + FileColumns.MEDIA_TYPE_AUDIO;
                break;
            case DOCS:
                sel = like(DOC_EXT, args);
                break;
            case APKS:
                sel = like(APK_EXT, args);
                break;
            case ARCHIVES:
                sel = like(ZIP_EXT, args);
                break;
            default: // recent: anything that isn't a folder, from the last 30 days
                sel = FileColumns.MIME_TYPE + " IS NOT NULL AND " + FileColumns.DATE_MODIFIED + ">?";
                args.add(String.valueOf(System.currentTimeMillis() / 1000 - 30L * 24 * 3600));
                break;
        }
        // hide files in hidden folders (thumbnail caches and the like)
        sel = "(" + sel + ") AND " + FileColumns.DATA + " NOT LIKE ?";
        args.add("%/.%");
        return new String[]{sel};
    }

    private static String like(String[] exts, List<String> args) {
        StringBuilder sb = new StringBuilder();
        for (String e : exts) {
            if (sb.length() > 0) sb.append(" OR ");
            sb.append(FileColumns.DATA).append(" LIKE ?");
            args.add("%." + e);
        }
        return sb.toString();
    }

    private static Cursor query(Context c, Cat cat, int limit) {
        List<String> args = new ArrayList<>();
        String sel = where(cat, args)[0];
        Bundle b = new Bundle();
        b.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, sel);
        b.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args.toArray(new String[0]));
        b.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, FileColumns.DATE_MODIFIED + " DESC");
        if (limit > 0) b.putInt(ContentResolver.QUERY_ARG_LIMIT, limit);
        Uri u = MediaStore.Files.getContentUri("external");
        String[] proj = {FileColumns.DATA, FileColumns.SIZE, FileColumns.DATE_MODIFIED, FileColumns.MIME_TYPE};
        try {
            return c.getContentResolver().query(u, proj, b, null);
        } catch (Exception e) {
            return null;
        }
    }

    static List<Item> list(Context c, Cat cat, int limit) {
        List<Item> out = new ArrayList<>();
        try (Cursor cur = query(c, cat, limit)) {
            if (cur == null) return out;
            while (cur.moveToNext()) {
                String path = cur.getString(0);
                if (path == null) continue;
                File f = new File(path);
                if (!f.isFile()) continue;
                Item i = new Item();
                i.file = f;
                i.name = f.getName();
                i.size = cur.isNull(1) ? f.length() : cur.getLong(1);
                i.modified = cur.isNull(2) ? f.lastModified() : cur.getLong(2) * 1000L;
                i.mime = cur.isNull(3) ? Item.mimeOf(i.name) : cur.getString(3);
                out.add(i);
            }
        }
        return out;
    }

    /** {count, total bytes} for a category. */
    static long[] stats(Context c, Cat cat) {
        long n = 0, bytes = 0;
        try (Cursor cur = query(c, cat, 0)) {
            if (cur == null) return new long[]{0, 0};
            while (cur.moveToNext()) {
                n++;
                if (!cur.isNull(1)) bytes += cur.getLong(1);
            }
        }
        return new long[]{n, bytes};
    }

    private Media() {}
}
