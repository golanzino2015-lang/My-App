package com.questionbubble.app;

import android.content.Context;
import android.content.SharedPreferences;

/** All settings, stored privately on the device. */
final class Prefs {

    static final String FILE = "settings";
    static final String DEFAULT_MODEL = "gpt-4o";

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

    static String apiKey(Context c) {
        return sp(c).getString(K_API_KEY, "").trim();
    }

    static void setApiKey(Context c, String key) {
        sp(c).edit().putString(K_API_KEY, key.trim()).apply();
    }

    static String model(Context c) {
        String m = sp(c).getString(K_MODEL, "").trim();
        return m.isEmpty() ? DEFAULT_MODEL : m;
    }

    static void setModel(Context c, String m) {
        sp(c).edit().putString(K_MODEL, m.trim()).apply();
    }

    static boolean detailed(Context c) {
        return "detailed".equals(sp(c).getString(K_STYLE, "short"));
    }

    static void setDetailed(Context c, boolean d) {
        sp(c).edit().putString(K_STYLE, d ? "detailed" : "short").apply();
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
        String k = apiKey(c);
        if (k.isEmpty()) return "Not set";
        String tail = k.length() > 4 ? k.substring(k.length() - 4) : k;
        return "•••• " + tail;
    }

    private Prefs() {}
}
