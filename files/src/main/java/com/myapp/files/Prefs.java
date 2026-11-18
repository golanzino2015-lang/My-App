package com.myapp.files;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final int SORT_NAME = 0, SORT_DATE = 1, SORT_SIZE = 2, SORT_TYPE = 3;

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("files", Context.MODE_PRIVATE);
    }

    static int sort(Context c) {
        return p(c).getInt("sort", SORT_NAME);
    }

    static boolean desc(Context c) {
        return p(c).getBoolean("desc", false);
    }

    static boolean hidden(Context c) {
        return p(c).getBoolean("hidden", false);
    }

    static void setSort(Context c, int sort, boolean desc) {
        p(c).edit().putInt("sort", sort).putBoolean("desc", desc).apply();
    }

    static void setHidden(Context c, boolean v) {
        p(c).edit().putBoolean("hidden", v).apply();
    }

    static int appSort(Context c) {
        return p(c).getInt("app_sort", 0);
    }

    static void setAppSort(Context c, int v) {
        p(c).edit().putInt("app_sort", v).apply();
    }

    private Prefs() {}
}
