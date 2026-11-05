package com.questionbubble.app;

import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Google Gemini (generateContent REST API). Call from a background thread. */
final class GeminiClient {

    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/";

    static String ask(String key, String model, boolean detailed, String text, Bitmap image) throws Exception {
        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("text", text));
        if (image != null) {
            parts.put(new JSONObject().put("inline_data", new JSONObject()
                    .put("mime_type", "image/jpeg")
                    .put("data", OpenAiClient.toBase64Jpeg(image))));
        }
        JSONObject body = new JSONObject()
                .put("system_instruction", new JSONObject().put("parts", new JSONArray()
                        .put(new JSONObject().put("text", detailed ? OpenAiClient.DETAILED_PROMPT : OpenAiClient.SYSTEM_PROMPT))))
                .put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts", parts)));

        String url = BASE + "models/" + URLEncoder.encode(model, "UTF-8") + ":generateContent";
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(20_000);
            conn.setReadTimeout(120_000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-goog-api-key", key);
            int code;
            try {
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                code = conn.getResponseCode();
            } catch (java.net.UnknownHostException e) {
                throw new Exception("No internet connection.");
            } catch (java.net.SocketTimeoutException e) {
                throw new Exception("The request timed out. Try again.");
            }
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String resp = is == null ? "" : readAll(is);
            if (code >= 400) throw new Exception(friendlyError(code, resp));

            JSONObject json = new JSONObject(resp);
            JSONArray candidates = json.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                JSONObject fb = json.optJSONObject("promptFeedback");
                if (fb != null && fb.has("blockReason")) throw new Exception("Gemini blocked this request.");
                throw new Exception("Gemini returned no answer.");
            }
            JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
            StringBuilder out = new StringBuilder();
            if (content != null) {
                JSONArray ps = content.optJSONArray("parts");
                if (ps != null) {
                    for (int i = 0; i < ps.length(); i++) {
                        JSONObject p = ps.getJSONObject(i);
                        if (p.optBoolean("thought", false)) continue;
                        out.append(p.optString("text", ""));
                    }
                }
            }
            String answer = out.toString().trim();
            if (answer.isEmpty()) {
                String reason = candidates.getJSONObject(0).optString("finishReason", "");
                if ("SAFETY".equals(reason)) throw new Exception("Gemini declined to answer this one.");
                throw new Exception("Gemini returned an empty answer.");
            }
            return answer;
        } finally {
            conn.disconnect();
        }
    }

    /** Fast model used only to find where a question is on the screen. */
    static final String LOCATE_MODEL = "gemini-3.5-flash-lite";

    /** Ask Gemini for a JSON reply (used to find a question's box). Returns the raw JSON text. */
    static String locate(String key, Bitmap image, String prompt) throws Exception {
        JSONArray parts = new JSONArray()
                .put(new JSONObject().put("text", prompt))
                .put(new JSONObject().put("inline_data", new JSONObject()
                        .put("mime_type", "image/jpeg").put("data", OpenAiClient.toBase64Jpeg(image))));
        JSONObject body = new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts", parts)))
                .put("generationConfig", new JSONObject()
                        .put("responseMimeType", "application/json")
                        .put("thinkingConfig", new JSONObject().put("thinkingLevel", "low")));
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE + "models/" + LOCATE_MODEL + ":generateContent").openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(45_000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-goog-api-key", key);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String resp = is == null ? "" : readAll(is);
            if (code >= 400) throw new Exception(friendlyError(code, resp));
            JSONArray cands = new JSONObject(resp).optJSONArray("candidates");
            if (cands == null || cands.length() == 0) throw new Exception("No result");
            JSONObject content = cands.getJSONObject(0).optJSONObject("content");
            JSONArray ps = content == null ? null : content.optJSONArray("parts");
            StringBuilder out = new StringBuilder();
            if (ps != null) {
                for (int i = 0; i < ps.length(); i++) {
                    JSONObject p = ps.getJSONObject(i);
                    if (!p.optBoolean("thought", false)) out.append(p.optString("text", ""));
                }
            }
            return out.toString();
        } finally {
            conn.disconnect();
        }
    }

    static void verifyKey(String key) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE + "models?pageSize=1").openConnection();
        try {
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(20_000);
            conn.setRequestProperty("x-goog-api-key", key);
            int code = conn.getResponseCode();
            if (code >= 400) {
                InputStream es = conn.getErrorStream();
                throw new Exception(friendlyError(code, es == null ? "" : readAll(es)));
            }
        } catch (java.net.UnknownHostException e) {
            throw new Exception("No internet connection.");
        } finally {
            conn.disconnect();
        }
    }

    static String friendlyError(int code, String body) {
        String msg = "";
        try {
            msg = new JSONObject(body).getJSONObject("error").optString("message", "");
        } catch (Exception ignored) {
        }
        if (code == 400 && msg.toLowerCase().contains("api key")) return "That Gemini API key was rejected. Check it and try again.";
        if (code == 401 || code == 403) return "That Gemini API key was rejected. Check it and try again.";
        if (code == 404) return "That Gemini model isn't available.";
        if (code == 429) return "Rate limit or quota reached on your Google account.";
        if (!msg.isEmpty()) return msg;
        return "Gemini returned an error (" + code + ").";
    }

    private static String readAll(InputStream is) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
        is.close();
        return out.toString("UTF-8");
    }

    private GeminiClient() {}
}
