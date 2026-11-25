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

    /** Cloud backup: your private GitHub repository ("owner/name", "" = off) and its access key. */
    static String cloudRepo(Context c) {
        return sp(c).getString("cloud_repo", "");
    }

    static String cloudToken(Context c) {
        return sp(c).getString("cloud_token", "");
    }

    static void setCloud(Context c, String repo, String token) {
        sp(c).edit().putString("cloud_repo", repo).putString("cloud_token", token).apply();
    }

    private Prefs() {}
}
