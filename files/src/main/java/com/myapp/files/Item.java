package com.myapp.files;

import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.util.Locale;

/** One entry in a list: a real file (file != null) or a Storage Access Framework document. */
final class Item {

    enum Kind {
        FOLDER(R.drawable.ic_folder, "Folder"),
        IMAGE(R.drawable.ic_image, "Image"),
        VIDEO(R.drawable.ic_video, "Video"),
        AUDIO(R.drawable.ic_music, "Audio"),
        DOC(R.drawable.ic_file_text, "Document"),
        ARCHIVE(R.drawable.ic_archive, "Archive"),
        APK(R.drawable.ic_package, "Android app"),
        OTHER(R.drawable.ic_file, "File");

        final int icon;
        final String label;

        Kind(int icon, String label) {
            this.icon = icon;
            this.label = label;
        }
    }

    String name;
    boolean dir;
    long size;
    long modified; // millis
    String mime;
    File file;
    Uri tree, doc;
    int children = -1;
    String note; // optional subtitle override (e.g. the folder a search result lives in)

    static Item of(File f) {
        Item i = new Item();
        i.file = f;
        i.name = f.getName();
        i.dir = f.isDirectory();
        i.modified = f.lastModified();
        if (!i.dir) i.size = f.length();
        i.mime = i.dir ? null : mimeOf(i.name);
        return i;
    }

    boolean isFile() {
        return file != null;
    }

    String key() {
        return file != null ? file.getAbsolutePath() : String.valueOf(doc);
    }

    String ext() {
        return extOf(name);
    }

    Kind kind() {
        if (dir) return Kind.FOLDER;
        return kindOf(name, mime);
    }

    boolean hidden() {
        return name != null && name.startsWith(".");
    }

    Loc asLoc() {
        return file != null ? Loc.of(file) : Loc.doc(tree, doc, name);
    }

    // ---------------------------------------------------------------- types
    static String extOf(String name) {
        if (name == null) return "";
        int i = name.lastIndexOf('.');
        return i > 0 && i < name.length() - 1 ? name.substring(i + 1).toLowerCase(Locale.ROOT) : "";
    }

    static String mimeOf(String name) {
        String ext = extOf(name);
        switch (ext) {
            case "apk":
            case "apks":
            case "xapk":
            case "apkm":
                return "application/vnd.android.package-archive";
            case "md":
                return "text/markdown";
            case "log":
            case "ini":
            case "conf":
            case "cfg":
                return "text/plain";
            case "json":
                return "application/json";
            case "kt":
            case "java":
            case "py":
            case "js":
            case "ts":
            case "sh":
                return "text/plain";
        }
        String m = ext.isEmpty() ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return m != null ? m : "application/octet-stream";
    }

    static Kind kindOf(String name, String mime) {
        String ext = extOf(name);
        switch (ext) {
            case "apk":
            case "apks":
            case "xapk":
            case "apkm":
                return Kind.APK;
            case "zip":
            case "rar":
            case "7z":
            case "tar":
            case "gz":
            case "tgz":
            case "bz2":
            case "xz":
                return Kind.ARCHIVE;
            case "pdf":
            case "doc":
            case "docx":
            case "xls":
            case "xlsx":
            case "ppt":
            case "pptx":
            case "txt":
            case "rtf":
            case "odt":
            case "ods":
            case "odp":
            case "csv":
            case "epub":
            case "md":
            case "json":
            case "xml":
            case "html":
            case "htm":
            case "log":
                return Kind.DOC;
        }
        if (mime == null) mime = mimeOf(name);
        if (mime.startsWith("image/")) return Kind.IMAGE;
        if (mime.startsWith("video/")) return Kind.VIDEO;
        if (mime.startsWith("audio/")) return Kind.AUDIO;
        if (mime.startsWith("text/")) return Kind.DOC;
        return Kind.OTHER;
    }
}
