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
import com.anthropic.models.beta.messages.BetaThinkingConfigAdaptive;
import com.anthropic.models.beta.messages.BetaWebSearchTool20250305;
import com.anthropic.models.beta.messages.BetaWebSearchTool20260209;
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

/**
 * Multi-turn chat with whichever AI is selected, with optional visible thinking and web search.
 * Call from a background thread.
 */
final class ChatApi {

    /** Fixed for every chat request, so Claude's saved reasoning stays valid across turns. */
    static final String CHAT_PROMPT =
            "You are a helpful assistant inside My App, a phone app. Answer clearly and "
            + "concisely, in short paragraphs. Use simple formatting only: **bold**, bullet lists, "
            + "headings and `code`. When the user shares a screenshot, focus on what they ask about it.";

    static Chats.Msg reply(Context c, Chats.Chat chat) throws Exception {
        Provider p = Prefs.provider(c);
        String key = Prefs.apiKey(c, p);
        String model = Prefs.model(c, p);
        boolean think = Prefs.thinking(c);
        boolean search = Prefs.webSearch(c);
        if (key.isEmpty()) throw new Exception("Add your " + p.label + " API key in My App.");
        long start = System.currentTimeMillis();
        Chats.Msg m;
        switch (p) {
            case CLAUDE:
                try {
                    m = claude(c, key, model, chat, think, search, false);
                } catch (StaleHistory e) {
                    // Saved reasoning no longer matches (e.g. Search was toggled): send it once without it.
                    m = claude(c, key, model, chat, think, search, true);
                }
                break;
            case GEMINI:
                m = gemini(c, key, model, chat, think, search);
                break;
            default:
                m = openai(c, key, model, chat, think, search);
        }
        m.seconds = (int) Math.max(1, (System.currentTimeMillis() - start) / 1000);
        return m;
    }

    private static Chats.Msg done(Provider p, String model, String text, String raw, String thinking, int searches) throws Exception {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) throw new Exception(p.label + " returned an empty answer.");
        Chats.Msg m = new Chats.Msg("assistant", t, null, p.id, model, raw, System.currentTimeMillis());
        m.thinking = thinking == null ? "" : thinking.trim();
        m.searches = searches;
        return m;
    }

    // ---------------------------------------------------------------- OpenAI (Responses API)

    private static boolean openaiReasoning(String model) {
        return model.startsWith("gpt-5") || model.startsWith("o1") || model.startsWith("o3") || model.startsWith("o4");
    }

    private static Chats.Msg openai(Context c, String key, String model, Chats.Chat chat, boolean think, boolean search) throws Exception {
        JSONArray input = new JSONArray();
        for (Chats.Msg m : chat.messages) {
            if (m.isUser()) {
                JSONArray parts = new JSONArray();
                if (!m.text.isEmpty()) parts.put(new JSONObject().put("type", "input_text").put("text", m.text));
                for (String img : m.images) {
                    parts.put(new JSONObject().put("type", "input_image").put("detail", "high")
                            .put("image_url", "data:image/jpeg;base64," + Chats.imageBase64(c, img)));
                }
                if (parts.length() == 0) continue;
                input.put(new JSONObject().put("role", "user").put("content", parts));
            } else {
                input.put(new JSONObject().put("role", "assistant").put("content", m.text));
            }
        }
        JSONObject body = new JSONObject().put("model", model).put("instructions", CHAT_PROMPT)
                .put("input", input).put("store", false);
        if (search) body.put("tools", new JSONArray().put(new JSONObject().put("type", "web_search")));
        boolean reasoning = openaiReasoning(model);
        if (reasoning) {
            JSONObject r = new JSONObject().put("effort", think ? "high" : "low");
            if (think) r.put("summary", "auto");
            body.put("reasoning", r);
        }

        JSONObject resp;
        try {
            resp = post("https://api.openai.com/v1/responses", new String[]{"Authorization", "Bearer " + key}, body, true);
        } catch (Exception e) {
            // Reasoning summaries need a verified OpenAI organization; ask again without them.
            String msg = String.valueOf(e.getMessage()).toLowerCase();
            if (reasoning && think && (msg.contains("summar") || msg.contains("verif"))) {
                body.getJSONObject("reasoning").remove("summary");
                resp = post("https://api.openai.com/v1/responses", new String[]{"Authorization", "Bearer " + key}, body, true);
            } else {
                throw e;
            }
        }

        StringBuilder text = new StringBuilder(), thoughts = new StringBuilder();
        int searches = 0;
        JSONArray out = resp.optJSONArray("output");
        if (out != null) {
            for (int i = 0; i < out.length(); i++) {
                JSONObject item = out.getJSONObject(i);
                String type = item.optString("type");
                if ("message".equals(type)) {
                    JSONArray content = item.optJSONArray("content");
                    if (content == null) continue;
                    for (int j = 0; j < content.length(); j++) {
                        JSONObject part = content.getJSONObject(j);
                        if ("output_text".equals(part.optString("type"))) text.append(part.optString("text"));
                        else if ("refusal".equals(part.optString("type"))) text.append(part.optString("refusal"));
                    }
                } else if ("reasoning".equals(type)) {
                    JSONArray sum = item.optJSONArray("summary");
                    if (sum == null) continue;
                    for (int j = 0; j < sum.length(); j++) {
                        if (thoughts.length() > 0) thoughts.append("\n\n");
                        thoughts.append(sum.getJSONObject(j).optString("text"));
                    }
                } else if ("web_search_call".equals(type)) {
                    searches++;
                }
            }
        }
        return done(Provider.OPENAI, model, text.toString(), null, thoughts.toString(), searches);
    }

    // ---------------------------------------------------------------- Gemini

    private static Chats.Msg gemini(Context c, String key, String model, Chats.Chat chat, boolean think, boolean search) throws Exception {
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
        JSONObject thinkingConfig = new JSONObject();
        boolean gen3 = model.startsWith("gemini-3");
        if (think) {
            thinkingConfig.put("includeThoughts", true);
            if (gen3) thinkingConfig.put("thinkingLevel", "high");
        } else if (gen3) {
            thinkingConfig.put("thinkingLevel", "low");
        } else if (!model.contains("pro")) {
            thinkingConfig.put("thinkingBudget", 0);
        }
        if (thinkingConfig.length() > 0) {
            body.put("generationConfig", new JSONObject().put("thinkingConfig", thinkingConfig));
        }
        if (search) body.put("tools", new JSONArray().put(new JSONObject().put("google_search", new JSONObject())));

        JSONObject resp = post("https://generativelanguage.googleapis.com/v1beta/models/"
                        + URLEncoder.encode(model, "UTF-8") + ":generateContent",
                new String[]{"x-goog-api-key", key}, body, false);
        JSONArray cands = resp.optJSONArray("candidates");
        if (cands == null || cands.length() == 0) throw new Exception("Gemini returned no answer.");
        JSONObject cand = cands.getJSONObject(0);
        StringBuilder text = new StringBuilder(), thoughts = new StringBuilder();
        JSONObject content = cand.optJSONObject("content");
        JSONArray ps = content == null ? null : content.optJSONArray("parts");
        if (ps != null) {
            for (int i = 0; i < ps.length(); i++) {
                JSONObject part = ps.getJSONObject(i);
                if (part.optBoolean("thought", false)) thoughts.append(part.optString("text", ""));
                else text.append(part.optString("text", ""));
            }
        }
        if (text.length() == 0 && "SAFETY".equals(cand.optString("finishReason"))) {
            throw new Exception("Gemini declined to answer this one.");
        }
        int searches = 0;
        JSONObject grounding = cand.optJSONObject("groundingMetadata");
        if (grounding != null) {
            JSONArray q = grounding.optJSONArray("webSearchQueries");
            searches = q == null ? 0 : q.length();
        }
        return done(Provider.GEMINI, model, text.toString(), null, thoughts.toString(), searches);
    }

    private static JSONObject post(String url, String[] header, JSONObject body, boolean openai) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(20_000);
            conn.setReadTimeout(300_000);
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
            client = AnthropicOkHttpClient.builder().apiKey(key).timeout(Duration.ofSeconds(300)).build();
            clientKey = key;
        }
        return client;
    }

    private static boolean current5(String model) {
        return model.startsWith("claude-opus-5") || model.startsWith("claude-sonnet-5")
                || model.startsWith("claude-haiku-5") || model.startsWith("claude-fable-5");
    }

    /** Models that take the dynamic-filtering web search tool; others use the basic one. */
    private static boolean newSearchTool(String model) {
        return model.startsWith("claude-opus-5") || model.startsWith("claude-sonnet-5")
                || model.startsWith("claude-opus-4-8") || model.startsWith("claude-opus-4-7")
                || model.startsWith("claude-opus-4-6") || model.startsWith("claude-sonnet-4-6");
    }

    /** Saved responses for one assistant turn (older chats stored a single object). */
    private static List<BetaMessage> savedResponses(String raw) throws Exception {
        List<BetaMessage> out = new ArrayList<>();
        String t = raw.trim();
        if (t.startsWith("[")) {
            JSONArray arr = new JSONArray(t);
            for (int i = 0; i < arr.length(); i++) {
                out.add(ObjectMappers.jsonMapper().readValue(arr.get(i).toString(), BetaMessage.class));
            }
        } else {
            out.add(ObjectMappers.jsonMapper().readValue(t, BetaMessage.class));
        }
        return out;
    }

    /**
     * Claude's reasoning blocks are tied to the exact conversation, so earlier Claude replies are
     * replayed exactly as they came back, the system prompt never changes, and the history is
     * append-only. {@code strip} keeps only the text of earlier replies: a one-time recovery when
     * the API rejects the saved history.
     */
    private static Chats.Msg claude(Context c, String key, String model, Chats.Chat chat,
                                    boolean think, boolean search, boolean strip) throws Exception {
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
            } else if (m.raw != null && !strip) {
                for (BetaMessage saved : savedResponses(m.raw)) b.addMessage(saved);
            } else {
                b.addAssistantMessage(m.text);
            }
        }

        boolean modern = current5(model);
        if (modern) {
            if (think) {
                b.thinking(BetaThinkingConfigAdaptive.builder()
                        .display(BetaThinkingConfigAdaptive.Display.SUMMARIZED).build());
            }
            b.outputConfig(BetaOutputConfig.builder()
                    .effort(think ? BetaOutputConfig.Effort.HIGH : BetaOutputConfig.Effort.LOW).build());
        }
        if (search) {
            if (newSearchTool(model)) b.addTool(BetaWebSearchTool20260209.builder().maxUses(5L).build());
            else b.addTool(BetaWebSearchTool20250305.builder().maxUses(5L).build());
        }
        if (model.equals("claude-opus-5-5") || model.equals("claude-opus-5")
                || model.equals("claude-sonnet-5-5") || model.equals("claude-fable-5-1")) {
            b.addBeta("server-side-fallback-2026-07-01").fallbacksDefault();
        }

        List<BetaMessage> responses = new ArrayList<>();
        StringBuilder text = new StringBuilder(), thoughts = new StringBuilder();
        int searches = 0;
        // A long web search can pause the turn; send the partial reply back to let it continue.
        for (int round = 0; round < 4; round++) {
            BetaMessage msg = call(key, b.build(), strip);
            responses.add(msg);
            if (msg.stopReason().map(r -> r.equals(BetaStopReason.REFUSAL)).orElse(false)) {
                throw new Exception("Claude declined to answer this one.");
            }
            for (BetaContentBlock block : msg.content()) {
                block.text().ifPresent(t -> text.append(t.text()));
                block.thinking().ifPresent(t -> {
                    if (!t.thinking().isEmpty()) {
                        if (thoughts.length() > 0) thoughts.append("\n\n");
                        thoughts.append(t.thinking());
                    }
                });
                if (block.serverToolUse().map(u -> u.name().toString().contains("web_search")).orElse(false)) {
                    searches++;
                }
            }
            boolean paused = msg.stopReason().map(r -> r.equals(BetaStopReason.PAUSE_TURN)).orElse(false);
            if (!paused) break;
            b.addMessage(msg);
        }

        JSONArray raw = new JSONArray();
        for (BetaMessage r : responses) raw.put(new JSONObject(ObjectMappers.jsonMapper().writeValueAsString(r)));
        return done(Provider.CLAUDE, model, text.toString(), raw.toString(), thoughts.toString(), searches);
    }

    private static BetaMessage call(String key, MessageCreateParams params, boolean strip) throws Exception {
        try {
            return claudeClient(key).beta().messages().create(params);
        } catch (BadRequestException e) {
            if (!strip) throw new StaleHistory();
            throw new Exception("Claude couldn't process this chat. Try a new chat.");
        } catch (UnauthorizedException e) {
            throw new Exception("That Claude API key was rejected. Check it in My App.");
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
    }

    private ChatApi() {}
}
