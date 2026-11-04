package com.questionbubble.app;

import android.content.Context;
import android.graphics.Bitmap;

/** One entry point for every provider. Call from a background thread. */
final class Ai {

    static String ask(Context c, boolean detailed, String text, Bitmap image) throws Exception {
        Provider p = Prefs.provider(c);
        String key = Prefs.apiKey(c, p);
        String model = Prefs.model(c, p);
        if (key.isEmpty()) throw new Exception("Add your " + p.label + " API key in Question Bubble.");
        switch (p) {
            case CLAUDE:
                return ClaudeClient.ask(key, model, detailed, text, image);
            case GEMINI:
                return GeminiClient.ask(key, model, detailed, text, image);
            default:
                return OpenAiClient.ask(key, model, detailed, text, image);
        }
    }

    /** Throws with a readable message if the key doesn't work. */
    static void verify(Provider p, String key) throws Exception {
        if (key == null || key.trim().isEmpty()) throw new Exception("Paste your API key first.");
        switch (p) {
            case CLAUDE:
                ClaudeClient.verifyKey(key.trim());
                break;
            case GEMINI:
                GeminiClient.verifyKey(key.trim());
                break;
            default:
                OpenAiClient.verifyKey(key.trim());
        }
    }

    private Ai() {}
}
