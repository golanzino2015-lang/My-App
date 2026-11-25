package com.questionbubble.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.PowerManager;

import java.util.Locale;

/** Keeps My AI training with the screen off: a foreground service holding a CPU wake lock. */
public class TrainService extends Service implements LocalAI.Listener {

    private static final String CHANNEL = "my_ai_training";
    private static final int ID = 4711;
    private static final String ACTION_STOP = "stop";

    private PowerManager.WakeLock wake;
    private long lastNote;
    private boolean started, seenTraining;

    static void start(Context c) {
        c.startForegroundService(new Intent(c, TrainService.class));
    }

    static void stop(Context c) {
        c.startService(new Intent(c, TrainService.class).setAction(ACTION_STOP));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannel ch = new NotificationChannel(CHANNEL, "My AI training", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Shows training progress while My AI learns in the background");
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
        wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp:my-ai-training");
        wake.setReferenceCounted(false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LocalAI ai = LocalAI.get(this);
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            new Thread(() -> {
                ai.stopTraining();
                stopSelf();
            }).start();
            if (!started) {
                startForeground(ID, note(ai), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                started = true;
            }
            return START_NOT_STICKY;
        }
        startForeground(ID, note(ai), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        started = true;
        wake.acquire();
        ai.addListener(this);
        new Thread(() -> {
            ai.ensureLoaded();
            ai.startTraining();
            if (!ai.isTraining()) stopSelf();
        }).start();
        return START_NOT_STICKY;
    }

    @Override
    public void onUpdate() {
        LocalAI ai = LocalAI.get(this);
        if (ai.isTraining()) seenTraining = true;
        else if (seenTraining && ai.busy() == null) {
            stopSelf();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastNote < 4000) return;
        lastNote = now;
        getSystemService(NotificationManager.class).notify(ID, note(ai));
    }

    private Notification note(LocalAI ai) {
        String title = ai.exists() ? ai.name() + " is learning" : "My AI";
        String text;
        if (ai.busy() != null) text = ai.busy();
        else if (!ai.isTraining()) text = "Starting…";
        else {
            float loss = ai.lastLoss();
            text = String.format(Locale.US, "%,d words read · %,.0f words/s%s", ai.tokens(), ai.speed(),
                    Float.isNaN(loss) ? "" : String.format(Locale.US, " · %.2f mistakes/word", loss));
        }
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, TrainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, TrainService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_cpu)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "Pause", stop).build())
                .build();
    }

    @Override
    public void onDestroy() {
        LocalAI.get(this).removeListener(this);
        if (wake.isHeld()) wake.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
