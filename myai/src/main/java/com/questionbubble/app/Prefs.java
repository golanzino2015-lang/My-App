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

    /** PC backup server address ("" = not set up) and its pairing code. */
    static String backupUrl(Context c) {
        return sp(c).getString("backup_url", "");
    }

    static String backupCode(Context c) {
        return sp(c).getString("backup_code", "");
    }

    static void setBackup(Context c, String url, String code) {
        sp(c).edit().putString("backup_url", url).putString("backup_code", code).apply();
    }

    private Prefs() {}
}
