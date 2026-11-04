package com.questionbubble.app;

import android.content.Context;
import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Saved conversations: one JSON file per chat, images stored once as JPEG files. */
final class Chats {

    static final class Msg {
        final String role;          // "user" | "assistant"
        final String text;
        final List<String> images;  // image file names (user messages)
        final String provider, model;
        final String raw;           // Claude: the full response JSON, replayed unchanged
        final long time;

        Msg(String role, String text, List<String> images, String provider, String model, String raw, long time) {
            this.role = role;
            this.text = text == null ? "" : text;
            this.images = images == null ? new ArrayList<>() : images;
            this.provider = provider == null ? "" : provider;
            this.model = model == null ? "" : model;
            this.raw = raw;
            this.time = time;
        }

        boolean isUser() {
            return "user".equals(role);
        }
    }

    static final class Chat {
        final String id;
        String title;
        final long created;
        long updated;
        final List<Msg> messages = new ArrayList<>();

        Chat(String id, String title, long created, long updated) {
            this.id = id;
            this.title = title;
            this.created = created;
            this.updated = updated;
        }

        String preview() {
            for (int i = messages.size() - 1; i >= 0; i--) {
                String t = messages.get(i).text;
                if (!t.isEmpty()) return MainActivity.firstLine(t);
            }
            return messages.isEmpty() ? "" : "Image";
        }
    }

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "chats");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static File imgDir(Context c) {
        File d = new File(dir(c), "img");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static File imageFile(Context c, String name) {
        return new File(imgDir(c), name);
    }

    static Chat create() {
        long now = System.currentTimeMillis();
        return new Chat(UUID.randomUUID().toString(), "", now, now);
    }

    /** Saves a picture as a JPEG (max 1600px) and returns its file name. */
    static String saveImage(Context c, Bitmap src) throws Exception {
        Bitmap bmp = src;
        int max = Math.max(src.getWidth(), src.getHeight());
        if (max > 1600) {
            float s = 1600f / max;
            bmp = Bitmap.createScaledBitmap(src, Math.max(1, Math.round(src.getWidth() * s)),
                    Math.max(1, Math.round(src.getHeight() * s)), true);
        }
        String name = UUID.randomUUID() + ".jpg";
        try (FileOutputStream out = new FileOutputStream(imageFile(c, name))) {
            bmp.compress(Bitmap.CompressFormat.JPEG, 86, out);
        }
        if (bmp != src) bmp.recycle();
        return name;
    }

    /** Base64 of the stored file: the exact same bytes every time the chat is sent. */
    static String imageBase64(Context c, String name) throws Exception {
        byte[] bytes = Files.readAllBytes(imageFile(c, name).toPath());
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
    }

    static synchronized void save(Context c, Chat chat) {
        try {
            JSONObject o = new JSONObject()
                    .put("id", chat.id).put("title", chat.title)
                    .put("created", chat.created).put("updated", chat.updated);
            JSONArray arr = new JSONArray();
            for (Msg m : chat.messages) {
                JSONObject mo = new JSONObject().put("role", m.role).put("text", m.text)
                        .put("provider", m.provider).put("model", m.model).put("time", m.time);
                JSONArray imgs = new JSONArray();
                for (String i : m.images) imgs.put(i);
                mo.put("images", imgs);
                if (m.raw != null) mo.put("raw", m.raw);
                arr.put(mo);
            }
            o.put("messages", arr);
            File f = new File(dir(c), chat.id + ".json");
            File tmp = new File(dir(c), chat.id + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(o.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(f)) {
                f.delete();
                tmp.renameTo(f);
            }
        } catch (Exception ignored) {
        }
    }

    static synchronized Chat load(Context c, String id) {
        try {
            File f = new File(dir(c), id + ".json");
            if (!f.exists()) return null;
            JSONObject o = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            Chat chat = new Chat(o.getString("id"), o.optString("title", ""),
                    o.optLong("created", 0), o.optLong("updated", 0));
            JSONArray arr = o.optJSONArray("messages");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject m = arr.getJSONObject(i);
                    List<String> imgs = new ArrayList<>();
                    JSONArray ia = m.optJSONArray("images");
                    if (ia != null) for (int j = 0; j < ia.length(); j++) imgs.add(ia.getString(j));
                    chat.messages.add(new Msg(m.optString("role"), m.optString("text"), imgs,
                            m.optString("provider"), m.optString("model"),
                            m.has("raw") ? m.optString("raw") : null, m.optLong("time")));
                }
            }
            return chat;
        } catch (Exception e) {
            return null;
        }
    }

    /** All chats with at least one message, newest first. */
    static List<Chat> list(Context c) {
        List<Chat> out = new ArrayList<>();
        File[] files = dir(c).listFiles((d, n) -> n.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                Chat ch = load(c, f.getName().substring(0, f.getName().length() - 5));
                if (ch != null && !ch.messages.isEmpty()) out.add(ch);
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(b.updated, a.updated));
        return out;
    }

    static synchronized void delete(Context c, String id) {
        Chat chat = load(c, id);
        if (chat != null) {
            for (Msg m : chat.messages) for (String i : m.images) imageFile(c, i).delete();
        }
        new File(dir(c), id + ".json").delete();
    }

    private Chats() {}
}
