package com.questionbubble.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Cloud backup of the brain in your own private GitHub repository. Each backup is a pair of files
 * (brain-TIME.bin + brain-TIME.json) attached to a release named "my-ai-brain". The two newest
 * complete pairs are kept. Call from a background thread.
 */
final class Backup {

    private static final String API = "https://api.github.com", UPLOADS = "https://uploads.github.com";
    private static final String TAG = "my-ai-brain";
    private static final int KEEP = 2;

    /** "https://github.com/me/my-ai-brain" or "me/my-ai-brain" -> "me/my-ai-brain". A bare name gets your username. */
    static String normalize(String repo, String token) throws Exception {
        String r = repo.trim().replaceFirst("^https?://github\\.com/", "").replaceAll("\\.git$", "").replaceAll("/+$", "");
        if (r.isEmpty()) return r;
        if (!r.contains("/")) {
            JSONObject me = new JSONObject(text(call("GET", API + "/user", token, null, null)));
            r = me.getString("login") + "/" + r;
        }
        return r;
    }

    /** Checks the key and repository, creating the backup slot if needed. Returns {has_brain, saved_at}. */
    static JSONObject hello(String repo, String token) throws Exception {
        long id = release(repo, token);
        TreeMap<Long, long[]> pairs = pairs(repo, token, id);
        JSONObject r = new JSONObject().put("ok", true).put("has_brain", !complete(pairs).isEmpty());
        List<Long> c = complete(pairs);
        if (!c.isEmpty()) r.put("saved_at", c.get(c.size() - 1));
        return r;
    }

    static void upload(String repo, String token, File brain, File meta) throws Exception {
        long id = release(repo, token);
        long t = System.currentTimeMillis();
        // the brain first, then the small details file: a backup only counts once both have arrived
        call("POST", UPLOADS + "/repos/" + repo + "/releases/" + id + "/assets?name=brain-" + t + ".bin", token, brain, null);
        call("POST", UPLOADS + "/repos/" + repo + "/releases/" + id + "/assets?name=brain-" + t + ".json", token, meta, null);
        TreeMap<Long, long[]> pairs = pairs(repo, token, id);
        List<Long> keep = complete(pairs);
        keep = keep.subList(Math.max(0, keep.size() - KEEP), keep.size());
        for (java.util.Map.Entry<Long, long[]> e : pairs.entrySet()) {
            if (keep.contains(e.getKey())) continue;
            for (long asset : e.getValue()) {
                if (asset != 0) call("DELETE", API + "/repos/" + repo + "/releases/assets/" + asset, token, null, null);
            }
        }
    }

    static void download(String repo, String token, File brainDest, File metaDest) throws Exception {
        long id = release(repo, token);
        TreeMap<Long, long[]> pairs = pairs(repo, token, id);
        List<Long> c = complete(pairs);
        if (c.isEmpty()) throw new Exception("No brain is saved in the cloud yet");
        long[] p = pairs.get(c.get(c.size() - 1));
        File tb = new File(brainDest.getPath() + ".dl"), tm = new File(metaDest.getPath() + ".dl");
        fetchAsset(repo, token, p[0], tb);
        fetchAsset(repo, token, p[1], tm);
        if (!tb.renameTo(brainDest) || !tm.renameTo(metaDest)) throw new IOException("Couldn't save the brain on the phone");
    }

    // ---------------------------------------------------------------- GitHub details

    /** The release that holds the backups; made on first use. */
    private static long release(String repo, String token) throws Exception {
        try {
            return new JSONObject(text(call("GET", API + "/repos/" + repo + "/releases/tags/" + TAG, token, null, null))).getLong("id");
        } catch (NotFound e) {
            JSONObject body = new JSONObject().put("tag_name", TAG).put("name", "My AI brain")
                    .put("body", "Cloud backups of My AI. Don't delete.").put("prerelease", true);
            return new JSONObject(text(call("POST", API + "/repos/" + repo + "/releases", token, null, body.toString()))).getLong("id");
        }
    }

    /** time -> {bin asset id, json asset id} (0 = missing). */
    private static TreeMap<Long, long[]> pairs(String repo, String token, long id) throws Exception {
        JSONArray a = new JSONArray(text(call("GET", API + "/repos/" + repo + "/releases/" + id + "/assets?per_page=100", token, null, null)));
        TreeMap<Long, long[]> out = new TreeMap<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            String n = o.getString("name");
            if (!n.startsWith("brain-") || !"uploaded".equals(o.optString("state", "uploaded"))) continue;
            boolean bin = n.endsWith(".bin");
            if (!bin && !n.endsWith(".json")) continue;
            long t;
            try { t = Long.parseLong(n.substring(6, n.lastIndexOf('.'))); } catch (NumberFormatException e) { continue; }
            long[] p = out.computeIfAbsent(t, k -> new long[2]);
            p[bin ? 0 : 1] = o.getLong("id");
        }
        return out;
    }

    private static List<Long> complete(TreeMap<Long, long[]> pairs) {
        List<Long> c = new ArrayList<>();
        for (java.util.Map.Entry<Long, long[]> e : pairs.entrySet()) if (e.getValue()[0] != 0 && e.getValue()[1] != 0) c.add(e.getKey());
        return c;
    }

    private static void fetchAsset(String repo, String token, long asset, File dest) throws Exception {
        // GitHub answers with a redirect to its file storage, which must be fetched without the key.
        HttpURLConnection c = open("GET", API + "/repos/" + repo + "/releases/assets/" + asset, token);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept", "application/octet-stream");
        int s = c.getResponseCode();
        if (s == 301 || s == 302 || s == 307) {
            String loc = c.getHeaderField("Location");
            c.disconnect();
            c = (HttpURLConnection) new URL(loc).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            s = c.getResponseCode();
        }
        if (s != 200) throw new Exception("Download failed (error " + s + ")");
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(dest)) {
            copy(in, out);
        }
    }

    static final class NotFound extends Exception {
        NotFound(String m) { super(m); }
    }

    private static HttpURLConnection open(String method, String url, String token) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(120000);
        c.setRequestProperty("Authorization", "Bearer " + token.trim());
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        c.setRequestProperty("User-Agent", "MyAI-app");
        return c;
    }

    /** Sends a request with an optional file or JSON body. Returns the open connection after checking the status. */
    private static HttpURLConnection call(String method, String url, String token, File file, String json) throws Exception {
        HttpURLConnection c = open(method, url, token);
        if (file != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/octet-stream");
            c.setFixedLengthStreamingMode(file.length());
            try (InputStream in = new FileInputStream(file); OutputStream out = c.getOutputStream()) {
                copy(in, out);
            }
        } else if (json != null) {
            byte[] b = json.getBytes(StandardCharsets.UTF_8);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setFixedLengthStreamingMode(b.length);
            try (OutputStream out = c.getOutputStream()) {
                out.write(b);
            }
        }
        int s = c.getResponseCode();
        if (s >= 200 && s < 300) return c;
        String detail = "";
        try (InputStream err = c.getErrorStream()) {
            if (err != null) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                copy(err, b);
                detail = new JSONObject(b.toString("UTF-8")).optString("message");
            }
        } catch (Exception ignored) { }
        if (s == 401) throw new Exception("GitHub didn't accept the key. Check it was copied fully and hasn't expired.");
        if (s == 403) throw new Exception("The key isn't allowed to do this. Give it 'Contents: Read and write' for the repository.");
        if (s == 404) throw new NotFound("Repository not found. Check its name, and that the key has access to it.");
        if (s == 422 && detail.toLowerCase().contains("empty")) throw new Exception("The repository is empty. Create it with 'Add a README' ticked.");
        throw new Exception("GitHub error " + s + (detail.isEmpty() ? "" : ": " + detail));
    }

    private static String text(HttpURLConnection c) throws IOException {
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            copy(in, b);
            return b.toString("UTF-8");
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[1 << 16];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
    }

    /** Turns network errors into something readable. */
    static String explain(Exception e) {
        if (e instanceof java.net.UnknownHostException || e instanceof java.net.ConnectException
                || e instanceof java.net.SocketTimeoutException)
            return "No internet connection. The backup will try again later.";
        return String.valueOf(e.getMessage());
    }

    private Backup() {}
}
