package com.questionbubble.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/** Invisible helper that opens the photo picker for the bubble, then hands the image back to it. */
public class PickImageActivity extends Activity {

    private static final int PICK = 7;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) return;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(i, PICK);
        } catch (Exception e) {
            done(null);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK || resultCode != RESULT_OK || data == null || data.getData() == null) {
            done(null);
            return;
        }
        android.net.Uri uri = data.getData();
        new Thread(() -> {
            String name = ChatActivity.saveFromUri(getApplicationContext(), uri);
            new Handler(Looper.getMainLooper()).post(() -> done(name));
        }).start();
    }

    private void done(String name) {
        finish();
        overridePendingTransition(0, 0);
        BubbleService svc = BubbleService.instance;
        if (svc != null) svc.bubbleImagePicked(name);
    }
}
