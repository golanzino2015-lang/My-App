package com.questionbubble.app;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The always-on part of the app. Runs as an Accessibility Service (same permission auto-clickers
 * use) so it can draw the floating bubble over any app and take screenshots.
 */
public class BubbleService extends AccessibilityService {

    private static final String CROP_PROMPT = "Answer the question shown in this image.";
    private static final String POINT_PROMPT =
            "The user long-pressed on a question in this screenshot. The RED CIRCLE marks where "
            + "their finger was. Answer only the question at (or closest to) the red circle.";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private WindowManager wm;
    private ImageView bubble;
    private WindowManager.LayoutParams bubbleParams;
    private View menu;
    private SelectionView selection;
    private Bitmap screenshot;
    private boolean busy;
    private Toast toast;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        wm = getSystemService(WindowManager.class);
        showBubble();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not needed: we only use the service for overlays and screenshots.
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        removeAll();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        removeAll();
        worker.shutdownNow();
        super.onDestroy();
    }

    private void removeAll() {
        hideMenu();
        removeSelection();
        if (bubble != null) {
            safeRemove(bubble);
            bubble = null;
        }
    }

    // ---------------------------------------------------------------- bubble

    @SuppressLint("ClickableViewAccessibility")
    private void showBubble() {
        if (bubble != null) return;
        bubble = new ImageView(this);
        bubble.setImageResource(R.drawable.ic_bubble);
        bubble.setElevation(dp(6));

        bubbleParams = overlayParams(dp(56), dp(56));
        bubbleParams.x = dp(8);
        bubbleParams.y = dp(220);

        final int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        bubble.setOnTouchListener(new View.OnTouchListener() {
            int startX, startY;
            float downX, downY;
            boolean dragging;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = bubbleParams.x;
                        startY = bubbleParams.y;
                        downX = e.getRawX();
                        downY = e.getRawY();
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                        if (!dragging && Math.hypot(dx, dy) > slop) {
                            dragging = true;
                            hideMenu();
                        }
                        if (dragging) {
                            bubbleParams.x = startX + Math.round(dx);
                            bubbleParams.y = startY + Math.round(dy);
                            wm.updateViewLayout(bubble, bubbleParams);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dragging) toggleMenu();
                        return true;
                }
                return false;
            }
        });
        wm.addView(bubble, bubbleParams);
    }

    private void setBubbleVisible(boolean visible) {
        if (bubble != null) bubble.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setBusy(boolean b) {
        busy = b;
        if (bubble != null) bubble.setAlpha(b ? 0.45f : 1f);
    }

    // ---------------------------------------------------------------- menu

    private void toggleMenu() {
        if (menu != null) hideMenu();
        else showMenu();
    }

    private void showMenu() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0202124);
        bg.setCornerRadius(dp(16));
        box.setBackground(bg);
        box.setElevation(dp(8));

        box.addView(menuButton("📷  Select Question", () -> {
            hideMenu();
            startSelect();
        }));
        box.addView(menuButton("⌨️  Type Question", () -> {
            hideMenu();
            if (!checkKey()) return;
            startActivity(new Intent(this, TypeQuestionActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        }));
        box.addView(menuButton("⚙️  Settings", () -> {
            hideMenu();
            openSettings();
        }));

        int menuWidth = dp(210);
        WindowManager.LayoutParams p = overlayParams(menuWidth, WindowManager.LayoutParams.WRAP_CONTENT);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        boolean bubbleOnRight = bubbleParams.x + dp(28) > screenW / 2;
        p.x = bubbleOnRight ? bubbleParams.x - menuWidth - dp(6) : bubbleParams.x + dp(62);
        p.y = bubbleParams.y;

        menu = box;
        wm.addView(menu, p);
    }

    private TextView menuButton(String text, Runnable action) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF3C4043);
        bg.setCornerRadius(dp(10));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void hideMenu() {
        if (menu != null) {
            safeRemove(menu);
            menu = null;
        }
    }

    // ---------------------------------------------------------------- select question

    private void startSelect() {
        if (busy) {
            showToast("Still answering the last question…");
            return;
        }
        if (!checkKey()) return;

        setBubbleVisible(false); // keep the bubble out of the screenshot
        main.postDelayed(() -> takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(),
                new TakeScreenshotCallback() {
                    @Override
                    public void onSuccess(ScreenshotResult result) {
                        HardwareBuffer hb = result.getHardwareBuffer();
                        Bitmap hw = Bitmap.wrapHardwareBuffer(hb, result.getColorSpace());
                        Bitmap soft = hw == null ? null : hw.copy(Bitmap.Config.ARGB_8888, false);
                        hb.close();
                        if (soft == null) {
                            setBubbleVisible(true);
                            showToast("Couldn't read the screenshot");
                            return;
                        }
                        showSelection(soft);
                    }

                    @Override
                    public void onFailure(int errorCode) {
                        setBubbleVisible(true);
                        showToast("Screenshot failed (code " + errorCode + "). Try again.");
                    }
                }), 250);
    }

    private void showSelection(Bitmap shot) {
        screenshot = shot;
        selection = new SelectionView(this, shot, new SelectionView.Listener() {
            @Override
            public void onCropped(Rect r) {
                Bitmap crop = Bitmap.createBitmap(shot, r.left, r.top, r.width(), r.height())
                        .copy(Bitmap.Config.ARGB_8888, false);
                removeSelection();
                askAboutImage(crop, CROP_PROMPT);
            }

            @Override
            public void onLongPress(int x, int y) {
                autoDetect(x, y);
            }

            @Override
            public void onCancel() {
                removeSelection();
            }
        });

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        p.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        p.setFitInsetsTypes(0);
        wm.addView(selection, p);
    }

    private void removeSelection() {
        if (selection != null) {
            safeRemove(selection);
            selection = null;
        }
        if (screenshot != null) {
            screenshot.recycle();
            screenshot = null;
        }
        setBubbleVisible(true);
    }

    /**
     * Long-press: find the block of the screen under the finger. First we ask Android which UI
     * element is there (works in most apps and browsers); if that fails, we take a band of the
     * screen around the finger. A red circle marks the exact spot for ChatGPT.
     */
    private void autoDetect(int x, int y) {
        Bitmap shot = screenshot;
        if (shot == null) return;
        int w = shot.getWidth(), h = shot.getHeight();

        Rect area = findQuestionArea(x, y, w, h);
        if (area == null) {
            int half = Math.round(h * 0.28f);
            area = new Rect(0, Math.max(0, y - half), w, Math.min(h, y + half));
        }
        int pad = dp(12);
        area.inset(-pad, -pad);
        if (!area.intersect(0, 0, w, h)) area.set(0, 0, w, h);

        Bitmap crop = Bitmap.createBitmap(shot, area.left, area.top, area.width(), area.height())
                .copy(Bitmap.Config.ARGB_8888, true);
        Paint red = new Paint(Paint.ANTI_ALIAS_FLAG);
        red.setStyle(Paint.Style.STROKE);
        red.setStrokeWidth(dp(4));
        red.setColor(Color.RED);
        new Canvas(crop).drawCircle(x - area.left, y - area.top, dp(26), red);

        if (selection != null) selection.showDetected(new Rect(area));
        main.postDelayed(() -> {
            removeSelection();
            askAboutImage(crop, POINT_PROMPT);
        }, 450);
    }

    private Rect findQuestionArea(int x, int y, int screenW, int screenH) {
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            for (AccessibilityWindowInfo win : windows) {
                if (win.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                Rect wb = new Rect();
                win.getBoundsInScreen(wb);
                if (!wb.contains(x, y)) continue;
                AccessibilityNodeInfo root = win.getRoot();
                if (root == null) continue;

                AccessibilityNodeInfo node = deepestAt(root, x, y);
                if (node == null) continue;

                // Walk up from the touched element until the block is big enough to hold a
                // whole question (but not the whole page).
                Rect best = null;
                AccessibilityNodeInfo cur = node;
                while (cur != null) {
                    Rect b = new Rect();
                    cur.getBoundsInScreen(b);
                    if (b.height() > screenH * 0.55f) break;
                    best = b;
                    if (b.height() >= screenH * 0.14f && b.width() >= screenW * 0.5f) break;
                    cur = cur.getParent();
                }
                if (best != null && best.height() >= screenH * 0.04f
                        && best.width() >= screenW * 0.3f) {
                    return best;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private AccessibilityNodeInfo deepestAt(AccessibilityNodeInfo node, int x, int y) {
        Rect b = new Rect();
        node.getBoundsInScreen(b);
        if (!b.contains(x, y)) return null;
        for (int i = node.getChildCount() - 1; i >= 0; i--) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null || !child.isVisibleToUser()) continue;
            AccessibilityNodeInfo hit = deepestAt(child, x, y);
            if (hit != null) return hit;
        }
        return node;
    }

    // ---------------------------------------------------------------- ChatGPT + clipboard

    private void askAboutImage(Bitmap image, String prompt) {
        setBusy(true);
        showToast("Thinking…");
        String key = Prefs.apiKey(this);
        String model = Prefs.model(this);
        worker.execute(() -> {
            try {
                String answer = OpenAiClient.ask(key, model, prompt, image);
                main.post(() -> {
                    copyToClipboard(answer);
                    String preview = answer.length() > 120 ? answer.substring(0, 120) + "…" : answer;
                    showToast("✅ Copied: " + preview);
                    setBusy(false);
                });
            } catch (Exception e) {
                main.post(() -> {
                    showToast("Error: " + e.getMessage());
                    setBusy(false);
                });
            } finally {
                image.recycle();
            }
        });
    }

    private void copyToClipboard(String text) {
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        cm.setPrimaryClip(ClipData.newPlainText("answer", text));
    }

    // ---------------------------------------------------------------- helpers

    private boolean checkKey() {
        if (!Prefs.apiKey(this).isEmpty()) return true;
        showToast("Add your OpenAI API key first");
        openSettings();
        return false;
    }

    private void openSettings() {
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void showToast(String msg) {
        if (toast != null) toast.cancel();
        toast = Toast.makeText(this, msg, Toast.LENGTH_LONG);
        toast.show();
    }

    private WindowManager.LayoutParams overlayParams(int w, int h) {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        return p;
    }

    private void safeRemove(View v) {
        try {
            wm.removeView(v);
        } catch (Exception ignored) {
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
