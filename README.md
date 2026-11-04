# Question Bubble (Android)

A floating bubble that sits on top of every app. Tap it to open a small menu:

| Button | What it does |
|---|---|
| **Select Question** | Freezes the screen. **Drag a box** around a question, **or long-press** the question and it auto-detects the block your finger is on. The image goes to ChatGPT and the answer is **copied to your clipboard**. |
| **Type Question** | A small pop-up where you type a question. The answer is shown and copied to your clipboard. |
| **Settings** | Opens the app (permission status, API key, model). |

Drag the bubble and it snaps to the nearest edge (or corner, near the top/bottom). Tap it again, or anywhere else, to close the menu.

## Get the APK (no converter needed)

This is a native Android project, so it builds directly into an APK:

1. On GitHub open **Actions → Build APK** and click the latest green run
   (or **Run workflow** to start one).
2. Download the **QuestionBubble-apk** artifact (a zip) and unzip it to get `app-debug.apk`.
3. Copy it to your phone and open it. Allow "install unknown apps" if asked.

Or open the folder in **Android Studio** and press ▶ Run.

## First-time setup on the phone

1. Open **Question Bubble**. It shows that permission is needed. Tap **Open settings**.
2. In Accessibility → *Installed/Downloaded apps* → **Question Bubble** → turn it **on**.
   (This is the same kind of permission auto-clickers use.)
   - **Android 13+ greyed out / "Restricted setting"?** Go to *Settings → Apps → Question Bubble →
     ⋮ (top-right) → Allow restricted settings*, then try again. The app has a button for this.
3. Back in the app, paste your **OpenAI API key** and tap **Save**.
   The key is stored only on your phone and only sent to `api.openai.com`.
4. The green bubble appears. Done.

To hide the bubble, turn Question Bubble off in Accessibility settings.

## Notes

- Needs **Android 11 or newer** (that's when apps got the screenshot ability used here).
- Default model is `gpt-4o`. You can change it in the app to any OpenAI model that accepts images.
- Each question costs a small amount on your OpenAI account.

## Code layout

```
app/src/main/java/com/questionbubble/app/
  BubbleService.java        floating bubble, menu, screenshots, auto-detect, clipboard
  SelectionView.java        full-screen crop / long-press overlay
  OpenAiClient.java         calls the OpenAI Chat Completions API
  TypeQuestionActivity.java the "Type Question" pop-up
  MainActivity.java         permission + API-key screen
  Ui.java                   colors, fonts, buttons, inputs, dialogs, animations
  ShineDrawable.java        glass panel with the moving shine outline
  BubbleView.java           the bubble graphic + loading ring
  Notice.java               top-of-screen notifications
  SpinnerView.java          loading spinner
  Prefs.java                saved settings
```
