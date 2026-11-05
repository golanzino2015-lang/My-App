# Question Bubble (Android)

A floating orb that sits on top of every app. Point it at a question on your screen and the answer
lands on your clipboard. Choose **ChatGPT** (OpenAI), **Claude** (Anthropic) or **Gemini** (Google);
each uses your own API key.

## Features

- **The orb** is a black glass sphere drawn in code, with drifting inner light and a rim highlight.
  - Drag it with spring physics. Fling it to either side and it snaps to the nearest edge, or into
    a corner near the top or bottom.
  - Drag it onto the **X** at the bottom of the screen to hide it.
  - It dims when idle and lights up while it's working.
  - When an answer arrives it sends out a ripple.
- **Tap the orb** to open the menu:
  - **Scan question**: the screen freezes. Drag a box around the question, press and hold on it
    until the ring fills to auto-detect it, or tap **Full screen**.
  - **Type**: a chat-style panel slides up over the current app.
  - **History**: every answer, grouped by day. Tap to copy, long-press to delete.
  - **Settings**.
  - **Last answer**: copy it again with one tap.
- **Chat from the bubble**: swipe the menu sideways (or tap **Chat**). Type right there, tap **+** to
  capture part of the screen or pick a photo, and press send or Enter: the chat opens and the
  message is sent.
- **Chat**: full-screen, in the style of modern AI chat apps.
  - ☰ opens your saved chats (they save themselves; press and hold to delete).
  - The model name under the title (or in the message box) opens the **model switcher**: every
    model from ChatGPT, Claude and Gemini, plus an **Extended thinking** switch.
  - **Think**: the AI reasons longer and shows a collapsible "Thought for 12s" section.
  - Web search is always on: the AI looks things up when it needs to ("Searched the web · 2 searches").
  - Copy any reply; retry the last one.
- **Main app**: swipe sideways between **Home** and **Chats** (or tap the tabs). Chats has your
  conversation list and a message box to start a new one.
- **Hold the orb** to start a scan right away.
- **Status pill** at the top of the screen shows progress, then "Copied to clipboard" with the
  answer. Tap it to dismiss.
- **First-launch onboarding**: welcome, permission (detected live), API key (verified with OpenAI).
- **Choose your AI**: ChatGPT, Claude or Gemini, each with its own key (checked when you save it)
  and its own model list, plus a custom model name.
  - Claude uses the official Anthropic Java SDK, with Claude Opus 5.5 as the default model. If
    Claude's safety check declines a question, the request is retried automatically on another
    Claude model (server-side fallback).
- **Home screen**:
  - Bubble on/off switch and today's stats.
  - Recent answers.
  - Model picker, Short / Detailed answer style, bubble size, haptics.
- Typeface: **Inter** (bundled, SIL Open Font License, see `FONT_LICENSE_Inter.txt`).

## Get the APK

1. On GitHub open **Actions → Build APK** and open the latest green run.
2. Download the **QuestionBubble-apk** artifact and unzip it to get `app-debug.apk`.
3. Install it on your phone.

Or open the folder in **Android Studio** and press Run.

## Setup on the phone

The app walks you through this the first time you open it:

1. **Allow screen access.** Accessibility → Question Bubble → on.
   - If the switch is greyed out (Android 13 and newer), go to *Settings → Apps → Question Bubble
     → ⋮ → Allow restricted settings*.
2. **Choose your AI and paste its API key.** Keys are stored only on the phone and only sent to
   that company's API.
   - ChatGPT: platform.openai.com/api-keys
   - Claude: console.anthropic.com/settings/keys
   - Gemini: aistudio.google.com/apikey

Requires Android 11 or newer.

## Code layout

```
app/src/main/java/com/questionbubble/app/
  BubbleService.java        accessibility service: overlay, scan, answer flow
  FloatingBubble.java       orb window: drag, spring, fling, snapping, idle dim
  DismissTarget.java        drop-to-hide target
  MenuPopover.java          the menu that grows out of the orb
  Island.java               top-of-screen status pill
  SelectionView.java        scan overlay (crop, hold-to-detect, full screen)
  OrbRenderer.java/OrbView  the animated orb
  OnboardingActivity.java   first-launch flow
  MainActivity.java         home
  HistoryActivity.java      saved answers
  TypeQuestionActivity.java quick one-off question sheet
  ChatActivity.java         full-screen chat: drawer, thinking blocks, search chips, retry
  Composer.java             message box: attach, Think, Search, model switcher, send
  ModelList.java            model switcher grouped by AI
  PickImageActivity.java    photo picker for the bubble
  Chats.java / ChatApi.java chat storage; multi-turn requests with thinking + web search
  PagerFrame.java           swipeable Tools / Chat pages in the bubble menu
  Md.java                   light Markdown rendering for replies
  Sheet.java / Sheets.java  bottom sheets (key, model, confirm, help)
  Theme.java / Fonts.java   colors, Inter type scale, motion, helpers
  PillButton, ToggleView, SegmentedView, Spinner, CheckView, TypingDots, Snack, Rows, KeyField
  Ai.java / Provider.java   picks the AI; provider list, models, key links
  OpenAiClient.java         ChatGPT (OpenAI API)
  ClaudeClient.java         Claude (official Anthropic Java SDK)
  GeminiClient.java         Gemini (Google Generative Language API)
  History.java / Prefs.java storage
```
