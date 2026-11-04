package com.questionbubble.app;

import android.accessibilityservice.AccessibilityService;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.StateListDrawable;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
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
    private Notice notice;
    private BubbleView bubble;
    private WindowManager.LayoutParams bubbleParams;
    private int bubbleSize;
    private ValueAnimator snapAnim;
    private View menu;
    private long menuClosedByOutsideAt;
    private SelectionView selection;
    private Bitmap screenshot;
    private boolean busy;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        wm = getSystemService(WindowManager.class);
        notice = new Notice(this, wm);
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
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        hideMenu();
        if (bubble != null) main.postDelayed(() -> snapBubble(true), 300);
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
        hideMenuNow();
        removeSelection();
        if (notice != null) notice.hide();
        if (bubble != null) {
            safeRemove(bubble);
            bubble = null;
        }
    }

    // ---------------------------------------------------------------- bubble

    /** Usable screen area (screen minus status/navigation bars and cutouts), in screen pixels. */
    private Rect safeArea() {
        WindowMetrics m = wm.getCurrentWindowMetrics();
        Rect b = new Rect(m.getBounds());
        Insets in = m.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        b.left += in.left;
        b.top += in.top;
        b.right -= in.right;
        b.bottom -= in.bottom;
        return b;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showBubble() {
        if (bubble != null) return;
        bubbleSize = dp(54);
        bubble = new BubbleView(this);

        bubbleParams = overlayParams(bubbleSize, bubbleSize);
        Rect a = safeArea();
        int[] saved = Prefs.bubblePos(this);
        if (saved[0] != Integer.MIN_VALUE) {
            bubbleParams.x = saved[0];
            bubbleParams.y = saved[1];
        } else {
            bubbleParams.x = a.right - bubbleSize - dp(12);
            bubbleParams.y = a.top + Math.round(a.height() * 0.35f);
        }

        final int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        bubble.setOnTouchListener(new View.OnTouchListener() {
            int startX, startY;
            float downX, downY;
            boolean dragging;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (snapAnim != null) snapAnim.cancel();
                        startX = bubbleParams.x;
                        startY = bubbleParams.y;
                        downX = e.getRawX();
                        downY = e.getRawY();
                        dragging = false;
                        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(120)
                                .setInterpolator(Ui.EMPHASIZED).start();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                        if (!dragging && Math.hypot(dx, dy) > slop) {
                            dragging = true;
                            hideMenu();
                            v.animate().scaleX(1f).scaleY(1f).setDuration(160)
                                    .setInterpolator(Ui.EMPHASIZED).start();
                        }
                        if (dragging) {
                            bubbleParams.x = startX + Math.round(dx);
                            bubbleParams.y = startY + Math.round(dy);
                            safeUpdate(bubble, bubbleParams);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(220)
                                .setInterpolator(Ui.EMPHASIZED).start();
                        if (dragging) {
                            snapBubble(true);
                        } else if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                            // a tap that just closed the menu via "outside touch" shouldn't reopen it
                            if (menu == null
                                    && SystemClock.uptimeMillis() - menuClosedByOutsideAt < 350) {
                                return true;
                            }
                            toggleMenu();
                        }
                        return true;
                }
                return false;
            }
        });
        wm.addView(bubble, bubbleParams);
        snapBubble(false);

        bubble.setAlpha(0f);
        bubble.setScaleX(0.4f);
        bubble.setScaleY(0.4f);
        bubble.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(420)
                .setInterpolator(new OvershootInterpolator(1.6f)).start();
    }

    /**
     * Stick the bubble to the nearest side edge. Near the top or bottom of the screen it locks
     * into that corner.
     */
    private void snapBubble(boolean animate) {
        if (bubble == null) return;
        Rect a = safeArea();
        int s = bubbleSize, m = dp(10);
        int cx = bubbleParams.x + s / 2, cy = bubbleParams.y + s / 2;

        int tx = cx < a.centerX() ? a.left + m : a.right - s - m;
        int minY = a.top + m, maxY = a.bottom - s - m;
        int zone = Math.round(a.height() * 0.18f);
        int ty;
        if (cy < a.top + zone) ty = minY;
        else if (cy > a.bottom - zone) ty = maxY;
        else ty = Math.max(minY, Math.min(maxY, bubbleParams.y));

        Prefs.saveBubblePos(this, tx, ty);
        if (!animate) {
            bubbleParams.x = tx;
            bubbleParams.y = ty;
            safeUpdate(bubble, bubbleParams);
            return;
        }
        final int fx = bubbleParams.x, fy = bubbleParams.y;
        if (snapAnim != null) snapAnim.cancel();
        snapAnim = ValueAnimator.ofFloat(0f, 1f);
        snapAnim.setDuration(420);
        snapAnim.setInterpolator(new OvershootInterpolator(0.9f));
        snapAnim.addUpdateListener(an -> {
            float f = (float) an.getAnimatedValue();
            bubbleParams.x = Math.round(fx + (tx - fx) * f);
            bubbleParams.y = Math.round(fy + (ty - fy) * f);
            safeUpdate(bubble, bubbleParams);
        });
        snapAnim.start();
    }

    private void setBubbleVisible(boolean visible) {
        if (bubble != null) bubble.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setBusy(boolean b) {
        busy = b;
        if (bubble != null) bubble.setBusy(b);
    }

    // ---------------------------------------------------------------- menu

    private void toggleMenu() {
        if (menu != null) hideMenu();
        else showMenu();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showMenu() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(12), dp(10), dp(10));
        ShineDrawable bg = Ui.panel(this, 18);
        panel.setBackground(bg);
        ShineDrawable.animateWhileAttached(panel, bg, 3600);

        // header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(6), 0, dp(6), 0);
        header.addView(Ui.label(this, "Question Bubble"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView status = Ui.text(this, busy ? "WORKING" : "READY", 9.5f, 700,
                busy ? Ui.WHITE : Ui.FAINT);
        status.setLetterSpacing(0.14f);
        header.addView(status);
        panel.addView(header);

        View div = Ui.divider(this);
        LinearLayout.LayoutParams dlp = (LinearLayout.LayoutParams) div.getLayoutParams();
        dlp.setMargins(dp(4), dp(10), dp(4), dp(6));
        panel.addView(div, dlp);

        List<View> rows = new ArrayList<>();
        rows.add(menuRow(R.drawable.ic_crop, "Select question", "Crop it, or hold to auto-detect", () -> {
            hideMenu();
            startSelect();
        }));
        rows.add(menuRow(R.drawable.ic_type, "Type question", "Ask in your own words", () -> {
            hideMenu();
            if (!checkKey()) return;
            startActivity(new Intent(this, TypeQuestionActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        }));
        rows.add(menuRow(R.drawable.ic_sliders, "Settings", "API key, model, access", () -> {
            hideMenu();
            openSettings();
        }));
        for (View r : rows) panel.addView(r);

        // size + position next to the bubble
        int menuW = dp(256);
        panel.measure(View.MeasureSpec.makeMeasureSpec(menuW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int menuH = panel.getMeasuredHeight();
        Rect a = safeArea();
        boolean bubbleOnLeft = bubbleParams.x + bubbleSize / 2 < a.centerX();

        WindowManager.LayoutParams p = overlayParams(menuW, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.flags |= WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        p.x = bubbleOnLeft ? bubbleParams.x + bubbleSize + dp(10) : bubbleParams.x - menuW - dp(10);
        p.y = bubbleParams.y + bubbleSize / 2 - menuH / 2;
        p.y = Math.max(a.top + dp(8), Math.min(a.bottom - menuH - dp(8), p.y));

        panel.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                menuClosedByOutsideAt = SystemClock.uptimeMillis();
                hideMenu();
                return true;
            }
            return false;
        });

        menu = panel;
        wm.addView(panel, p);

        // open animation: grow out of the bubble, rows cascade in
        panel.setPivotX(bubbleOnLeft ? 0 : menuW);
        panel.setPivotY(bubbleParams.y + bubbleSize / 2f - p.y);
        panel.setAlpha(0f);
        panel.setScaleX(0.86f);
        panel.setScaleY(0.86f);
        panel.setTranslationX(bubbleOnLeft ? -dp(12) : dp(12));
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).translationX(0f)
                .setDuration(320).setInterpolator(Ui.EMPHASIZED).start();
        for (int i = 0; i < rows.size(); i++) {
            View r = rows.get(i);
            r.setAlpha(0f);
            r.setTranslationY(dp(8));
            r.animate().alpha(1f).translationY(0f).setStartDelay(70 + 45L * i)
                    .setDuration(300).setInterpolator(Ui.EMPHASIZED).start();
        }
    }

    private View menuRow(int icon, String title, String sub, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(8), dp(8), dp(8));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, Ui.rounded(Ui.ELEM_PRESSED, dp(12), 0, 0));
        states.addState(new int[]{}, Ui.rounded(0x00000000, dp(12), 0, 0));
        states.setExitFadeDuration(180);
        row.setBackground(states);
        row.setClickable(true);

        FrameLayout iconBox = new FrameLayout(this);
        iconBox.setBackground(Ui.rounded(Ui.ELEM, dp(10), Ui.SEP, dp(1)));
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iconBox.addView(iv, new FrameLayout.LayoutParams(dp(17), dp(17), Gravity.CENTER));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(36), dp(36));
        ip.rightMargin = dp(12);
        row.addView(iconBox, ip);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(this, title, 14, 600, Ui.TEXT);
        TextView st = Ui.text(this, sub, 11.5f, 400, Ui.DIM);
        st.setSingleLine(true);
        st.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(t);
        col.addView(st, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, dp(3)));
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView chev = Ui.text(this, "\u203A", 20, 400, Ui.FAINT);
        row.addView(chev);

        row.setOnClickListener(v -> action.run());
        Ui.pressEffect(row);
        return row;
    }

    /** Animated close. */
    private void hideMenu() {
        if (menu == null) return;
        View m = menu;
        menu = null;
        m.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f).setStartDelay(0).setDuration(150)
                .setInterpolator(Ui.EXIT).withEndAction(() -> safeRemove(m)).start();
    }

    private void hideMenuNow() {
        if (menu != null) {
            safeRemove(menu);
            menu = null;
        }
    }

    // ---------------------------------------------------------------- select question

    private void startSelect() {
        if (busy) {
            notice.show(Notice.Kind.INFO, "Still working", "Answering your last question", 2200);
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
                            notice.show(Notice.Kind.ERROR, "Capture failed",
                                    "Couldn't read the screen. Try again.", 3500);
                            return;
                        }
                        showSelection(soft);
                    }

                    @Override
                    public void onFailure(int errorCode) {
                        setBubbleVisible(true);
                        notice.show(Notice.Kind.ERROR, "Capture failed",
                                "Screenshot error " + errorCode + ". Try again.", 3500);
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
        selection.setAlpha(0f);
        selection.animate().alpha(1f).setDuration(220).setInterpolator(Ui.EMPHASIZED).start();
    }

    private void removeSelection() {
        View v = selection;
        Bitmap shot = screenshot;
        selection = null;
        screenshot = null;
        if (v != null) {
            v.animate().alpha(0f).setDuration(160).setInterpolator(Ui.EXIT).withEndAction(() -> {
                safeRemove(v);
                if (shot != null) shot.recycle();
            }).start();
        } else if (shot != null) {
            shot.recycle();
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
        notice.show(Notice.Kind.LOADING, "Analyzing question", "Asking " + Prefs.model(this), 0);
        String key = Prefs.apiKey(this);
        String model = Prefs.model(this);
        worker.execute(() -> {
            try {
                String answer = OpenAiClient.ask(key, model, prompt, image);
                main.post(() -> {
                    copyToClipboard(answer);
                    notice.show(Notice.Kind.SUCCESS, "Answer copied", answer, 4500);
                    setBusy(false);
                });
            } catch (Exception e) {
                main.post(() -> {
                    notice.show(Notice.Kind.ERROR, "Couldn't get an answer",
                            String.valueOf(e.getMessage()), 5500);
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
        notice.show(Notice.Kind.INFO, "API key required", "Add your OpenAI key in Settings", 3500);
        openSettings();
        return false;
    }

    private void openSettings() {
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private WindowManager.LayoutParams overlayParams(int w, int h) {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        return p;
    }

    private void safeUpdate(View v, WindowManager.LayoutParams p) {
        try {
            if (v.isAttachedToWindow()) wm.updateViewLayout(v, p);
        } catch (Exception ignored) {
        }
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
