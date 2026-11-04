package com.questionbubble.app;

import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Always-on part of the app. Runs as an Accessibility Service (the same permission auto-clickers
 * use) so it can float the orb over any app and capture the screen when asked.
 */
public class BubbleService extends AccessibilityService {

    private static final String CROP_PROMPT = "Answer the question shown in this image.";
    private static final String FULL_PROMPT =
            "This is a screenshot of the user's screen. Find the main question on it and answer it.";
    private static final String POINT_PROMPT =
            "The user pressed and held on a question in this screenshot. The RED CIRCLE marks where "
            + "their finger was. Answer only the question at (or closest to) the red circle.";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private WindowManager wm;
    private FloatingBubble bubble;
    private MenuPopover menu;
    private Island island;
    private SelectionView selection;
    private Bitmap screenshot;
    private boolean busy;
    private long menuClosedAt;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener = (sp, key) -> {
        if (Prefs.K_BUBBLE_VISIBLE.equals(key)) {
            if (Prefs.bubbleVisible(this)) showBubble();
            else {
                menu.dismissNow();
                bubble.remove();
            }
        } else if (Prefs.K_BUBBLE_SIZE.equals(key)) {
            if (bubble.isShowing()) {
                menu.dismissNow();
                bubble.removeNow();
                showBubble();
            }
        }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        wm = getSystemService(WindowManager.class);
        Fonts.init(this);
        island = new Island(this, wm);
        menu = new MenuPopover(this, wm);
        bubble = new FloatingBubble(this, wm, new FloatingBubble.Callbacks() {
            @Override
            public void onTap() {
                toggleMenu();
            }

            @Override
            public void onLongPress() {
                menu.dismissNow();
                bubble.setMenuOpen(false);
                startScan();
            }

            @Override
            public void onDragStart() {
                closeMenu();
            }

            @Override
            public void onDismissed() {
                Prefs.setBubbleVisible(BubbleService.this, false);
                island.show(Island.Kind.INFO, "Bubble hidden", "Open Question Bubble to bring it back", 3200);
            }
        });
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(prefListener);
        if (Prefs.bubbleVisible(this)) showBubble();
    }

    private void showBubble() {
        if (!bubble.isShowing()) bubble.show(Prefs.bubbleSize(this));
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not used: the service exists for overlays and screenshots.
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (menu != null) menu.dismissNow();
        if (bubble != null) main.postDelayed(bubble::resnap, 350);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        cleanup();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        cleanup();
        worker.shutdownNow();
        super.onDestroy();
    }

    private void cleanup() {
        try {
            Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(prefListener);
        } catch (Exception ignored) {
        }
        if (menu != null) menu.dismissNow();
        removeSelection(false);
        if (island != null) island.hideNow();
        if (bubble != null) bubble.removeNow();
    }

    // ---------------------------------------------------------------- menu

    private void toggleMenu() {
        if (menu.isShowing()) {
            closeMenu();
            return;
        }
        // the tap that closed the menu via "outside touch" shouldn't immediately reopen it
        if (System.currentTimeMillis() - menuClosedAt < 600) return;
        Rect orb = bubble.orbBounds();
        menu.show(orb, bubble.safeArea(), bubble.onLeftSide(), busy, History.latest(this),
                new MenuPopover.Actions() {
                    @Override
                    public void scan() {
                        menu.dismissNow();
                        bubble.setMenuOpen(false);
                        startScan();
                    }

                    @Override
                    public void type() {
                        closeMenu();
                        if (!checkKey()) return;
                        startActivity(new Intent(BubbleService.this, TypeQuestionActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    }

                    @Override
                    public void history() {
                        closeMenu();
                        startActivity(new Intent(BubbleService.this, HistoryActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    }

                    @Override
                    public void settings() {
                        closeMenu();
                        openApp();
                    }

                    @Override
                    public void copyLast() {
                        History.Item last = History.latest(BubbleService.this);
                        closeMenu();
                        if (last != null) {
                            copy(last.answer);
                            island.show(Island.Kind.SUCCESS, "Copied again", last.answer, 2600);
                        }
                    }
                }, () -> {
                    menuClosedAt = System.currentTimeMillis();
                    closeMenu();
                });
        bubble.setMenuOpen(true);
    }

    private void closeMenu() {
        if (menu.isShowing()) menu.dismiss();
        bubble.setMenuOpen(false);
    }

    // ---------------------------------------------------------------- scan

    private void startScan() {
        if (busy) {
            island.show(Island.Kind.INFO, "One moment", "Still answering your last question", 2200);
            return;
        }
        if (!checkKey()) return;
        island.hideNow();
        bubble.setHidden(true);
        main.postDelayed(() -> takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(),
                new TakeScreenshotCallback() {
                    @Override
                    public void onSuccess(ScreenshotResult result) {
                        HardwareBuffer hb = result.getHardwareBuffer();
                        Bitmap hw = Bitmap.wrapHardwareBuffer(hb, result.getColorSpace());
                        Bitmap soft = hw == null ? null : hw.copy(Bitmap.Config.ARGB_8888, false);
                        hb.close();
                        if (soft == null) {
                            bubble.setHidden(false);
                            island.show(Island.Kind.ERROR, "Couldn't capture the screen", "Please try again", 3500);
                            return;
                        }
                        showSelection(soft);
                    }

                    @Override
                    public void onFailure(int errorCode) {
                        bubble.setHidden(false);
                        island.show(Island.Kind.ERROR, "Couldn't capture the screen",
                                "Try again in a second (error " + errorCode + ")", 3500);
                    }
                }), 220);
    }

    private void showSelection(Bitmap shot) {
        screenshot = shot;
        selection = new SelectionView(this, shot, new SelectionView.Listener() {
            @Override
            public void onCropped(Rect r) {
                Bitmap crop = Bitmap.createBitmap(shot, r.left, r.top, r.width(), r.height())
                        .copy(Bitmap.Config.ARGB_8888, false);
                main.postDelayed(() -> {
                    removeSelection(true);
                    ask(crop, CROP_PROMPT);
                }, 180);
            }

            @Override
            public void onLongPress(int x, int y) {
                autoDetect(x, y);
            }

            @Override
            public void onFullScreen() {
                Bitmap full = shot.copy(Bitmap.Config.ARGB_8888, false);
                removeSelection(true);
                ask(full, FULL_PROMPT);
            }

            @Override
            public void onCancel() {
                removeSelection(true);
            }
        });

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        p.setFitInsetsTypes(0);
        wm.addView(selection, p);
    }

    private void removeSelection(boolean animate) {
        View v = selection;
        Bitmap shot = screenshot;
        selection = null;
        screenshot = null;
        if (v != null) {
            if (animate) {
                v.animate().alpha(0f).setDuration(220).setInterpolator(Theme.ACCELERATE).withEndAction(() -> {
                    safeRemove(v);
                    if (shot != null) shot.recycle();
                }).start();
            } else {
                safeRemove(v);
                if (shot != null) shot.recycle();
            }
        } else if (shot != null) {
            shot.recycle();
        }
        if (bubble != null) bubble.setHidden(false);
    }

    /**
     * Press-and-hold: find the block of the screen under the finger. First we ask Android which UI
     * element is there (works in most apps and browsers); otherwise we take a band of the screen
     * around the finger. A red circle marks the exact spot for the model.
     */
    private void autoDetect(int x, int y) {
        Bitmap shot = screenshot;
        if (shot == null) return;
        int w = shot.getWidth(), h = shot.getHeight();

        Rect area = findQuestionArea(x, y, w, h);
        if (area == null) {
            int half = Math.round(h * 0.2f);
            area = new Rect(0, Math.max(0, y - half), w, Math.min(h, y + half));
        }
        int pad = Theme.dp(this, 10);
        area.inset(-pad, -pad);
        if (!area.intersect(0, 0, w, h)) area.set(0, 0, w, h);

        Bitmap crop = Bitmap.createBitmap(shot, area.left, area.top, area.width(), area.height())
                .copy(Bitmap.Config.ARGB_8888, true);
        Paint red = new Paint(Paint.ANTI_ALIAS_FLAG);
        red.setStyle(Paint.Style.STROKE);
        red.setStrokeWidth(Theme.dp(this, 4));
        red.setColor(Color.RED);
        new Canvas(crop).drawCircle(x - area.left, y - area.top, Theme.dp(this, 26), red);

        SelectionView sv = selection;
        if (sv != null) {
            sv.showDetected(area, () -> {
                removeSelection(true);
                ask(crop, POINT_PROMPT);
            });
        } else {
            ask(crop, POINT_PROMPT);
        }
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
                if (best != null && best.height() >= screenH * 0.04f && best.width() >= screenW * 0.3f) {
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

    // ---------------------------------------------------------------- answer

    private void ask(Bitmap image, String prompt) {
        busy = true;
        bubble.setBusy(true);
        island.show(Island.Kind.LOADING, "Reading the question", "Asking " + Prefs.provider(this).label, 0);
        boolean detailed = Prefs.detailed(this);
        worker.execute(() -> {
            try {
                String answer = Ai.ask(this, detailed, prompt, image);
                History.add(this, "scan", "", answer);
                main.post(() -> {
                    copy(answer);
                    busy = false;
                    bubble.setBusy(false);
                    bubble.celebrate();
                    island.show(Island.Kind.SUCCESS, "Copied to clipboard", answer, 5000);
                });
            } catch (Exception e) {
                main.post(() -> {
                    busy = false;
                    bubble.setBusy(false);
                    bubble.errorShake();
                    island.show(Island.Kind.ERROR, "Couldn't get an answer", String.valueOf(e.getMessage()), 5500);
                });
            } finally {
                image.recycle();
            }
        });
    }

    private void copy(String text) {
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        cm.setPrimaryClip(ClipData.newPlainText("answer", text));
    }

    // ---------------------------------------------------------------- helpers

    private boolean checkKey() {
        if (!Prefs.apiKey(this).isEmpty()) return true;
        island.show(Island.Kind.INFO, "Add your " + Prefs.provider(this).label + " key", "Opening Question Bubble", 3000);
        openApp();
        return false;
    }

    private void openApp() {
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void safeRemove(View v) {
        try {
            wm.removeView(v);
        } catch (Exception ignored) {
        }
    }
}
