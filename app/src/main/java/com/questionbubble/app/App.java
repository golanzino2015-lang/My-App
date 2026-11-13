package com.questionbubble.app;

import android.app.Application;

/** Loads the saved color theme before any screen or the bubble is drawn. */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Theme.apply(this);
    }
}
