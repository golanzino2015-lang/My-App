package com.questionbubble.app;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPInputStream;

/**
 * "My AI": a transformer trained from scratch on this phone. Owns the brain file, the lessons,
 * the training thread and chatting. One instance per app.
 */
final class LocalAI {

    enum Size {
        QUICK("Quick", 128, 4, 4, 2e-3f, "About 1 million numbers. Talks within an hour or two."),
        DAY("Day", 192, 6, 6, 1.5e-3f, "About 3 million numbers. Recommended: good within a day."),
        MAX("Max", 256, 8, 8, 1e-3f, "About 7 million numbers. Smartest, needs a day or more.");

        final String label, about;
        final int C, L, H;
        final float lr;

        Size(String label, int C, int L, int H, float lr, String about) {
            this.label = label;
            this.C = C;
            this.L = L;
            this.H = H;
            this.lr = lr;
            this.about = about;
        }
    }

    static final int T = 128;              // words it can see at once
    static final int SEQS_PER_STEP = 16;   // 16 x 128 = 2,048 words per learning step
    static final int WARMUP = 300, DECAY = 20000;
    static final int SPARE_WORDS = 256;    // room for taught words before the table must grow
    static final int[] BANNED = {0, Words.UNK, Words.USER, Words.AI};
    static final String[] PREVIEW = {"hi", "how are you", "what is your name", "tell me a joke", "who made you",
            "what is the capital of japan", "what is 7 plus 5", "i am sad", "what color is a banana"};

    interface Listener {
        void onUpdate();
    }

    private static LocalAI inst;

    static synchronized LocalAI get(Context c) {
        if (inst == null) inst = new LocalAI(c.getApplicationContext());
        return inst;
    }

    private final Context app;
    private final File dir, modelFile, metaFile;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();

    private long h;                 // native brain
    private String name = "";
    private Size size = Size.DAY;
    private Words.Vocab vocab;
    private final List<String[]> lessons = new ArrayList<>();
    private boolean loaded, dataReady;
    private String busy;            // non-null while creating/loading

    // stats
    private long tokens;
    private double seconds;
    private float speed;            // words per second
    private final ArrayList<float[]> lossHist = new ArrayList<>(); // {step, loss}
    private String sampleQ = "", sampleA = "";
    private String error;

    // PC backup
    static final long BACKUP_EVERY_MS = 10 * 60_000;
    private volatile boolean uploading;
    private volatile long lastBackup;
    private volatile String backupNote = "";

    private volatile boolean training;
    private Thread trainer;

    private LocalAI(Context app) {
        this.app = app;
        dir = new File(app.getFilesDir(), "myai");
        dir.mkdirs();
        modelFile = new File(dir, "brain.bin");
        metaFile = new File(dir, "brain.json");
        // Cheap peek at the saved brain so screens can show its name before it is loaded.
        try {
            if (metaFile.exists()) {
                JSONObject j = new JSONObject(new String(Files.readAllBytes(metaFile.toPath()), StandardCharsets.UTF_8));
                name = j.optString("name");
                tokens = j.optLong("tokens");
            }
        } catch (Exception ignored) { }
    }

    /** True when a brain exists, loaded or still on disk. */
    boolean saved() {
        return h != 0 || (!loaded && metaFile.exists() && modelFile.exists());
    }

    // ---------------------------------------------------------------- state for the UI

    void addListener(Listener l) { listeners.add(l); }
    void removeListener(Listener l) { listeners.remove(l); }

    private void changed() {
        main.post(() -> { for (Listener l : listeners) l.onUpdate(); });
    }

    /** Loads the saved brain if there is one. Safe to call often. Blocks: call off the main thread. */
    void ensureLoaded() {
        synchronized (lock) {
            if (loaded) return;
            loaded = true;
            if (!metaFile.exists() || !modelFile.exists()) return;
            busy = "Loading brain…";
            changed();
            try {
                JSONObject j = new JSONObject(new String(Files.readAllBytes(metaFile.toPath()), StandardCharsets.UTF_8));
                name = j.getString("name");
                size = Size.valueOf(j.optString("size", "DAY"));
                List<String> words = new ArrayList<>();
                JSONArray w = j.getJSONArray("words");
                for (int i = 0; i < w.length(); i++) words.add(w.getString(i));
                vocab = new Words.Vocab(words);
                JSONArray ls = j.optJSONArray("lessons");
                if (ls != null) for (int i = 0; i < ls.length(); i++) {
                    JSONArray p = ls.getJSONArray(i);
                    lessons.add(new String[]{p.getString(0), p.getString(1)});
                }
                tokens = j.optLong("tokens");
                seconds = j.optDouble("seconds");
                JSONArray lh = j.optJSONArray("loss");
                if (lh != null) for (int i = 0; i + 1 < lh.length(); i += 2)
                    lossHist.add(new float[]{(float) lh.getDouble(i), (float) lh.getDouble(i + 1)});
                sampleQ = j.optString("sampleQ");
                sampleA = j.optString("sampleA");
                h = NativeAI.load(modelFile.getPath(), threadsFor(size, vocab.size() + SPARE_WORDS));
                if (h == 0) throw new Exception("brain file is damaged");
                buildData();
            } catch (Throwable e) {
                error = "Couldn't load the saved brain: " + e.getMessage();
                if (h != 0) NativeAI.free(h);
                h = 0;
            }
            busy = null;
            changed();
        }
    }

    boolean exists() { return h != 0; }
    String busy() { return busy; }
    String error() { return error; }
    String name() { return name; }
    Size size() { return size; }
    boolean isTraining() { return training; }
    long step() { return h == 0 ? 0 : NativeAI.step(h); }
    long params() { return h == 0 ? 0 : NativeAI.numParams(h); }
    int threads() { return h == 0 ? 0 : NativeAI.threads(h); }
    int words() { return vocab == null ? 0 : vocab.size(); }
    long tokens() { return tokens; }
    double seconds() { return seconds; }
    float speed() { return training ? speed : 0; }
    String sampleQ() { return sampleQ; }
    String sampleA() { return sampleA; }

    float lastLoss() {
        synchronized (lossHist) { return lossHist.isEmpty() ? Float.NaN : lossHist.get(lossHist.size() - 1)[1]; }
    }

    float[][] lossHistory() {
        synchronized (lossHist) { return lossHist.toArray(new float[0][]); }
    }

    List<String[]> lessons() {
        synchronized (lessons) { return new ArrayList<>(lessons); }
    }

    // ---------------------------------------------------------------- create / delete

    /** Makes a new random brain. Blocks; call off the main thread. */
    void create(String aiName, Size s) {
        busy = "Building brain…";
        error = null;
        changed();
        stopTraining();
        synchronized (lock) {
            busy = "Building brain…";
            error = null;
            changed();
            freeBrain();
            name = aiName.trim().isEmpty() ? "Pip" : aiName.trim();
            size = s;
            lessons.clear();
            tokens = 0;
            seconds = 0;
            synchronized (lossHist) { lossHist.clear(); }
            sampleQ = sampleA = "";
            vocab = new Words.Vocab(Arrays.asList(Words.SPECIAL));
            try {
                buildData();
                int V = vocab.size() + SPARE_WORDS;
                h = NativeAI.create(V, s.C, s.L, s.H, T, new Random().nextInt(1 << 30) + 1, threadsFor(s, V));
                if (h == 0) throw new Exception("not enough memory");
                NativeAI.setData(h, pendingStream, pendingStarts);
                pendingStream = pendingStarts = null;
                dataReady = true;
                save();
            } catch (Throwable e) {
                error = "Couldn't create the brain: " + e.getMessage();
                freeBrain();
            }
            loaded = true;
            busy = null;
            changed();
        }
    }

    void delete() {
        stopTraining();
        synchronized (lock) {
            freeBrain();
            modelFile.delete();
            metaFile.delete();
            vocab = null;
            lessons.clear();
            synchronized (lossHist) { lossHist.clear(); }
            tokens = 0;
            seconds = 0;
            sampleQ = sampleA = "";
        }
        changed();
    }

    private void freeBrain() {
        if (h != 0) NativeAI.free(h);
        h = 0;
        dataReady = false;
    }

    /** As many threads as the phone has cores, limited so all their memory fits comfortably. */
    private int threadsFor(Size s, int V) {
        int cores = Runtime.getRuntime().availableProcessors();
        long acts = 2L * T * s.C + (long) s.L * (18L * T * s.C + 4L * T + (long) s.H * T * T);
        long params = (long) V * s.C + (long) T * s.C + (long) s.L * 12L * s.C * s.C;
        long perThread = 4L * (2 * acts + params + (long) T * V);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
        long budget = Math.min(mi.totalMem / 4, 1536L << 20);
        int byMem = (int) Math.max(1, budget / Math.max(1, perThread));
        return Math.max(1, Math.min(cores, Math.min(byMem, 16)));
    }

    // ---------------------------------------------------------------- lessons -> numbers

    private int[] pendingStream, pendingStarts;

    /** Reads the bundled lessons plus the user's own, turns them into word numbers and hands them to the brain. */
    private void buildData() throws Exception {
        String me = String.join(" ", Words.userWords(name));
        if (me.isEmpty()) me = "pip";
        List<String> proper = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(app.getAssets().open("myai/proper.txt"), StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) if (!line.isEmpty()) proper.add(line);
        }
        proper.add(me);
        Words.setProper(proper);

        List<String[]> mine = lessons();
        Words.IntList stream = new Words.IntList(), starts = new Words.IntList();
        Random rnd = new Random(7);
        boolean grow = h == 0;  // a new brain learns every word in the lessons
        for (String[] l : mine) addExchange(l, new Words.IntList(), true); // your words are always learned
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(app.getAssets().open("myai/lessons.bin")), StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                String[] parts = line.replace("zzname", me).split("\t");
                starts.add(stream.n);
                int at = !mine.isEmpty() && rnd.nextInt(10) < 3 ? rnd.nextInt(parts.length / 2 + 1) : -1;
                for (int i = 0; i + 1 < parts.length; i += 2) {
                    if (i / 2 == at) addExchange(mine.get(rnd.nextInt(mine.size())), stream, grow);
                    addExchange(new String[]{parts[i], parts[i + 1]}, stream, grow);
                }
                if (at == parts.length / 2) addExchange(mine.get(rnd.nextInt(mine.size())), stream, grow);
            }
        }
        // Each of your lessons shows up often enough to be learned.
        for (String[] l : mine) for (int i = 0; i < 60; i++) {
            starts.add(stream.n);
            addExchange(l, stream, true);
        }
        userCache.clear();
        replyCache.clear();
        int[] s = stream.toArray(), st = starts.toArray();
        if (h == 0) {
            pendingStream = s;
            pendingStarts = st;
            return;
        }
        int V = NativeAI.config(h)[0];
        if (vocab.size() > V && !NativeAI.growVocab(h, vocab.size() + SPARE_WORDS)) throw new Exception("not enough memory");
        NativeAI.setData(h, s, st);
        dataReady = true;
    }

    private final java.util.HashMap<String, List<String>> userCache = new java.util.HashMap<>(), replyCache = new java.util.HashMap<>();

    private void addExchange(String[] ex, Words.IntList out, boolean learnWords) {
        List<String> u = userCache.get(ex[0]), a = replyCache.get(ex[1]);
        if (u == null) userCache.put(ex[0], u = Words.userWords(ex[0]));
        if (a == null) replyCache.put(ex[1], a = Words.normalize(ex[1]));
        if (learnWords) {
            for (String w : u) if (!Words.UNK_MARK.equals(w)) vocab.add(w);
            for (String w : a) vocab.add(w);
        }
        out.add(Words.USER);
        for (String w : u) out.add(vocab.id(w));
        out.add(Words.AI);
        for (String w : a) out.add(vocab.id(w));
        out.add(Words.END);
    }

    /** Replaces your lessons and retrains on them. Blocks; call off the main thread. */
    void setLessons(List<String[]> list) {
        boolean was = training;
        stopTraining();
        synchronized (lock) {
            synchronized (lessons) {
                lessons.clear();
                for (String[] l : list) if (!l[0].trim().isEmpty() && !l[1].trim().isEmpty()) lessons.add(new String[]{l[0].trim(), l[1].trim()});
            }
            busy = "Adding lessons…";
            changed();
            try {
                if (h != 0) {
                    buildData();
                    save();
                }
            } catch (Throwable e) {
                error = "Couldn't add lessons: " + e.getMessage();
            }
            busy = null;
            changed();
        }
        if (was) startTraining();
    }

    // ---------------------------------------------------------------- training

    private float lrAt(long step) {
        float peak = size.lr, min = peak * 0.1f;
        if (step < WARMUP) return peak * (step + 1) / WARMUP;
        double p = Math.min(1.0, (step - WARMUP) / (double) DECAY);
        return (float) (min + (peak - min) * 0.5 * (1 + Math.cos(Math.PI * p)));
    }

    void startTraining() {
        synchronized (lock) {
            if (training || h == 0 || !dataReady) return;
            training = true;
            trainer = new Thread(this::trainLoop, "my-ai-training");
            trainer.setPriority(Thread.NORM_PRIORITY);
            trainer.start();
        }
        changed();
    }

    void stopTraining() {
        Thread t;
        synchronized (lock) {
            training = false;
            t = trainer;
            trainer = null;
        }
        if (t != null && t != Thread.currentThread()) {
            try { t.join(); } catch (InterruptedException ignored) { }
        }
        changed();
    }

    private void trainLoop() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        long lastSave = System.currentTimeMillis(), lastUi = 0, windowStart = System.nanoTime();
        long windowTokens = 0;
        double lossSum = 0;
        int lossN = 0;
        while (training) {
            long t0 = System.nanoTime();
            long step = NativeAI.step(h);
            float loss = NativeAI.trainStep(h, SEQS_PER_STEP, lrAt(step), 0.01f);
            long t1 = System.nanoTime();
            if (loss < 0 || Float.isNaN(loss)) {
                error = "Training stopped: the lessons could not be read.";
                training = false;
                break;
            }
            int n = SEQS_PER_STEP * T;
            tokens += n;
            windowTokens += n;
            seconds += (t1 - t0) / 1e9;
            lossSum += loss;
            lossN++;
            if (lossN == 10) {
                synchronized (lossHist) {
                    lossHist.add(new float[]{step + 1, (float) (lossSum / lossN)});
                    if (lossHist.size() > 600) {
                        ArrayList<float[]> keep = new ArrayList<>();
                        for (int i = 0; i < lossHist.size(); i += 2) keep.add(lossHist.get(i));
                        lossHist.clear();
                        lossHist.addAll(keep);
                    }
                }
                lossSum = 0;
                lossN = 0;
            }
            if ((step + 1) % 150 == 0) preview();
            long now = System.currentTimeMillis();
            if (now - lastUi > 1000) {
                double dt = (System.nanoTime() - windowStart) / 1e9;
                speed = (float) (windowTokens / dt);
                windowStart = System.nanoTime();
                windowTokens = 0;
                lastUi = now;
                changed();
            }
            if (now - lastSave > 120_000) {
                save();
                lastSave = now;
                if (now - lastBackup > BACKUP_EVERY_MS) backupNow();
            }
        }
        save();
        backupNow();
        changed();
    }

    private void preview() {
        String q = PREVIEW[new Random().nextInt(PREVIEW.length)];
        String a = reply(new ArrayList<>(), q);
        sampleQ = q;
        sampleA = a;
    }

    // ---------------------------------------------------------------- talking

    /** Answers `text`, using recent turns of `history` ({user, ai} pairs) as context. */
    String reply(List<String[]> history, String text) {
        long brain = h;
        if (brain == 0) return "";
        Words.IntList ids = new Words.IntList();
        Words.encode(vocab, text, null, ids);
        int[] mine = ids.toArray();
        int room = T - 40;
        if (mine.length > room) mine = Arrays.copyOfRange(mine, mine.length - room, mine.length);
        List<int[]> parts = new ArrayList<>();
        parts.add(mine);
        int used = mine.length;
        for (int i = history.size() - 1; i >= 0; i--) {
            Words.IntList ex = new Words.IntList();
            Words.encode(vocab, history.get(i)[0], history.get(i)[1], ex);
            if (used + ex.n > room) break;
            parts.add(0, ex.toArray());
            used += ex.n;
        }
        int[] ctx = new int[used];
        int p = 0;
        for (int[] part : parts) { System.arraycopy(part, 0, ctx, p, part.length); p += part.length; }
        int[] out = NativeAI.generate(brain, ctx, 60, Words.END, vocab.size(), BANNED, 0.6f, 8, System.nanoTime());
        List<String> w = new ArrayList<>();
        for (int id : out) if (id < vocab.size()) w.add(vocab.words.get(id));
        return Words.detokenize(w);
    }

    // ---------------------------------------------------------------- PC backup

    boolean backupSetUp() { return !Prefs.backupUrl(app).isEmpty(); }
    String backupNote() { return backupNote; }
    long lastBackup() { return lastBackup; }

    /** Sends the saved brain to the PC in the background. Skips if one is already on its way. */
    void backupNow() {
        String url = Prefs.backupUrl(app), code = Prefs.backupCode(app);
        if (url.isEmpty() || uploading || !modelFile.exists() || !metaFile.exists()) return;
        uploading = true;
        backupNote = "Backing up…";
        changed();
        new Thread(() -> {
            try {
                Backup.upload(url, code, modelFile, metaFile);
                lastBackup = System.currentTimeMillis();
                backupNote = "";
            } catch (Exception e) {
                backupNote = Backup.explain(e);
            }
            uploading = false;
            changed();
        }, "my-ai-backup").start();
    }

    /** Replaces this phone's brain with the one saved on the PC. Blocks; call off the main thread. */
    void restore(String url, String code) throws Exception {
        stopTraining();
        synchronized (lock) {
            busy = "Restoring from your PC…";
            error = null;
            changed();
            try {
                Backup.download(url, code, modelFile, metaFile);
                freeBrain();
                vocab = null;
                lessons.clear();
                synchronized (lossHist) { lossHist.clear(); }
                loaded = false;
                lastBackup = System.currentTimeMillis();
            } finally {
                busy = null;
            }
        }
        ensureLoaded();
        changed();
    }

    // ---------------------------------------------------------------- saving

    private void save() {
        long brain = h;
        if (brain == 0) return;
        try {
            if (!NativeAI.save(brain, modelFile.getPath())) throw new Exception("disk full?");
            JSONObject j = new JSONObject();
            j.put("name", name).put("size", size.name()).put("tokens", tokens).put("seconds", seconds)
                    .put("sampleQ", sampleQ).put("sampleA", sampleA);
            JSONArray w = new JSONArray();
            for (String s : vocab.words) w.put(s);
            j.put("words", w);
            JSONArray ls = new JSONArray();
            for (String[] l : lessons()) ls.put(new JSONArray().put(l[0]).put(l[1]));
            j.put("lessons", ls);
            JSONArray lh = new JSONArray();
            for (float[] f : lossHistory()) lh.put(f[0]).put(f[1]);
            j.put("loss", lh);
            File tmp = new File(dir, "brain.json.tmp");
            try (FileOutputStream o = new FileOutputStream(tmp)) {
                o.write(j.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(metaFile)) throw new Exception("rename failed");
        } catch (Exception e) {
            error = "Couldn't save progress: " + e.getMessage();
        }
    }
}
