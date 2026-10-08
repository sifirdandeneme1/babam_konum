package com.omer.ailekonum;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class LiveLocationService extends Service implements LocationListener {
    private static final String CHANNEL_ID = "aile_konum_live";
    private static final int NOTIFICATION_ID = 301;

    private SharedPreferences prefs;
    private LocationManager locationManager;
    private ExecutorService networkExecutor;
    private final AtomicBoolean sending = new AtomicBoolean(false);

    private Location lastSent;
    private long lastSendAt = 0L;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        networkExecutor = Executors.newSingleThreadExecutor();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        createChannel();
        startForeground(NOTIFICATION_ID, notification("Canlı konum hazırlanıyor…"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        prefs.edit().putBoolean("tracking", true).apply();
        startUpdates();
        return START_STICKY;
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void startUpdates() {
        if (!hasLocationPermission()) {
            prefs.edit().putString("tracking_error", "Konum izni verilmemiş").apply();
            stopSelf();
            return;
        }

        try {
            locationManager.removeUpdates(this);
            boolean any = false;

            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        3000L,
                        1f,
                        this
                );
                any = true;
            }

            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        5000L,
                        3f,
                        this
                );
                any = true;
            }

            if (!any) {
                prefs.edit().putString("tracking_error", "Konum hizmetleri kapalı").apply();
                updateNotification("Konum hizmetleri kapalı");
            } else {
                prefs.edit().putString("tracking_error", "").apply();
                updateNotification("Canlı takip açık");
            }
        } catch (Exception e) {
            prefs.edit().putString("tracking_error", "GPS başlatılamadı: " + safe(e)).apply();
            updateNotification("GPS başlatılamadı");
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null) return;

        long now = System.currentTimeMillis();
        if (location.getTime() > 0L && now - location.getTime() > 30_000L) return;

        long elapsed = now - lastSendAt;
        float distance = lastSent == null ? Float.MAX_VALUE : lastSent.distanceTo(location);

        if (lastSent == null || (elapsed >= 3000L && distance >= 1f) || elapsed >= 5000L) {
            publish(location);
        }
    }

    private void publish(Location location) {
        if (!sending.compareAndSet(false, true)) return;

        Location copy = new Location(location);
        networkExecutor.execute(() -> {
            try {
                String secret = prefs.getString("master_secret", "");
                if (secret == null || secret.isEmpty()) throw new IllegalStateException("Eşleştirme anahtarı yok");

                JSONObject obj = new JSONObject();
                obj.put("lat", copy.getLatitude());
                obj.put("lon", copy.getLongitude());
                obj.put("acc", copy.hasAccuracy() ? Math.round(copy.getAccuracy()) : -1);
                obj.put("speed", copy.hasSpeed() ? copy.getSpeed() : -1);
                obj.put("bearing", copy.hasBearing() ? copy.getBearing() : -1);
                obj.put("provider", copy.getProvider() == null ? "" : copy.getProvider());
                obj.put("time", copy.getTime() > 0L ? copy.getTime() : System.currentTimeMillis());
                obj.put("sent", System.currentTimeMillis());
                obj.put("battery", DeviceUtil.battery(this));

                String encrypted = CryptoUtil.encryptWithSecret(secret, obj.toString());
                NtfyClient.post(CryptoUtil.locationTopic(secret), encrypted);

                lastSent = copy;
                lastSendAt = System.currentTimeMillis();
                prefs.edit()
                        .putLong("last_location_sent", lastSendAt)
                        .putInt("last_accuracy", copy.hasAccuracy() ? Math.round(copy.getAccuracy()) : -1)
                        .putString("tracking_error", "")
                        .apply();

                updateNotification("Canlı • ±" +
                        (copy.hasAccuracy() ? Math.round(copy.getAccuracy()) : "?") + " m");
            } catch (Exception e) {
                prefs.edit().putString("tracking_error", "Gönderim: " + safe(e)).apply();
            } finally {
                sending.set(false);
            }
        });
    }

    private String safe(Exception e) {
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "Canlı konum",
                    NotificationManager.IMPORTANCE_LOW
            );
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setDescription("Canlı konum paylaşımı açıkken gösterilir.");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification notification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return b.setContentTitle("Aile Konum • Canlı takip")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(pi)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, notification(text));
    }

    @Override
    public void onProviderEnabled(String provider) {
        startUpdates();
    }

    @Override
    public void onProviderDisabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            updateNotification("GPS kapalı");
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onDestroy() {
        prefs.edit().putBoolean("tracking", false).apply();
        try {
            if (locationManager != null) locationManager.removeUpdates(this);
        } catch (Exception ignored) {
        }
        if (networkExecutor != null) networkExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
