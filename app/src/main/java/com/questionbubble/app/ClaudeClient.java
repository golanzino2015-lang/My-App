package com.questionbubble.app;

import android.graphics.Bitmap;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.beta.messages.BetaBase64ImageSource;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Claude via the official Anthropic Java SDK. Call from a background thread. */
final class ClaudeClient {

    private static AnthropicClient client;
    private static String clientKey;

    private static synchronized AnthropicClient client(String key) {
        if (client == null || !key.equals(clientKey)) {
            if (client != null) client.close();
            client = AnthropicOkHttpClient.builder()
                    .apiKey(key)
                    .timeout(Duration.ofSeconds(180))
                    .build();
            clientKey = key;
        }
        return client;
    }

    /** Current-generation models take output_config.effort. */
    private static boolean supportsEffort(String model) {
        return model.startsWith("claude-opus-5") || model.startsWith("claude-sonnet-5")
                || model.startsWith("claude-haiku-5") || model.startsWith("claude-fable-5");
    }

    /** Models that accept the server-side refusal fallback ("default" mode). */
    private static boolean supportsFallbacks(String model) {
        return model.equals("claude-opus-5-5") || model.equals("claude-opus-5")
                || model.equals("claude-sonnet-5-5") || model.equals("claude-fable-5-1");
    }

    static String ask(String key, String model, boolean detailed, String text, Bitmap image) throws Exception {
        List<BetaContentBlockParam> blocks = new ArrayList<>();
        if (image != null) {
            blocks.add(BetaContentBlockParam.ofImage(BetaBase64ImageSource.builder()
                    .mediaType(BetaBase64ImageSource.MediaType.IMAGE_JPEG)
                    .data(OpenAiClient.toBase64Jpeg(image))
                    .build()));
        }
        blocks.add(BetaContentBlockParam.ofText(text));

        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(detailed ? OpenAiClient.DETAILED_PROMPT : OpenAiClient.SYSTEM_PROMPT)
                .addUserMessageOfContentBlockParams(blocks);
        if (supportsEffort(model)) {
            b.outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.MEDIUM).build());
        }
        if (supportsFallbacks(model)) {
            // If a safety classifier declines, the API retries on Anthropic's recommended model.
            b.addBeta("server-side-fallback-2026-07-01").fallbacksDefault();
        }

        BetaMessage msg;
        try {
            msg = client(key).beta().messages().create(b.build());
        } catch (Exception e) {
            throw friendly(e);
        }

        if (msg.stopReason().map(r -> r.equals(BetaStopReason.REFUSAL)).orElse(false)) {
            throw new Exception("Claude declined to answer this one.");
        }
        StringBuilder out = new StringBuilder();
        for (BetaContentBlock block : msg.content()) {
            block.text().ifPresent(t -> out.append(t.text()));
        }
        String answer = out.toString().trim();
        if (answer.isEmpty()) throw new Exception("Claude returned an empty answer.");
        return answer;
    }

    static void verifyKey(String key) throws Exception {
        try {
            client(key).models().list();
        } catch (Exception e) {
            throw friendly(e);
        }
    }

    private static Exception friendly(Exception e) {
        if (e instanceof UnauthorizedException) {
            return new Exception("That Claude API key was rejected. Check it and try again.");
        } else if (e instanceof PermissionDeniedException) {
            return new Exception("Your Anthropic key doesn't have access to this model.");
        } else if (e instanceof NotFoundException) {
            return new Exception("That Claude model isn't available on your account.");
        } else if (e instanceof RateLimitException) {
            return new Exception("Rate limit reached on your Anthropic account. Try again shortly.");
        } else if (e instanceof AnthropicServiceException) {
            return new Exception("Claude returned an error (" + ((AnthropicServiceException) e).statusCode() + ").");
        } else if (e instanceof AnthropicIoException) {
            return new Exception("Couldn't reach Claude. Check your internet connection.");
        }
        return new Exception(e.getMessage() == null ? "Something went wrong talking to Claude." : e.getMessage());
    }

    private ClaudeClient() {}
}
