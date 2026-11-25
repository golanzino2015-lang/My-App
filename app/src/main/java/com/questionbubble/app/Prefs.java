package com.questionbubble.app;

import android.content.Context;
import android.content.SharedPreferences;

/** All settings, stored privately on the device. */
final class Prefs {

    static final String FILE = "settings";
    static final String K_API_KEY = "api_key";
    static final String K_MODEL = "model";
    static final String K_STYLE = "style";          // "short" | "detailed"
    static final String K_HAPTICS = "haptics";
    static final String K_BUBBLE_VISIBLE = "bubble_visible";
    static final String K_BUBBLE_SIZE = "bubble_size"; // 0 small, 1 medium, 2 large
    static final String K_ONBOARDED = "onboarded";

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static final String K_PROVIDER = "provider";

    /** Copies settings saved before multi-AI support into the ChatGPT slots (runs once). */
    private static void migrate(SharedPreferences p) {
        if (p.getBoolean("migrated_v2", false)) return;
        SharedPreferences.Editor e = p.edit();
        String oldKey = p.getString("api_key", "");
        if (!oldKey.isEmpty() && !p.contains("api_key_openai")) e.putString("api_key_openai", oldKey);
        String oldModel = p.getString("model", "");
        if (!oldModel.isEmpty() && !p.contains("model_openai")) e.putString("model_openai", oldModel);
        e.putBoolean("migrated_v2", true).apply();
    }

    static Provider provider(Context c) {
        SharedPreferences p = sp(c);
        migrate(p);
        return Provider.fromId(p.getString(K_PROVIDER, Provider.OPENAI.id));
    }

    static void setProvider(Context c, Provider pr) {
        sp(c).edit().putString(K_PROVIDER, pr.id).apply();
    }

    /** Key for the AI currently selected. */
    static String apiKey(Context c) {
        return apiKey(c, provider(c));
    }

    static String apiKey(Context c, Provider pr) {
        SharedPreferences p = sp(c);
        migrate(p);
        return p.getString("api_key_" + pr.id, "").trim();
    }

    static void setApiKey(Context c, Provider pr, String key) {
        sp(c).edit().putString("api_key_" + pr.id, key.trim()).apply();
    }

    /** Model for the AI currently selected. */
    static String model(Context c) {
        return model(c, provider(c));
    }

    static String model(Context c, Provider pr) {
        SharedPreferences p = sp(c);
        migrate(p);
        String m = p.getString("model_" + pr.id, "").trim();
        if (pr == Provider.GEMINI && (m.startsWith("gemini-2") || m.startsWith("gemini-1"))) m = "";
        return m.isEmpty() ? pr.defaultModel : m;
    }

    static void setModel(Context c, Provider pr, String m) {
        sp(c).edit().putString("model_" + pr.id, m.trim()).apply();
    }

    static boolean detailed(Context c) {
        return "detailed".equals(sp(c).getString(K_STYLE, "short"));
    }

    static void setDetailed(Context c, boolean d) {
        sp(c).edit().putString(K_STYLE, d ? "detailed" : "short").apply();
    }

    /** Chat: let the AI think longer and show its reasoning. */
    static boolean thinking(Context c) {
        return sp(c).getBoolean("chat_thinking", true);
    }

    static void setThinking(Context c, boolean on) {
        sp(c).edit().putBoolean("chat_thinking", on).apply();
    }

    /** Chat: the AI may search the web whenever it needs to (always on). */
    static boolean webSearch(Context c) {
        return true;
    }

    static void setWebSearch(Context c, boolean on) {
        sp(c).edit().putBoolean("chat_search", on).apply();
    }

    /** Color theme id (see Palette). */
    static String theme(Context c) {
        return sp(c).getString("theme", "classic");
    }

    static void setTheme(Context c, String id) {
        sp(c).edit().putString("theme", id).apply();
    }

    static boolean haptics(Context c) {
        return sp(c).getBoolean(K_HAPTICS, true);
    }

    static void setHaptics(Context c, boolean on) {
        sp(c).edit().putBoolean(K_HAPTICS, on).apply();
    }

    static boolean bubbleVisible(Context c) {
        return sp(c).getBoolean(K_BUBBLE_VISIBLE, true);
    }

    static void setBubbleVisible(Context c, boolean on) {
        sp(c).edit().putBoolean(K_BUBBLE_VISIBLE, on).apply();
    }

    static int bubbleSize(Context c) {
        return sp(c).getInt(K_BUBBLE_SIZE, 1);
    }

    static void setBubbleSize(Context c, int s) {
        sp(c).edit().putInt(K_BUBBLE_SIZE, s).apply();
    }

    static boolean onboarded(Context c) {
        return sp(c).getBoolean(K_ONBOARDED, false);
    }

    static void setOnboarded(Context c, boolean v) {
        sp(c).edit().putBoolean(K_ONBOARDED, v).apply();
    }

    static int[] bubblePos(Context c) {
        SharedPreferences p = sp(c);
        return new int[]{p.getInt("bubble_x", Integer.MIN_VALUE), p.getInt("bubble_y", Integer.MIN_VALUE)};
    }

    static void saveBubblePos(Context c, int x, int y) {
        sp(c).edit().putInt("bubble_x", x).putInt("bubble_y", y).apply();
    }

    /** "sk-...a1b2" style preview of the saved key. */
    static String maskedKey(Context c) {
        return maskedKey(c, provider(c));
    }

    static String maskedKey(Context c, Provider pr) {
        String k = apiKey(c, pr);
        if (k.isEmpty()) return "Not set";
        String tail = k.length() > 4 ? k.substring(k.length() - 4) : k;
        return "•••• " + tail;
    }

    private Prefs() {}
}
