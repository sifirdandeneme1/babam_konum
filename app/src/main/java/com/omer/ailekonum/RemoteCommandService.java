package com.omer.ailekonum;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONObject;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class RemoteCommandService extends Service {
    private static final String CHANNEL_ID = "aile_konum_remote";
    private static final int NOTIFICATION_ID = 201;

    private SharedPreferences prefs;
    private ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private String lastNonce = "";

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        executor = Executors.newSingleThreadExecutor();
        createChannel();
        startForeground(NOTIFICATION_ID, notification("Uzaktan komut bekleniyor"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String mode = prefs.getString("mode", "");
        String secret = prefs.getString("master_secret", "");
        if (!"sender".equals(mode) || secret == null || secret.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (running.compareAndSet(false, true)) {
            executor.execute(() -> listen(secret));
        }
        publishStatusAsync(secret, "");
        return START_STICKY;
    }

    private void listen(String secret) {
        try {
            String topic = CryptoUtil.commandTopic(secret);
            NtfyClient.streamJson(topic, running, message -> handleCommand(secret, message));
        } catch (Exception ignored) {
        }
    }

    private void handleCommand(String secret, String encrypted) {
        try {
            String plain = CryptoUtil.decryptWithSecret(secret, encrypted);
            JSONObject obj = new JSONObject(plain);

            long ts = obj.optLong("ts", 0L);
            String nonce = obj.optString("nonce", "");
            String cmd = obj.optString("cmd", "");

            long age = Math.abs(System.currentTimeMillis() - ts);
            if (ts <= 0L || age > 120_000L) return;
            if (nonce.isEmpty() || nonce.equals(lastNonce)) return;
            lastNonce = nonce;

            String result = "";
            switch (cmd) {
                case "LOCATION_ON":
                    result = SystemLocationController.setEnabled(this, true)
                            ? "Konum açıldı"
                            : "Konum açılamadı";
                    break;

                case "LOCATION_OFF":
                    stopService(new Intent(this, LiveLocationService.class));
                    prefs.edit().putBoolean("tracking", false).apply();
                    result = SystemLocationController.setEnabled(this, false)
                            ? "Konum kapatıldı"
                            : "Konum kapatılamadı";
                    break;

                case "TRACK_START":
                    if (!SystemLocationController.isEnabled(this)) {
                        SystemLocationController.setEnabled(this, true);
                    }
                    if (!SystemLocationController.isEnabled(this)) {
                        result = "Konum hizmetleri açılamadı";
                        break;
                    }
                    Intent live = new Intent(this, LiveLocationService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(live);
                    } else {
                        startService(live);
                    }
                    prefs.edit().putBoolean("tracking", true).apply();
                    result = "Canlı takip başlatıldı";
                    break;

                case "TRACK_STOP":
                    stopService(new Intent(this, LiveLocationService.class));
                    prefs.edit().putBoolean("tracking", false).apply();
                    result = "Canlı takip durduruldu";
                    break;

                case "STATUS":
                    result = "Durum gönderildi";
                    break;

                default:
                    return;
            }

            prefs.edit().putString("last_remote_result", result).apply();
            publishStatusAsync(secret, result);
        } catch (Exception ignored) {
        }
    }

    private void publishStatusAsync(String secret, String result) {
        if (executor == null || executor.isShutdown()) return;
        executor.execute(() -> {
            try {
                JSONObject s = new JSONObject();
                s.put("time", System.currentTimeMillis());
                s.put("locationEnabled", SystemLocationController.isEnabled(this));
                s.put("secureSettings", SystemLocationController.hasSecureSettings(this));
                s.put("tracking", prefs.getBoolean("tracking", false));
                s.put("battery", DeviceUtil.battery(this));
                s.put("result", result == null ? "" : result);
                s.put("nonce", UUID.randomUUID().toString());

                String encrypted = CryptoUtil.encryptWithSecret(secret, s.toString());
                NtfyClient.post(CryptoUtil.statusTopic(secret), encrypted);
            } catch (Exception ignored) {
            }
        });
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "Aile Konum hazır",
                    NotificationManager.IMPORTANCE_MIN
            );
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setDescription("Uzaktan komutları alabilmek için arka planda çalışır.");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification notification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return b.setContentTitle("Aile Konum hazır")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(pi)
                .build();
    }

    @Override
    public void onDestroy() {
        running.set(false);
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
