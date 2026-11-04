package com.questionbubble.app;

import android.content.Context;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.beta.messages.BetaBase64ImageSource;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaMessageContentParam;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Multi-turn chat with whichever AI is selected. Call from a background thread. */
final class ChatApi {

    /** Fixed for every chat request, so Claude's saved reasoning stays valid across turns. */
    static final String CHAT_PROMPT =
            "You are a helpful assistant inside Question Bubble, a phone app. Answer clearly and "
            + "concisely, in short paragraphs. Use simple formatting only: **bold**, bullet lists "
            + "and `code`. When the user shares a screenshot, focus on what they ask about it.";

    static Chats.Msg reply(Context c, Chats.Chat chat) throws Exception {
        Provider p = Prefs.provider(c);
        String key = Prefs.apiKey(c, p);
        String model = Prefs.model(c, p);
        if (key.isEmpty()) throw new Exception("Add your " + p.label + " API key in Question Bubble.");
        switch (p) {
            case CLAUDE:
                try {
                    return claude(c, key, model, chat, false);
                } catch (StaleHistory e) {
                    // The saved reasoning no longer matches this conversation: send it once without it.
                    return claude(c, key, model, chat, true);
                }
            case GEMINI:
                return gemini(c, key, model, chat);
            default:
                return openai(c, key, model, chat);
        }
    }

    private static Chats.Msg done(Provider p, String model, String text, String raw) throws Exception {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) throw new Exception(p.label + " returned an empty answer.");
        return new Chats.Msg("assistant", t, null, p.id, model, raw, System.currentTimeMillis());
    }

    // ---------------------------------------------------------------- OpenAI

    private static Chats.Msg openai(Context c, String key, String model, Chats.Chat chat) throws Exception {
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", CHAT_PROMPT));
        for (Chats.Msg m : chat.messages) {
            if (m.isUser()) {
                JSONArray parts = new JSONArray();
                if (!m.text.isEmpty()) parts.put(new JSONObject().put("type", "text").put("text", m.text));
                for (String img : m.images) {
                    parts.put(new JSONObject().put("type", "image_url").put("image_url", new JSONObject()
                            .put("url", "data:image/jpeg;base64," + Chats.imageBase64(c, img)).put("detail", "high")));
                }
                messages.put(new JSONObject().put("role", "user").put("content", parts));
            } else {
                messages.put(new JSONObject().put("role", "assistant").put("content", m.text));
            }
        }
        JSONObject body = new JSONObject().put("model", model).put("messages", messages);
        JSONObject resp = post("https://api.openai.com/v1/chat/completions",
                new String[]{"Authorization", "Bearer " + key}, body, true);
        String text = resp.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "");
        return done(Provider.OPENAI, model, text, null);
    }

    // ---------------------------------------------------------------- Gemini

    private static Chats.Msg gemini(Context c, String key, String model, Chats.Chat chat) throws Exception {
        JSONArray contents = new JSONArray();
        for (Chats.Msg m : chat.messages) {
            JSONArray parts = new JSONArray();
            if (!m.text.isEmpty()) parts.put(new JSONObject().put("text", m.text));
            for (String img : m.images) {
                parts.put(new JSONObject().put("inline_data", new JSONObject()
                        .put("mime_type", "image/jpeg").put("data", Chats.imageBase64(c, img))));
            }
            if (parts.length() == 0) continue;
            contents.put(new JSONObject().put("role", m.isUser() ? "user" : "model").put("parts", parts));
        }
        JSONObject body = new JSONObject()
                .put("system_instruction", new JSONObject().put("parts",
                        new JSONArray().put(new JSONObject().put("text", CHAT_PROMPT))))
                .put("contents", contents);
        JSONObject resp = post("https://generativelanguage.googleapis.com/v1beta/models/"
                        + URLEncoder.encode(model, "UTF-8") + ":generateContent",
                new String[]{"x-goog-api-key", key}, body, false);
        JSONArray cands = resp.optJSONArray("candidates");
        if (cands == null || cands.length() == 0) throw new Exception("Gemini returned no answer.");
        StringBuilder out = new StringBuilder();
        JSONObject content = cands.getJSONObject(0).optJSONObject("content");
        JSONArray ps = content == null ? null : content.optJSONArray("parts");
        if (ps != null) {
            for (int i = 0; i < ps.length(); i++) {
                JSONObject part = ps.getJSONObject(i);
                if (!part.optBoolean("thought", false)) out.append(part.optString("text", ""));
            }
        }
        if (out.length() == 0 && "SAFETY".equals(cands.getJSONObject(0).optString("finishReason"))) {
            throw new Exception("Gemini declined to answer this one.");
        }
        return done(Provider.GEMINI, model, out.toString(), null);
    }

    private static JSONObject post(String url, String[] header, JSONObject body, boolean openai) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(20_000);
            conn.setReadTimeout(150_000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty(header[0], header[1]);
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
            String resp = is == null ? "" : OpenAiClient.readAll(is);
            if (code >= 400) {
                throw new Exception(openai ? OpenAiClient.friendlyError(code, resp) : GeminiClient.friendlyError(code, resp));
            }
            return new JSONObject(resp);
        } finally {
            conn.disconnect();
        }
    }

    // ---------------------------------------------------------------- Claude

    private static final class StaleHistory extends Exception {
    }

    private static AnthropicClient client;
    private static String clientKey;

    private static synchronized AnthropicClient claudeClient(String key) {
        if (client == null || !key.equals(clientKey)) {
            if (client != null) client.close();
            client = AnthropicOkHttpClient.builder().apiKey(key).timeout(Duration.ofSeconds(180)).build();
            clientKey = key;
        }
        return client;
    }

    /**
     * Claude Opus 5.5 ties its reasoning blocks to the exact conversation, so earlier Claude replies
     * are replayed exactly as they came back (thinking + text, in order), the system prompt never
     * changes, and the history is append-only. {@code strip} drops all reasoning blocks: the
     * one-time recovery if the API says a saved block no longer matches.
     */
    private static Chats.Msg claude(Context c, String key, String model, Chats.Chat chat, boolean strip) throws Exception {
        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(CHAT_PROMPT);
        for (Chats.Msg m : chat.messages) {
            if (m.isUser()) {
                List<BetaContentBlockParam> blocks = new ArrayList<>();
                for (String img : m.images) {
                    blocks.add(BetaContentBlockParam.ofImage(BetaBase64ImageSource.builder()
                            .mediaType(BetaBase64ImageSource.MediaType.IMAGE_JPEG)
                            .data(Chats.imageBase64(c, img))
                            .build()));
                }
                if (!m.text.isEmpty()) blocks.add(BetaContentBlockParam.ofText(m.text));
                if (blocks.isEmpty()) continue;
                b.addUserMessageOfContentBlockParams(blocks);
            } else if (m.raw != null) {
                BetaMessage saved = ObjectMappers.jsonMapper().readValue(m.raw, BetaMessage.class);
                List<BetaContentBlockParam> blocks = new ArrayList<>();
                for (BetaContentBlock block : saved.content()) {
                    if (block.isText() || (!strip && (block.isThinking() || block.isRedactedThinking()))) {
                        blocks.add(block.toParam());
                    }
                }
                if (blocks.isEmpty()) blocks.add(BetaContentBlockParam.ofText(m.text));
                b.addAssistantMessage(BetaMessageContentParam.ofContentBlockParams(blocks));
            } else {
                b.addAssistantMessage(m.text);
            }
        }
        if (model.startsWith("claude-opus-5") || model.startsWith("claude-sonnet-5")
                || model.startsWith("claude-haiku-5") || model.startsWith("claude-fable-5")) {
            b.outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.MEDIUM).build());
        }
        if (model.equals("claude-opus-5-5") || model.equals("claude-opus-5")
                || model.equals("claude-sonnet-5-5") || model.equals("claude-fable-5-1")) {
            b.addBeta("server-side-fallback-2026-07-01").fallbacksDefault();
        }

        BetaMessage msg;
        try {
            msg = claudeClient(key).beta().messages().create(b.build());
        } catch (BadRequestException e) {
            String m = String.valueOf(e.getMessage());
            if (!strip && (m.contains("signature") || m.contains("different conversation"))) throw new StaleHistory();
            throw new Exception("Claude couldn't process this chat (400).");
        } catch (UnauthorizedException e) {
            throw new Exception("That Claude API key was rejected. Check it in Question Bubble.");
        } catch (PermissionDeniedException e) {
            throw new Exception("Your Anthropic key doesn't have access to this model.");
        } catch (NotFoundException e) {
            throw new Exception("That Claude model isn't available on your account.");
        } catch (RateLimitException e) {
            throw new Exception("Rate limit reached on your Anthropic account. Try again shortly.");
        } catch (AnthropicServiceException e) {
            throw new Exception("Claude returned an error (" + e.statusCode() + ").");
        } catch (AnthropicIoException e) {
            throw new Exception("Couldn't reach Claude. Check your internet connection.");
        }

        if (msg.stopReason().map(r -> r.equals(BetaStopReason.REFUSAL)).orElse(false)) {
            throw new Exception("Claude declined to answer this one.");
        }
        StringBuilder out = new StringBuilder();
        for (BetaContentBlock block : msg.content()) block.text().ifPresent(t -> out.append(t.text()));
        String raw = ObjectMappers.jsonMapper().writeValueAsString(msg);
        return done(Provider.CLAUDE, model, out.toString(), raw);
    }

    private ChatApi() {}
}
