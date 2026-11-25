package com.questionbubble.app;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Talks to brain_server.py on your PC. Call from a background thread. */
final class Backup {

    /** "192.168.1.20" or "192.168.1.20:8765" -> "http://192.168.1.20:8765" */
    static String normalize(String address) {
        String a = address.trim();
        if (a.isEmpty()) return a;
        if (!a.startsWith("http://") && !a.startsWith("https://")) a = "http://" + a;
        while (a.endsWith("/")) a = a.substring(0, a.length() - 1);
        if (!a.substring(a.indexOf("//") + 2).contains(":")) a += ":8765";
        return a;
    }

    /** Checks the address and code. Returns the server's summary (has_brain, name, tokens…). */
    static JSONObject hello(String url, String code) throws Exception {
        HttpURLConnection c = open(url + "/api/hello", code, "GET", 6000);
        return new JSONObject(readAll(c));
    }

    static void upload(String url, String code, File brain, File meta) throws Exception {
        HttpURLConnection c = open(url + "/api/brain/file", code, "PUT", 15000);
        c.setDoOutput(true);
        c.setFixedLengthStreamingMode(brain.length());
        try (InputStream in = new FileInputStream(brain); OutputStream out = c.getOutputStream()) {
            copy(in, out);
        }
        readAll(c);
        byte[] m = readFile(meta);
        c = open(url + "/api/brain/meta", code, "PUT", 15000);
        c.setDoOutput(true);
        c.setFixedLengthStreamingMode(m.length);
        try (OutputStream out = c.getOutputStream()) {
            out.write(m);
        }
        readAll(c);
    }

    static void download(String url, String code, File brainDest, File metaDest) throws Exception {
        File tb = new File(brainDest.getPath() + ".dl"), tm = new File(metaDest.getPath() + ".dl");
        fetch(url + "/api/brain/file", code, tb);
        fetch(url + "/api/brain/meta", code, tm);
        if (!tb.renameTo(brainDest) || !tm.renameTo(metaDest)) throw new IOException("Couldn't save the brain on the phone");
    }

    private static void fetch(String url, String code, File dest) throws Exception {
        HttpURLConnection c = open(url, code, "GET", 15000);
        check(c);
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(dest)) {
            copy(in, out);
        }
    }

    private static HttpURLConnection open(String url, String code, String method, int timeout) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(timeout);
        c.setReadTimeout(60000);
        c.setRequestProperty("X-Pair-Code", code.trim());
        return c;
    }

    private static void check(HttpURLConnection c) throws Exception {
        int s = c.getResponseCode();
        if (s == 403) throw new Exception("Wrong pairing code");
        if (s == 404) throw new Exception("No brain is saved on the PC yet");
        if (s != 200) throw new Exception("The PC answered with error " + s);
    }

    private static String readAll(HttpURLConnection c) throws Exception {
        check(c);
        try (InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            copy(in, b);
            return b.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static byte[] readFile(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            copy(in, b);
            return b.toByteArray();
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[1 << 16];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
    }

    /** Turns network errors into something readable. */
    static String explain(Exception e) {
        String m = String.valueOf(e.getMessage());
        if (e instanceof java.net.ConnectException || e instanceof java.net.SocketTimeoutException
                || e instanceof java.net.NoRouteToHostException)
            return "Can't reach your PC. Is brain_server.py running, and is the phone on the same Wi-Fi?";
        if (e instanceof java.net.UnknownHostException || e instanceof java.net.MalformedURLException)
            return "That address doesn't look right. Copy it exactly as the server shows it.";
        return m;
    }

    private Backup() {}
}
