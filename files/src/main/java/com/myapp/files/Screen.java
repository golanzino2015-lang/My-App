package com.myapp.files;

import android.content.Context;
import android.view.View;

/** One page in the app's navigation stack. */
abstract class Screen {
    final MainActivity a;
    final Context c;
    View view;

    Screen(MainActivity a) {
        this.a = a;
        this.c = a;
    }

    abstract View build();

    /** Becoming visible (pushed, returned to, or the app resumed). */
    void onShow() {}

    /** Back press; return true if handled inside the screen. */
    boolean onBack() {
        return false;
    }

    void onDestroy() {}

    int tab() {
        return MainActivity.TAB_HOME;
    }

    /** Whether the floating tab bar should show. */
    boolean navVisible() {
        return true;
    }
}
