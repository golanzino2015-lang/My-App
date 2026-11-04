package com.questionbubble.app;

import android.graphics.Bitmap;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Minimal OpenAI Chat Completions client (no external libraries). Call from a background thread. */
final class OpenAiClient {

    private static final String ENDPOINT = "https://api.openai.com/v1/chat/completions";
    private static final int MAX_IMAGE_SIDE = 2000;

    static final String DETAILED_PROMPT =
            "You are a fast, accurate question-answering assistant. The user gives you a question, "
            + "either as text or as a picture of their screen. Start with the answer on its own "
            + "first line (for multiple choice: the option letter/number and its text). Then add a "
            + "short explanation of at most two sentences. No markdown, no preamble. If several "
            + "questions are visible, answer only the one the user indicated.";

    static final String SYSTEM_PROMPT =
            "You are a fast, accurate question-answering assistant. The user gives you a question, "
            + "either as text or as a picture of their screen. Reply with ONLY the answer: no "
            + "explanation, no preamble, no markdown. For multiple choice, reply with the correct "
            + "option's letter/number followed by its text. For math, give the final result. For "
            + "fill-in-the-blank, give just the missing word(s). If several questions are visible, "
            + "answer only the one the user indicated.";

    static String ask(String apiKey, String model, boolean detailed, String text, Bitmap image) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new Exception("No API key set. Open the app and paste your OpenAI API key.");
        }

        JSONArray content = new JSONArray();
        content.put(new JSONObject().put("type", "text").put("text", text));
        if (image != null) {
            String dataUrl = "data:image/jpeg;base64," + toBase64Jpeg(image);
            content.put(new JSONObject()
                    .put("type", "image_url")
                    .put("image_url", new JSONObject().put("url", dataUrl).put("detail", "high")));
        }

        JSONArray messages = new JSONArray()
                .put(new JSONObject().put("role", "system").put("content", detailed ? DETAILED_PROMPT : SYSTEM_PROMPT))
                .put(new JSONObject().put("role", "user").put("content", content));

        JSONObject body = new JSONObject().put("model", model).put("messages", messages);

        HttpURLConnection conn = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(20_000);
            conn.setReadTimeout(120_000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);

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

            String answer = new JSONObject(resp)
                    .getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").optString("content", "").trim();
            if (answer.isEmpty()) throw new Exception("ChatGPT returned an empty answer.");
            return answer;
        } finally {
            conn.disconnect();
        }
    }

    /** Checks that a key works by listing models. Throws with a readable message if not. */
    static void verifyKey(String apiKey) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) throw new Exception("Enter your API key first.");
        HttpURLConnection conn = (HttpURLConnection) new URL("https://api.openai.com/v1/models").openConnection();
        try {
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(20_000);
            conn.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
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
        if (code == 401) return "That API key was rejected. Check it and try again.";
        if (code == 429) return "Rate limit or quota reached on your OpenAI account.";
        if (code == 404) return "That model isn't available on your account.";
        try {
            return new JSONObject(body).getJSONObject("error").getString("message");
        } catch (Exception ignored) {
        }
        return "OpenAI returned an error (" + code + ").";
    }

    static String toBase64Jpeg(Bitmap src) {
        Bitmap bmp = src;
        int max = Math.max(src.getWidth(), src.getHeight());
        if (max > MAX_IMAGE_SIDE) {
            float s = (float) MAX_IMAGE_SIDE / max;
            bmp = Bitmap.createScaledBitmap(src,
                    Math.max(1, Math.round(src.getWidth() * s)),
                    Math.max(1, Math.round(src.getHeight() * s)), true);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 88, out);
        if (bmp != src) bmp.recycle();
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    static String readAll(InputStream is) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
        is.close();
        return out.toString("UTF-8");
    }

    private OpenAiClient() {}
}
