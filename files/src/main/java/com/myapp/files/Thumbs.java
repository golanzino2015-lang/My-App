package com.myapp.files;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.provider.DocumentsContract;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Thumbnails for images, videos, music and APK icons; loaded off the main thread and cached. */
final class Thumbs {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(
            (int) Math.min(Runtime.getRuntime().maxMemory() / 8, 48L * 1024 * 1024)) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount();
        }
    };
    private static final Set<String> MISSING = new HashSet<>();

    interface Callback {
        void loaded(Bitmap b);
    }

    static boolean has(Item i) {
        Item.Kind k = i.kind();
        if (k == Item.Kind.IMAGE || k == Item.Kind.VIDEO) return true;
        if (!i.isFile()) return false;
        return k == Item.Kind.AUDIO || (k == Item.Kind.APK && "apk".equals(i.ext()));
    }

    /** Loads a thumbnail into the view, unless the view has since been reused for another item. */
    static void load(ImageView v, Item i, int px, Callback cb) {
        final String key = i.key() + "@" + px + "#" + i.modified;
        v.setTag(R.id.thumb_key, key);
        Bitmap hit = CACHE.get(key);
        if (hit != null) {
            cb.loaded(hit);
            return;
        }
        synchronized (MISSING) {
            if (MISSING.contains(key)) {
                cb.loaded(null);
                return;
            }
        }
        cb.loaded(null);
        final Context app = v.getContext().getApplicationContext();
        POOL.execute(() -> {
            Bitmap b = null;
            try {
                b = make(app, i, px);
            } catch (Throwable ignored) {
            }
            if (b == null) {
                synchronized (MISSING) {
                    MISSING.add(key);
                }
                return;
            }
            CACHE.put(key, b);
            final Bitmap fb = b;
            Bg.main(() -> {
                if (key.equals(v.getTag(R.id.thumb_key))) cb.loaded(fb);
            });
        });
    }

    private static Bitmap make(Context c, Item i, int px) throws Exception {
        Size s = new Size(px, px);
        Item.Kind k = i.kind();
        if (!i.isFile()) {
            return DocumentsContract.getDocumentThumbnail(c.getContentResolver(), i.doc, new Point(px, px), null);
        }
        switch (k) {
            case IMAGE:
                return ThumbnailUtils.createImageThumbnail(i.file, s, null);
            case VIDEO:
                return ThumbnailUtils.createVideoThumbnail(i.file, s, null);
            case AUDIO:
                return ThumbnailUtils.createAudioThumbnail(i.file, s, null);
            case APK: {
                PackageManager pm = c.getPackageManager();
                PackageInfo pi = pm.getPackageArchiveInfo(i.file.getAbsolutePath(), 0);
                if (pi == null || pi.applicationInfo == null) return null;
                ApplicationInfo ai = pi.applicationInfo;
                ai.sourceDir = ai.publicSourceDir = i.file.getAbsolutePath();
                return toBitmap(ai.loadIcon(pm), px);
            }
            default:
                return null;
        }
    }

    static Bitmap toBitmap(Drawable d, int px) {
        if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
            Bitmap b = ((BitmapDrawable) d).getBitmap();
            return b.getWidth() > px * 2 ? Bitmap.createScaledBitmap(b, px, px, true) : b;
        }
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        d.setBounds(0, 0, px, px);
        d.draw(cv);
        return b;
    }

    /** App icons for the app manager. */
    static void appIcon(ImageView v, ApplicationInfo ai, int px) {
        final String key = "app:" + ai.packageName + "@" + px;
        v.setTag(R.id.thumb_key, key);
        Bitmap hit = CACHE.get(key);
        if (hit != null) {
            v.setImageBitmap(hit);
            return;
        }
        v.setImageDrawable(null);
        final PackageManager pm = v.getContext().getPackageManager();
        POOL.execute(() -> {
            Bitmap b;
            try {
                b = toBitmap(ai.loadIcon(pm), px);
            } catch (Throwable t) {
                return;
            }
            CACHE.put(key, b);
            Bg.main(() -> {
                if (key.equals(v.getTag(R.id.thumb_key))) v.setImageBitmap(b);
            });
        });
    }

    static void forget() {
        CACHE.evictAll();
        synchronized (MISSING) {
            MISSING.clear();
        }
    }

    private Thumbs() {}
}
