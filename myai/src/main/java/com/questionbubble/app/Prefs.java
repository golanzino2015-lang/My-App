package com.questionbubble.app;

import android.content.Context;
import android.content.SharedPreferences;

/** My AI's few settings, stored privately on the device. */
final class Prefs {

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    /** Color theme id (see Palette). */
    static String theme(Context c) {
        return sp(c).getString("theme", "classic");
    }

    static void setTheme(Context c, String id) {
        sp(c).edit().putString("theme", id).apply();
    }

    static boolean haptics(Context c) {
        return sp(c).getBoolean("haptics", true);
    }

    private Prefs() {}
}
