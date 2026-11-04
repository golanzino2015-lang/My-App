package com.questionbubble.app;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;

/** Whether the Accessibility service (the bubble) is switched on. */
final class Access {

    static boolean enabled(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (s == null) return false;
        ComponentName cn = new ComponentName(c, BubbleService.class);
        return s.contains(cn.flattenToString()) || s.contains(cn.flattenToShortString());
    }

    private Access() {}
}
