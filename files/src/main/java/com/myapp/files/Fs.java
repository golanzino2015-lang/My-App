package com.myapp.files;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.Settings;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Everything that touches the disk: listing, copy/move/delete/rename, zip, open and share. */
final class Fs {

    interface Progress {
        void step(String name);

        boolean cancelled();
    }

    static final String EXT_AUTH = "com.android.externalstorage.documents";

    static boolean hasAllFiles() {
        return Environment.isExternalStorageManager();
    }

    static void askAllFiles(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + a.getPackageName())));
        } catch (ActivityNotFoundException e) {
            a.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    // ---------------------------------------------------------------- listing
    /** Children of a folder, or null when the folder can't be read. */
    static List<Item> list(Context c, Loc l, boolean showHidden) {
        List<Item> out = new ArrayList<>();
        if (l.isFile()) {
            File[] arr = l.file.listFiles();
            if (arr == null) return null;
            for (File f : arr) {
                if (!showHidden && f.getName().startsWith(".")) continue;
                Item i = Item.of(f);
                if (i.dir) {
                    String[] names = f.list();
                    i.children = names == null ? -1 : names.length;
                }
                out.add(i);
            }
            return out;
        }
        ContentResolver cr = c.getContentResolver();
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(l.tree, DocumentsContract.getDocumentId(l.doc));
        String[] proj = {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
                Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED};
        try (Cursor cur = cr.query(children, proj, null, null, null)) {
            if (cur == null) return null;
            while (cur.moveToNext()) {
                Item i = new Item();
                i.tree = l.tree;
                i.doc = DocumentsContract.buildDocumentUriUsingTree(l.tree, cur.getString(0));
                i.name = cur.getString(1);
                String mime = cur.getString(2);
                i.dir = Document.MIME_TYPE_DIR.equals(mime);
                i.mime = i.dir ? null : (mime == null ? Item.mimeOf(i.name) : mime);
                i.size = cur.isNull(3) ? 0 : cur.getLong(3);
                i.modified = cur.isNull(4) ? 0 : cur.getLong(4);
                if (!showHidden && i.hidden()) continue;
                out.add(i);
            }
        } catch (Exception e) {
            return null;
        }
        return out;
    }

    static void sort(List<Item> items, int mode, boolean desc) {
        Comparator<Item> byName = (a, b) -> a.name.compareToIgnoreCase(b.name);
        Comparator<Item> cmp;
        switch (mode) {
            case Prefs.SORT_DATE:
                cmp = (a, b) -> Long.compare(a.modified, b.modified);
                break;
            case Prefs.SORT_SIZE:
                cmp = (a, b) -> Long.compare(a.dir ? a.children : a.size, b.dir ? b.children : b.size);
                break;
            case Prefs.SORT_TYPE:
                cmp = (a, b) -> a.ext().compareTo(b.ext());
                break;
            default:
                cmp = byName;
        }
        if (mode != Prefs.SORT_NAME) cmp = cmp.thenComparing(byName);
        if (desc) cmp = cmp.reversed();
        final Comparator<Item> inner = cmp;
        Collections.sort(items, (a, b) -> {
            if (a.dir != b.dir) return a.dir ? -1 : 1; // folders first
            return inner.compare(a, b);
        });
    }

    // ---------------------------------------------------------------- Android/data and obb
    static Uri restrictedTree(String sub) {
        return DocumentsContract.buildTreeDocumentUri(EXT_AUTH, "primary:Android/" + sub);
    }

    static Loc restrictedLoc(String sub) {
        Uri tree = restrictedTree(sub);
        return Loc.doc(tree, DocumentsContract.buildDocumentUriUsingTree(tree, "primary:Android/" + sub), "Android/" + sub);
    }

    static boolean hasTree(Context c, Uri tree) {
        for (UriPermission p : c.getContentResolver().getPersistedUriPermissions()) {
            if (p.getUri().equals(tree) && p.isReadPermission()) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- operations
    static Loc mkdir(Context c, Loc parent, String name) throws IOException {
        if (parent.isFile()) {
            File f = new File(parent.file, name);
            if (f.exists()) throw new IOException("\"" + name + "\" already exists");
            if (!f.mkdirs()) throw new IOException("Couldn't create the folder");
            return Loc.of(f);
        }
        Uri u = DocumentsContract.createDocument(c.getContentResolver(), parent.doc, Document.MIME_TYPE_DIR, name);
        if (u == null) throw new IOException("Couldn't create the folder");
        return Loc.doc(parent.tree, u, name);
    }

    static void newFile(Context c, Loc parent, String name) throws IOException {
        if (parent.isFile()) {
            File f = new File(parent.file, name);
            if (f.exists()) throw new IOException("\"" + name + "\" already exists");
            if (!f.createNewFile()) throw new IOException("Couldn't create the file");
            scan(c, f);
            return;
        }
        Uri u = DocumentsContract.createDocument(c.getContentResolver(), parent.doc, Item.mimeOf(name), name);
        if (u == null) throw new IOException("Couldn't create the file");
    }

    static void rename(Context c, Item i, String name) throws IOException {
        if (i.isFile()) {
            File nf = new File(i.file.getParentFile(), name);
            if (nf.exists()) throw new IOException("\"" + name + "\" already exists");
            if (!i.file.renameTo(nf)) throw new IOException("Couldn't rename");
            scan(c, i.file, nf);
            return;
        }
        try {
            if (DocumentsContract.renameDocument(c.getContentResolver(), i.doc, name) == null)
                throw new IOException("Couldn't rename");
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Couldn't rename");
        }
    }

    static void delete(Context c, Item i, Progress p) throws IOException {
        if (i.isFile()) {
            deleteFile(i.file, p);
            scan(c, i.file);
            return;
        }
        try {
            p.step(i.name);
            if (!DocumentsContract.deleteDocument(c.getContentResolver(), i.doc))
                throw new IOException("Couldn't delete " + i.name);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Couldn't delete " + i.name);
        }
    }

    private static void deleteFile(File f, Progress p) throws IOException {
        if (p.cancelled()) return;
        if (f.isDirectory() && !Files.isSymbolicLink(f.toPath())) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteFile(k, p);
        }
        p.step(f.getName());
        if (!f.delete() && f.exists()) throw new IOException("Couldn't delete " + f.getName());
    }

    /** Copy or move items into a folder. Names that already exist get a " (1)" suffix. */
    static void transfer(Context c, List<Item> src, Loc dest, boolean move, Progress p) throws IOException {
        for (Item s : src) {
            if (p.cancelled()) return;
            if (s.isFile() && dest.isFile()) {
                File parent = s.file.getParentFile();
                if (move && dest.file.equals(parent)) continue; // already there
                if (s.dir && inside(dest.file, s.file))
                    throw new IOException("Can't put \"" + s.name + "\" inside itself");
                File target = unique(dest.file, s.name);
                if (move && s.file.renameTo(target)) {
                    p.step(s.name);
                    scan(c, s.file, target);
                    continue;
                }
                copyFile(s.file, target, p);
                scan(c, target);
                if (move && !p.cancelled()) {
                    deleteFile(s.file, p);
                    scan(c, s.file);
                }
            } else {
                copyAny(c, s, dest, p);
                if (move && !p.cancelled()) delete(c, s, p);
            }
        }
    }

    private static boolean inside(File dir, File ancestor) {
        String a = ancestor.getAbsolutePath() + "/";
        String d = dir.getAbsolutePath() + "/";
        return d.startsWith(a);
    }

    static File unique(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        String base = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        for (int n = 1; ; n++) {
            f = new File(dir, base + " (" + n + ")" + ext);
            if (!f.exists()) return f;
        }
    }

    private static String uniqueName(Context c, Loc dest, String name) {
        if (dest.isFile()) return unique(dest.file, name).getName();
        Set<String> names = new HashSet<>();
        List<Item> l = list(c, dest, true);
        if (l != null) for (Item i : l) names.add(i.name);
        if (!names.contains(name)) return name;
        String base = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        for (int n = 1; ; n++) {
            String s = base + " (" + n + ")" + ext;
            if (!names.contains(s)) return s;
        }
    }

    private static void copyFile(File src, File dst, Progress p) throws IOException {
        if (p.cancelled()) return;
        if (src.isDirectory()) {
            if (!dst.mkdirs() && !dst.isDirectory()) throw new IOException("Couldn't create " + dst.getName());
            File[] kids = src.listFiles();
            if (kids != null) for (File k : kids) copyFile(k, new File(dst, k.getName()), p);
            return;
        }
        p.step(src.getName());
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            pipe(in, out, p);
        }
        //noinspection ResultOfMethodCallIgnored
        dst.setLastModified(src.lastModified());
    }

    private static void copyAny(Context c, Item s, Loc dest, Progress p) throws IOException {
        if (p.cancelled()) return;
        String name = uniqueName(c, dest, s.name);
        if (s.dir) {
            Loc nd = mkdir(c, dest, name);
            List<Item> kids = list(c, s.asLoc(), true);
            if (kids != null) for (Item k : kids) copyAny(c, k, nd, p);
            return;
        }
        p.step(s.name);
        try (InputStream in = open(c, s); OutputStream out = create(c, dest, name, s.mime)) {
            pipe(in, out, p);
        }
        if (dest.isFile()) scan(c, new File(dest.file, name));
    }

    static InputStream open(Context c, Item i) throws IOException {
        if (i.isFile()) return new FileInputStream(i.file);
        InputStream in = c.getContentResolver().openInputStream(i.doc);
        if (in == null) throw new IOException("Couldn't read " + i.name);
        return in;
    }

    private static OutputStream create(Context c, Loc dest, String name, String mime) throws IOException {
        if (dest.isFile()) return new FileOutputStream(new File(dest.file, name));
        Uri u = DocumentsContract.createDocument(c.getContentResolver(), dest.doc,
                mime == null ? "application/octet-stream" : mime, name);
        if (u == null) throw new IOException("Couldn't create " + name);
        OutputStream out = c.getContentResolver().openOutputStream(u);
        if (out == null) throw new IOException("Couldn't write " + name);
        return out;
    }

    private static void pipe(InputStream in, OutputStream out, Progress p) throws IOException {
        byte[] buf = new byte[128 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (p.cancelled()) throw new IOException("Cancelled");
            out.write(buf, 0, n);
        }
    }

    // ---------------------------------------------------------------- zip
    static File compress(Context c, List<Item> items, File dir, String name, Progress p) throws IOException {
        File zip = unique(dir, name.endsWith(".zip") ? name : name + ".zip");
        try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip)))) {
            for (Item i : items) {
                if (p.cancelled()) break;
                if (i.isFile()) zipFile(out, i.file, i.name, p);
            }
        }
        if (p.cancelled()) {
            //noinspection ResultOfMethodCallIgnored
            zip.delete();
        } else scan(c, zip);
        return zip;
    }

    private static void zipFile(ZipOutputStream out, File f, String path, Progress p) throws IOException {
        if (p.cancelled()) return;
        if (f.isDirectory()) {
            out.putNextEntry(new ZipEntry(path + "/"));
            out.closeEntry();
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) zipFile(out, k, path + "/" + k.getName(), p);
            return;
        }
        p.step(f.getName());
        ZipEntry e = new ZipEntry(path);
        e.setTime(f.lastModified());
        out.putNextEntry(e);
        try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
            pipe(in, out, p);
        }
        out.closeEntry();
    }

    /** Unzip into a new folder next to the archive. Returns that folder. */
    static File extract(Context c, File zip, Progress p) throws IOException {
        String base = zip.getName();
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        File dir = unique(zip.getParentFile(), base);
        if (!dir.mkdirs()) throw new IOException("Couldn't create " + dir.getName());
        String root = dir.getCanonicalPath() + File.separator;
        try (ZipInputStream in = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                if (p.cancelled()) break;
                File out = new File(dir, e.getName());
                if (!out.getCanonicalPath().startsWith(root)) continue; // zip-slip guard
                if (e.isDirectory()) {
                    //noinspection ResultOfMethodCallIgnored
                    out.mkdirs();
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null) //noinspection ResultOfMethodCallIgnored
                    parent.mkdirs();
                p.step(out.getName());
                try (OutputStream o = new BufferedOutputStream(new FileOutputStream(out))) {
                    pipe(in, o, p);
                }
                if (e.getTime() > 0) //noinspection ResultOfMethodCallIgnored
                    out.setLastModified(e.getTime());
            }
        }
        scan(c, dir);
        return dir;
    }

    // ---------------------------------------------------------------- sizes
    static long[] dirStats(File dir) {
        long size = 0, files = 0, folders = 0;
        ArrayDeque<File> q = new ArrayDeque<>();
        q.add(dir);
        while (!q.isEmpty()) {
            File d = q.poll();
            File[] kids = d.listFiles();
            if (kids == null) continue;
            for (File k : kids) {
                if (k.isDirectory()) {
                    folders++;
                    if (!Files.isSymbolicLink(k.toPath())) q.add(k);
                } else {
                    files++;
                    size += k.length();
                }
            }
        }
        return new long[]{size, files, folders};
    }

    // ---------------------------------------------------------------- formatting
    static String size(long b) {
        if (b < 1000) return b + " B";
        String[] u = {"KB", "MB", "GB", "TB"};
        double v = b;
        int i = -1;
        do {
            v /= 1000.0;
            i++;
        } while (v >= 1000 && i < u.length - 1);
        return (v >= 100 ? String.format(Locale.US, "%.0f", v) : v >= 10
                ? String.format(Locale.US, "%.1f", v) : String.format(Locale.US, "%.2f", v)) + " " + u[i];
    }

    static String date(long ms) {
        if (ms <= 0) return "";
        Calendar now = Calendar.getInstance(), t = Calendar.getInstance();
        t.setTimeInMillis(ms);
        Date d = new Date(ms);
        if (now.get(Calendar.YEAR) == t.get(Calendar.YEAR)) {
            if (now.get(Calendar.DAY_OF_YEAR) == t.get(Calendar.DAY_OF_YEAR))
                return "Today, " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(d);
            if (now.get(Calendar.DAY_OF_YEAR) - 1 == t.get(Calendar.DAY_OF_YEAR))
                return "Yesterday, " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(d);
            return new SimpleDateFormat("MMM d", Locale.getDefault()).format(d);
        }
        return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(d);
    }

    static String fullDate(long ms) {
        return ms <= 0 ? "Unknown" : new SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()).format(new Date(ms));
    }

    static String meta(Item i) {
        if (i.note != null) return i.note;
        String when = date(i.modified);
        if (i.dir) {
            String n = i.children < 0 ? "Folder" : i.children == 1 ? "1 item" : i.children + " items";
            return when.isEmpty() ? n : n + "  ·  " + when;
        }
        return when.isEmpty() ? size(i.size) : size(i.size) + "  ·  " + when;
    }

    static String count(int n, String one) {
        return n + " " + (n == 1 ? one : one + "s");
    }

    // ---------------------------------------------------------------- intents
    static Uri shareUri(Item i) {
        return i.isFile() ? FileShare.uri(i.file) : i.doc;
    }

    static void scan(Context c, File... files) {
        String[] paths = new String[files.length];
        for (int k = 0; k < files.length; k++) paths[k] = files[k].getAbsolutePath();
        MediaScannerConnection.scanFile(c.getApplicationContext(), paths, null, null);
    }

    /** Open with the default app; returns false when nothing on the phone can open it. */
    static boolean open(Activity a, Item i, boolean chooser) {
        if (i.kind() == Item.Kind.APK && "apk".equals(i.ext())) return install(a, i);
        Uri u = shareUri(i);
        String mime = i.mime == null ? Item.mimeOf(i.name) : i.mime;
        Intent v = new Intent(Intent.ACTION_VIEW).setDataAndType(u, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            a.startActivity(chooser ? Intent.createChooser(v, "Open with") : v);
            return true;
        } catch (ActivityNotFoundException e) {
            try {
                v.setDataAndType(u, "*/*");
                a.startActivity(Intent.createChooser(v, "Open with"));
                return true;
            } catch (ActivityNotFoundException e2) {
                return false;
            }
        }
    }

    static boolean install(Activity a, Item i) {
        if (!a.getPackageManager().canRequestPackageInstalls()) {
            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + a.getPackageName())));
            return true;
        }
        Intent v = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(shareUri(i), "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            a.startActivity(v);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    static void share(Activity a, List<Item> items) {
        List<Item> files = new ArrayList<>();
        for (Item i : items) if (!i.dir) files.add(i);
        if (files.isEmpty()) return;
        Intent s;
        if (files.size() == 1) {
            Item i = files.get(0);
            s = new Intent(Intent.ACTION_SEND).setType(i.mime == null ? "*/*" : i.mime)
                    .putExtra(Intent.EXTRA_STREAM, shareUri(i));
            s.setClipData(ClipData.newRawUri(i.name, shareUri(i)));
        } else {
            ArrayList<Uri> uris = new ArrayList<>();
            ClipData clip = null;
            String type = null;
            for (Item i : files) {
                Uri u = shareUri(i);
                uris.add(u);
                if (clip == null) clip = ClipData.newRawUri(i.name, u);
                else clip.addItem(new ClipData.Item(u));
                String m = i.mime == null ? "*/*" : i.mime;
                if (type == null) type = m;
                else if (!type.equals(m)) {
                    String t1 = type.split("/")[0], t2 = m.split("/")[0];
                    type = t1.equals(t2) ? t1 + "/*" : "*/*";
                }
            }
            s = new Intent(Intent.ACTION_SEND_MULTIPLE).setType(type)
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            s.setClipData(clip);
        }
        s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        a.startActivity(Intent.createChooser(s, files.size() == 1 ? "Share" : "Share " + files.size() + " files"));
    }

    private Fs() {}
}
