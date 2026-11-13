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
import android.os.SystemClock;
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

    /** The running service, or null when Accessibility access is off. */
    static BubbleService instance;

    private static final String CROP_PROMPT = "Answer the question shown in this image.";
    private static final String FULL_PROMPT =
            "This is a screenshot of the user's screen. Find the main question on it and answer it.";
    private static final String LOCATE_AND_ANSWER =
            "This is a screenshot of the user's phone. They pressed and held on it: the RED CIRCLE "
            + "marks where their finger was. Find the ONE question at or nearest to the red circle "
            + "(including its answer choices, if it has any) and ignore every other question on the "
            + "screen. Then answer that question.\n"
            + "Reply with ONLY this JSON object and nothing else (no code fences):\n"
            + "{\"box\":[ymin,xmin,ymax,xmax],\"answer\":\"...\"}\n"
            + "- box: the bounding box of that whole question and its choices, as integers from 0 "
            + "to 1000 relative to the image's height (y) and width (x).\n"
            + "- answer: your answer to it, following your usual answer rules.";
    private static final String LOCATE_ONLY =
            "This is a screenshot of the user's phone. They pressed and held on it: the RED CIRCLE "
            + "marks where their finger was. Find the ONE question or block of content at or nearest "
            + "to the red circle (include answer choices, if any). Highlighted (selected) text, if any, "
            + "is also where they pressed.\n"
            + "Reply with ONLY this JSON object and nothing else (no code fences):\n"
            + "{\"box\":[ymin,xmin,ymax,xmax]}\n"
            + "where box is its bounding box as integers from 0 to 1000 relative to the image's "
            + "height (y) and width (x).";

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
    private String chatCaptureId; // set while a scan is collecting an image for a chat

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener = (sp, key) -> {
        if (Prefs.K_BUBBLE_VISIBLE.equals(key)) {
            if (Prefs.bubbleVisible(this)) showBubble();
            else {
                menu.dismissNow();
                bubble.remove();
            }
        } else if ("theme".equals(key)) {
            Theme.apply(this);
            menu.dismissNow();
            if (bubble.isShowing()) {
                bubble.removeNow();
                showBubble();
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
                island.show(Island.Kind.INFO, "Bubble hidden", "Open My App to bring it back", 3200);
            }
        });
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(prefListener);
        if (Prefs.bubbleVisible(this)) showBubble();
        instance = this;
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
        if (instance == this) instance = null;
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
        openMenu(menuDraftImages.isEmpty() && menuDraftText.isEmpty() ? 0 : 1);
    }

    /** What's typed in the bubble's message box, kept while the user captures or picks an image. */
    private String menuDraftText = "";
    private final java.util.List<String> menuDraftImages = new java.util.ArrayList<>();
    static final String MENU_CAPTURE = "__menu__";

    private void saveMenuDraft() {
        if (!menu.isShowing()) return;
        menuDraftText = menu.draftText();
        menuDraftImages.clear();
        menuDraftImages.addAll(menu.draftImages());
    }

    /** The bubble's photo picker finished (name is null if cancelled). */
    void bubbleImagePicked(String name) {
        if (name != null && !menuDraftImages.contains(name)) menuDraftImages.add(name);
        main.postDelayed(() -> {
            if (bubble.isShowing() && !menu.isShowing()) openMenu(1);
        }, 250);
    }

    private void openMenu(int page) {
        Rect orb = bubble.orbBounds();
        menu.show(orb, bubble.safeArea(), bubble.onLeftSide(), busy, History.latest(this),
                page, menuDraftText, menuDraftImages,
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
                    public void chat(String chatId, boolean fresh) {
                        closeMenu();
                        openChat(chatId, fresh, null);
                    }

                    @Override
                    public void send(String text, java.util.List<String> images) {
                        // Send from the bubble: open a new chat that sends this right away.
                        ChatActivity.draftText = text;
                        ChatActivity.draftImages.clear();
                        ChatActivity.draftImages.addAll(images);
                        menuDraftText = "";
                        menuDraftImages.clear();
                        menu.dismissNow();
                        bubble.setMenuOpen(false);
                        if (!checkKey()) return;
                        Intent i = new Intent(BubbleService.this, ChatActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .putExtra(ChatActivity.EXTRA_NEW, true)
                                .putExtra(ChatActivity.EXTRA_AUTOSEND, true);
                        startActivity(i);
                    }

                    @Override
                    public void attachScreen() {
                        saveMenuDraft();
                        captureForChat(MENU_CAPTURE);
                    }

                    @Override
                    public void attachPhoto() {
                        saveMenuDraft();
                        menu.dismissNow();
                        bubble.setMenuOpen(false);
                        startActivity(new Intent(BubbleService.this, PickImageActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    }

                    @Override
                    public void needKey() {
                        saveMenuDraft();
                        closeMenu();
                        island.show(Island.Kind.INFO, "Add that AI's key first", "Opening My App", 3000);
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
        saveMenuDraft();
        if (menu.isShowing()) menu.dismiss();
        bubble.setMenuOpen(false);
    }

    // ---------------------------------------------------------------- scan

    /** Called by the chat screen: capture part of the screen and attach it to that chat. */
    void captureForChat(String chatId) {
        chatCaptureId = chatId;
        if (menu != null) menu.dismissNow();
        main.postDelayed(this::startScan, 260);
    }

    void openChat(String chatId, boolean fresh, String attach) {
        Intent i = new Intent(this, ChatActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (chatId != null) i.putExtra(ChatActivity.EXTRA_CHAT, chatId);
        if (fresh) i.putExtra(ChatActivity.EXTRA_NEW, true);
        if (attach != null) i.putExtra(ChatActivity.EXTRA_ATTACH, attach);
        startActivity(i);
    }

    /** Save the picked area and hand it back: to the chat, or to the bubble's message box. */
    private void deliverToChat(Bitmap img) {
        String id = chatCaptureId;
        chatCaptureId = null;
        worker.execute(() -> {
            String name = null;
            try {
                name = Chats.saveImage(this, img);
            } catch (Exception ignored) {
            } finally {
                img.recycle();
            }
            String n = name;
            main.post(() -> {
                if (MENU_CAPTURE.equals(id)) bubbleImagePicked(n);
                else openChat(id, false, n);
            });
        });
    }

    private void startScan() {
        if (chatCaptureId == null && busy) {
            island.show(Island.Kind.INFO, "One moment", "Still answering your last question", 2200);
            return;
        }
        if (chatCaptureId == null && !checkKey()) return;
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
                            reopenChatAfterFailure();
                            island.show(Island.Kind.ERROR, "Couldn't capture the screen", "Please try again", 3500);
                            return;
                        }
                        showSelection(soft);
                    }

                    @Override
                    public void onFailure(int errorCode) {
                        bubble.setHidden(false);
                        reopenChatAfterFailure();
                        island.show(Island.Kind.ERROR, "Couldn't capture the screen",
                                "Try again in a second (error " + errorCode + ")", 3500);
                    }
                }), 220);
    }

    private void reopenChatAfterFailure() {
        if (chatCaptureId == null) return;
        String id = chatCaptureId;
        chatCaptureId = null;
        if (MENU_CAPTURE.equals(id)) bubbleImagePicked(null);
        else main.postDelayed(() -> openChat(id, false, null), 600);
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
                    if (chatCaptureId != null) deliverToChat(crop);
                    else ask(crop, CROP_PROMPT);
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
                if (chatCaptureId != null) deliverToChat(full);
                else ask(full, FULL_PROMPT);
            }

            @Override
            public void onCancel() {
                locateCancelled = true;
                removeSelection(true);
                if (chatCaptureId != null) {
                    String id = chatCaptureId;
                    chatCaptureId = null;
                    if (MENU_CAPTURE.equals(id)) bubbleImagePicked(null);
                    else openChat(id, false, null);
                }
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

    private boolean locateCancelled;


    /**
     * Find where the question is with Gemini 3.5 Flash-Lite (fast and cheap). Returns null when
     * there's no Gemini key or it didn't work, so the caller can fall back. Background thread.
     */
    private Rect geminiBox(Bitmap marked, String prompt, int w, int h) {
        String key = Prefs.apiKey(this, Provider.GEMINI);
        if (key.isEmpty()) return null;
        try {
            return parseBox(GeminiClient.locate(key, marked, prompt), w, h);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Press-and-hold on the scan screen. Gemini 3.5 Flash-Lite finds the question nearest the
     * finger, the box animates onto it, then your chosen AI answers just that question. Without a
     * Gemini key, your chosen AI finds and answers it in one request.
     */
    private void autoDetect(int x, int y) {
        Bitmap shot = screenshot;
        SelectionView sv = selection;
        if (shot == null || sv == null) return;
        boolean forChat = chatCaptureId != null;
        int w = shot.getWidth(), h = shot.getHeight();

        Bitmap marked = shot.copy(Bitmap.Config.ARGB_8888, true);
        Canvas cv = new Canvas(marked);
        float r = Math.max(Theme.dp(this, 26), w * 0.04f);
        Paint red = new Paint(Paint.ANTI_ALIAS_FLAG);
        red.setStyle(Paint.Style.STROKE);
        red.setStrokeWidth(Math.max(Theme.dp(this, 4), w * 0.006f));
        red.setColor(Color.RED);
        cv.drawCircle(x, y, r, red);
        red.setStyle(Paint.Style.FILL);
        cv.drawCircle(x, y, red.getStrokeWidth(), red);

        boolean gemini = !Prefs.apiKey(this, Provider.GEMINI).isEmpty();
        sv.startSearching("Finding the question", gemini ? "Using Gemini 3.5 Flash-Lite"
                : "Asking " + Prefs.provider(this).label, forChat ? "Adding it to your chat" : "Getting your answer");
        locateCancelled = false;
        if (!forChat) {
            busy = true;
            bubble.setBusy(true);
        }
        boolean detailed = Prefs.detailed(this);
        worker.execute(() -> {
            Rect fast = geminiBox(marked, LOCATE_ONLY, w, h);
            String reply = null, error = null;
            if (fast == null) {
                try {
                    reply = Ai.ask(this, detailed, forChat ? LOCATE_ONLY : LOCATE_AND_ANSWER, marked);
                } catch (Exception e) {
                    error = String.valueOf(e.getMessage());
                }
            }
            marked.recycle();
            String rep = reply, err = error;
            main.post(() -> {
                if (!forChat) {
                    busy = false;
                    bubble.setBusy(false);
                }
                if (locateCancelled || selection != sv || screenshot != shot) return;
                if (fast == null && err != null && !forChat) {
                    removeSelection(true);
                    bubble.errorShake();
                    island.show(Island.Kind.ERROR, "Couldn't get an answer", err, 5500);
                    return;
                }
                Rect box = fast != null ? fast : rep == null ? null : parseBox(rep, w, h);
                Rect area = box != null ? box
                        : new Rect(0, Math.max(0, y - Math.round(h * 0.15f)), w, Math.min(h, y + Math.round(h * 0.15f)));
                sv.showDetected(area, () -> {
                    if (forChat || fast != null) {
                        Bitmap crop = Bitmap.createBitmap(shot, area.left, area.top, area.width(), area.height())
                                .copy(Bitmap.Config.ARGB_8888, false);
                        removeSelection(true);
                        if (forChat) deliverToChat(crop);
                        else ask(crop, CROP_PROMPT);
                    } else {
                        removeSelection(true);
                        finishAnswer(parseAnswer(rep));
                    }
                });
            });
        });
    }

    /** "{"box":[ymin,xmin,ymax,xmax],...}" (0-1000) -> padded screen rect, or null. */
    private Rect parseBox(String reply, int w, int h) {
        try {
            int s0 = reply.indexOf('{'), e0 = reply.lastIndexOf('}');
            if (s0 < 0 || e0 <= s0) return null;
            org.json.JSONArray b = new org.json.JSONObject(reply.substring(s0, e0 + 1)).optJSONArray("box");
            if (b == null || b.length() != 4) return null;
            double y0 = b.getDouble(0), x0 = b.getDouble(1), y1 = b.getDouble(2), x1 = b.getDouble(3);
            double scale = Math.max(Math.max(y0, y1), Math.max(x0, x1)) <= 1.5 ? 1.0 : 1000.0;
            int top = (int) Math.round(Math.min(y0, y1) / scale * h);
            int bottom = (int) Math.round(Math.max(y0, y1) / scale * h);
            int left = (int) Math.round(Math.min(x0, x1) / scale * w);
            int right = (int) Math.round(Math.max(x0, x1) / scale * w);
            Rect r = new Rect(left, top, right, bottom);
            int pad = Theme.dp(this, 10);
            r.inset(-pad, -pad);
            if (!r.intersect(0, 0, w, h)) return null;
            if (r.width() < w * 0.08f || r.height() < Theme.dp(this, 24)) return null;
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    private static String parseAnswer(String reply) {
        if (reply == null) return "";
        try {
            int s0 = reply.indexOf('{'), e0 = reply.lastIndexOf('}');
            if (s0 >= 0 && e0 > s0) {
                String a = new org.json.JSONObject(reply.substring(s0, e0 + 1)).optString("answer", "").trim();
                if (!a.isEmpty()) return a;
            }
        } catch (Exception ignored) {
        }
        return reply.replaceAll("(?s)```(json)?", "").trim();
    }

    private void finishAnswer(String answer) {
        if (answer == null || answer.isEmpty()) {
            bubble.errorShake();
            island.show(Island.Kind.ERROR, "Couldn't get an answer", "Try again or drag a box around it", 4500);
            return;
        }
        History.add(this, "scan", "", answer);
        copy(answer);
        bubble.celebrate();
        island.show(Island.Kind.SUCCESS, "Copied to clipboard", answer, 5000);
    }

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
        island.show(Island.Kind.INFO, "Add your " + Prefs.provider(this).label + " key", "Opening My App", 3000);
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
