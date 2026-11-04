package com.questionbubble.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores the OpenAI API key and model on the phone (never leaves the device except to OpenAI). */
final class Prefs {
    static final String DEFAULT_MODEL = "gpt-4o";

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static String apiKey(Context c) {
        return sp(c).getString("api_key", "").trim();
    }

    static String model(Context c) {
        String m = sp(c).getString("model", "").trim();
        return m.isEmpty() ? DEFAULT_MODEL : m;
    }

    static void save(Context c, String apiKey, String model) {
        sp(c).edit()
                .putString("api_key", apiKey.trim())
                .putString("model", model.trim())
                .apply();
    }

    private Prefs() {}
}
