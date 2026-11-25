# My Own AI

A chat AI built completely from scratch, trained and run on your phone.

- **No borrowed brain.** The AI is a small GPT-style transformer, the same kind
  of design as ChatGPT and Claude but thousands of times smaller, written by
  hand in plain JavaScript. It starts as random numbers and knows nothing.
- **Trains on your phone.** It learns to talk by reading about 9,000 generated
  example chats: greetings, feelings, animals, colors, math, space, countries,
  jokes and more. Training runs in the background of the page.
- **Chat** with it any time, even while it's training.
- **Teach** it new answers ("what is my dog called" → "your dog is called rex.").
- **Offline.** Everything runs on the phone. Nothing is sent anywhere.
  Progress saves automatically in the browser's storage.

## What to expect

| Training time on a phone | What it can do |
|---|---|
| 0–10 minutes | Random words and gibberish |
| 10–60 minutes | Real words, broken sentences, mixed-up facts |
| 1–3 hours | Short sentences, gets many quiz questions right |
| Overnight | Knows most of its lessons, chats simply |

It will always be a *tiny* AI. It only knows what's in its lessons, and it will
sometimes say wrong or strange things. That's normal for a brain this size.

## Files

```
engine.js   the transformer: math, learning (backpropagation), AdamW optimizer
lessons.js  the starter lessons; edit this to change what it knows
host.js     training loop, chatting, teaching, saving progress
app.html    the phone app screen
build.js    joins everything into index.html (run: node build.js)
index.html  the finished app, one file
```

## How it works

1. **Words to numbers.** Every word gets an ID. A chat becomes a list like
   `<user> what color is the sky <ai> the sky is blue . <end>`.
2. **The brain.** Each word ID becomes a list of numbers (an "embedding").
   Several transformer layers use *attention* to look back at earlier words,
   then predict which word comes next.
3. **Learning.** After each guess, it measures how wrong it was (the
   "mistakes per word" chart) and nudges all its numbers slightly in the
   direction that would have made the guess better. Repeat millions of times.
4. **Talking.** To answer you, it predicts one word at a time until it
   predicts `<end>`.
