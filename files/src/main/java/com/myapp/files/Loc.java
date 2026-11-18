package com.myapp.files;

import android.net.Uri;
import android.os.Environment;

import java.io.File;

/** A folder you can open: a real directory, or a folder inside a granted document tree. */
final class Loc {
    final File file;
    final Uri tree, doc;
    final String name;

    private Loc(File file, Uri tree, Uri doc, String name) {
        this.file = file;
        this.tree = tree;
        this.doc = doc;
        this.name = name;
    }

    static Loc of(File f) {
        return new Loc(f, null, null, displayName(f));
    }

    static Loc doc(Uri tree, Uri doc, String name) {
        return new Loc(null, tree, doc, name);
    }

    boolean isFile() {
        return file != null;
    }

    String key() {
        return file != null ? file.getAbsolutePath() : String.valueOf(doc);
    }

    static File root() {
        return Environment.getExternalStorageDirectory();
    }

    static String displayName(File f) {
        if (f.equals(root())) return "Internal storage";
        String n = f.getName();
        return n.isEmpty() ? f.getAbsolutePath() : n;
    }
}
