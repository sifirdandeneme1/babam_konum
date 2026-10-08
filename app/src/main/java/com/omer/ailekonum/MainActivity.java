package com.omer.ailekonum;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private static final int REQ_LOCATION = 1001;
    private static final int REQ_NOTIFICATIONS = 1002;

    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newCachedThreadPool();

    private Runnable senderPairRunnable;
    private Runnable senderStatusRunnable;

    private final AtomicBoolean viewerRunning = new AtomicBoolean(false);
    private ExecutorService viewerStreams;

    private TextView viewerInfo;
    private TextView remoteInfo;
    private WebView mapView;
    private boolean mapReady = false;

    private double latestLat;
    private double latestLon;
    private int latestAcc = -1;
    private int latestBattery = -1;
    private long latestTime = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        showCurrentMode();
    }

    private void showCurrentMode() {
        stopTransientWork();
        String mode = prefs.getString("mode", "");
        if ("sender".equals(mode)) {
            showSender();
        } else if ("viewer".equals(mode)) {
            showViewer();
        } else {
            showRoleChoice();
        }
    }

    private LinearLayout baseColumn() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(Color.rgb(248, 249, 250));
        return root;
    }

    private TextView title(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(27);
        v.setTextColor(Color.rgb(25, 35, 45));
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        v.setPadding(0, 0, 0, dp(14));
        return v;
    }

    private TextView body(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(16);
        v.setTextColor(Color.rgb(55, 65, 75));
        v.setLineSpacing(0, 1.12f);
        v.setPadding(0, dp(5), 0, dp(10));
        return v;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setMinHeight(dp(50));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, dp(5), 0, dp(5));
        b.setLayoutParams(lp);
        return b;
    }

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setMinHeight(dp(48));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setWeightSum(2f);
        return r;
    }

    private void showRoleChoice() {
        LinearLayout root = baseColumn();
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title("Aile Konum"));

        root.addView(body(
                "Aynı APK iki telefona kurulur.\n\n" +
                "Babanın telefonunda “Konum Gönderen”, kendi telefonunda “Takip Eden” seç."
        ));

        Button sender = button("📍 Konum Gönderen • Babanın telefonu");
        sender.setOnClickListener(v -> {
            String secret = prefs.getString("master_secret", "");
            if (secret == null || secret.isEmpty()) secret = CryptoUtil.randomSecret();
            String pin = CryptoUtil.randomPin();
            prefs.edit()
                    .putString("mode", "sender")
                    .putString("master_secret", secret)
                    .putString("pair_pin", pin)
                    .putLong("pair_pin_created", System.currentTimeMillis())
                    .putBoolean("remote_agent_enabled", true)
                    .apply();
            showCurrentMode();
        });
        root.addView(sender);

        Button viewer = button("🗺️ Takip Eden • Benim telefonum");
        viewer.setOnClickListener(v -> {
            prefs.edit().putString("mode", "viewer").apply();
            showCurrentMode();
        });
        root.addView(viewer);

        TextView note = body(
                "\nTakip aktifken Android sessiz bir konum bildirimi gösterir. " +
                "Uzaktan komutları alabilmek için babanın telefonunda düşük öncelikli “Aile Konum hazır” bildirimi kalır."
        );
        note.setTextSize(13);
        root.addView(note);

        setContentView(root);
    }

    private void showSender() {
        ensureSenderIdentity();
        requestBasicPermissionsIfNeeded();
        startRemoteAgent();

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = baseColumn();
        scroll.addView(root);

        root.addView(title("Babanın Telefonu"));
        root.addView(body("Kendi telefonunda eşleştirme ekranına yalnızca bu 4 haneli kodu gir:"));

        TextView pinView = new TextView(this);
        pinView.setText(prefs.getString("pair_pin", "----"));
        pinView.setTextSize(42);
        pinView.setTextColor(Color.BLACK);
        pinView.setGravity(Gravity.CENTER);
        pinView.setPadding(0, dp(12), 0, dp(12));
        root.addView(pinView);

        Button newPin = button("Yeni 4 haneli kod oluştur");
        newPin.setOnClickListener(v -> {
            String pin = CryptoUtil.randomPin();
            prefs.edit()
                    .putString("pair_pin", pin)
                    .putLong("pair_pin_created", System.currentTimeMillis())
                    .apply();
            pinView.setText(pin);
            publishPairingOffer();
        });
        root.addView(newPin);

        TextView pairNote = body(
                "Bu PIN yalnızca ilk eşleştirme içindir ve kısa süreli kullanılır. " +
                "Eşleşince iki telefon arka planda uzun, rastgele bir anahtarla haberleşir."
        );
        pairNote.setTextSize(13);
        root.addView(pairNote);

        TextView status = body("");
        status.setTextSize(15);
        root.addView(status);

        Button permissions = button("Konum izinlerini kontrol et");
        permissions.setOnClickListener(v -> requestLocationPermissions());
        root.addView(permissions);

        Button battery = button("Pil kısıtlamasını kaldır");
        battery.setOnClickListener(v -> requestBatteryExemption());
        root.addView(battery);

        Button appSettings = button("Uygulama ayarlarını aç");
        appSettings.setOnClickListener(v -> openAppSettings());
        root.addView(appSettings);

        Button restartAgent = button("Uzaktan kontrol ajanını yeniden başlat");
        restartAgent.setOnClickListener(v -> {
            stopService(new Intent(this, RemoteCommandService.class));
            startRemoteAgent();
            toast("Ajan yeniden başlatıldı.");
        });
        root.addView(restartAgent);

        Button reset = button("Rolü ve eşleştirmeyi sıfırla");
        reset.setOnClickListener(v -> confirmReset());
        root.addView(reset);

        TextView adb = body(
                "ADB özel yetkisi bir kez verilecek:\n" +
                "adb shell pm grant com.omer.ailekonum android.permission.WRITE_SECURE_SETTINGS"
        );
        adb.setTextSize(13);
        adb.setTextIsSelectable(true);
        root.addView(adb);

        senderStatusRunnable = new Runnable() {
            @Override
            public void run() {
                boolean secure = SystemLocationController.hasSecureSettings(MainActivity.this);
                boolean loc = SystemLocationController.isEnabled(MainActivity.this);
                boolean tracking = prefs.getBoolean("tracking", false);
                String error = prefs.getString("tracking_error", "");

                StringBuilder s = new StringBuilder();
                s.append(secure ? "✅ ADB özel yetkisi hazır" : "❌ ADB özel yetkisi henüz verilmedi");
                s.append("\n").append(loc ? "📍 Telefon Konumu: AÇIK" : "📍 Telefon Konumu: KAPALI");
                s.append("\n").append(tracking ? "🟢 Canlı takip: AÇIK" : "⚪ Canlı takip: KAPALI");
                s.append("\n🔌 Uzaktan kontrol ajanı: etkin");
                if (error != null && !error.isEmpty()) s.append("\n⚠ ").append(error);
                status.setText(s.toString());

                handler.postDelayed(this, 1500L);
            }
        };
        handler.post(senderStatusRunnable);

        senderPairRunnable = new Runnable() {
            @Override
            public void run() {
                publishPairingOffer();
                handler.postDelayed(this, 30_000L);
            }
        };
        handler.post(senderPairRunnable);

        setContentView(scroll);
    }

    private void ensureSenderIdentity() {
        String secret = prefs.getString("master_secret", "");
        String pin = prefs.getString("pair_pin", "");
        if (secret == null || secret.isEmpty()) {
            prefs.edit().putString("master_secret", CryptoUtil.randomSecret()).apply();
        }
        if (pin == null || pin.length() != 4) {
            prefs.edit()
                    .putString("pair_pin", CryptoUtil.randomPin())
                    .putLong("pair_pin_created", System.currentTimeMillis())
                    .apply();
        }
    }

    private void publishPairingOffer() {
        String pin = prefs.getString("pair_pin", "");
        String secret = prefs.getString("master_secret", "");
        if (pin == null || pin.length() != 4 || secret == null || secret.isEmpty()) return;

        long bucket = CryptoUtil.pairingBucket();
        io.execute(() -> {
            try {
                JSONObject obj = new JSONObject();
                obj.put("secret", secret);
                obj.put("created", System.currentTimeMillis());
                obj.put("expires", System.currentTimeMillis() + 10L * 60L * 1000L);
                obj.put("version", 3);

                String encrypted = CryptoUtil.encryptWithPin(pin, bucket, obj.toString());
                NtfyClient.post(CryptoUtil.pairingTopic(pin, bucket), encrypted);
            } catch (Exception ignored) {
            }
        });
    }

    private void showViewer() {
        String secret = prefs.getString("master_secret", "");
        if (secret == null || secret.isEmpty()) {
            showPairingEntry();
            return;
        }

        LinearLayout root = baseColumn();
        root.addView(title("Babam Nerede?"));

        remoteInfo = body("Babamın telefonu ile bağlantı kuruluyor…");
        remoteInfo.setTextSize(14);
        root.addView(remoteInfo);

        LinearLayout r1 = row();
        Button locOn = smallButton("📍 Konumu Aç");
        Button locOff = smallButton("📍 Konumu Kapat");
        locOn.setOnClickListener(v -> sendCommand(secret, "LOCATION_ON"));
        locOff.setOnClickListener(v -> sendCommand(secret, "LOCATION_OFF"));
        r1.addView(locOn);
        r1.addView(locOff);
        root.addView(r1);

        LinearLayout r2 = row();
        Button start = smallButton("▶ Canlı Takibi Başlat");
        Button stop = smallButton("■ Takibi Durdur");
        start.setOnClickListener(v -> sendCommand(secret, "TRACK_START"));
        stop.setOnClickListener(v -> sendCommand(secret, "TRACK_STOP"));
        r2.addView(start);
        r2.addView(stop);
        root.addView(r2);

        viewerInfo = body("Canlı konum bekleniyor…");
        viewerInfo.setTextSize(14);
        root.addView(viewerInfo);

        mapView = new WebView(this);
        mapView.getSettings().setJavaScriptEnabled(true);
        mapView.getSettings().setDomStorageEnabled(true);
        mapView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                mapReady = true;
                if (latestTime > 0) updateMap();
            }
        });
        LinearLayout.LayoutParams mapLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        mapLp.setMargins(0, dp(5), 0, dp(6));
        mapView.setLayoutParams(mapLp);
        root.addView(mapView);

        LinearLayout r3 = row();
        Button status = smallButton("↻ Durumu Yenile");
        Button reset = smallButton("Eşleştirmeyi Sıfırla");
        status.setOnClickListener(v -> sendCommand(secret, "STATUS"));
        reset.setOnClickListener(v -> confirmReset());
        r3.addView(status);
        r3.addView(reset);
        root.addView(r3);

        setContentView(root);
        loadMap();
        startViewerStreams(secret);
        sendCommand(secret, "STATUS");
        loadLatestLocation(secret);
    }

    private void showPairingEntry() {
        LinearLayout root = baseColumn();
        root.addView(title("Takip Eden Telefon"));
        root.addView(body("Babanın telefonunda görünen 4 haneli eşleştirme kodunu gir:"));

        EditText pin = new EditText(this);
        pin.setHint("0000");
        pin.setTextSize(28);
        pin.setGravity(Gravity.CENTER);
        pin.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(pin);

        TextView state = body("");
        state.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(state);

        Button pair = button("Eşleştir");
        pair.setOnClickListener(v -> {
            String p = CryptoUtil.normalizePin(pin.getText().toString());
            if (p.length() != 4) {
                toast("4 haneli kodu gir.");
                return;
            }
            pair.setEnabled(false);
            state.setText("Eşleştirme aranıyor…");
            io.execute(() -> {
                String found = findPairingSecret(p);
                runOnUiThread(() -> {
                    pair.setEnabled(true);
                    if (found == null || found.isEmpty()) {
                        state.setText("Kod bulunamadı. Babanın telefonunda Aile Konum ekranı açık olsun ve tekrar dene.");
                    } else {
                        prefs.edit().putString("master_secret", found).apply();
                        toast("Eşleşme tamamlandı.");
                        showCurrentMode();
                    }
                });
            });
        });
        root.addView(pair);

        Button reset = button("Rol seçimine dön");
        reset.setOnClickListener(v -> confirmReset());
        root.addView(reset);

        setContentView(root);
    }

    private String findPairingSecret(String pin) {
        long now = System.currentTimeMillis();
        long current = CryptoUtil.pairingBucket();

        for (long bucket : new long[]{current, current - 1}) {
            try {
                String topic = CryptoUtil.pairingTopic(pin, bucket);
                List<String> messages = NtfyClient.pollRaw(topic, "10m");

                for (int i = messages.size() - 1; i >= 0; i--) {
                    try {
                        String plain = CryptoUtil.decryptWithPin(pin, bucket, messages.get(i));
                        JSONObject obj = new JSONObject(plain);
                        long created = obj.optLong("created", 0L);
                        long expires = obj.optLong("expires", 0L);
                        String secret = obj.optString("secret", "");
                        if (created > 0L && expires >= now && now - created <= 10L * 60L * 1000L
                                && !secret.isEmpty()) {
                            return secret;
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private void sendCommand(String secret, String cmd) {
        if (remoteInfo != null) remoteInfo.setText("Komut gönderiliyor: " + commandLabel(cmd));
        io.execute(() -> {
            try {
                JSONObject obj = new JSONObject();
                obj.put("cmd", cmd);
                obj.put("ts", System.currentTimeMillis());
                obj.put("nonce", UUID.randomUUID().toString());

                String encrypted = CryptoUtil.encryptWithSecret(secret, obj.toString());
                NtfyClient.post(CryptoUtil.commandTopic(secret), encrypted);

                runOnUiThread(() -> {
                    if (remoteInfo != null) remoteInfo.setText("Komut gönderildi • babanın telefonundan yanıt bekleniyor…");
                });
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                runOnUiThread(() -> {
                    if (remoteInfo != null) remoteInfo.setText("⚠ Komut gönderilemedi: " + msg);
                });
            }
        });
    }

    private String commandLabel(String cmd) {
        switch (cmd) {
            case "LOCATION_ON": return "Konumu Aç";
            case "LOCATION_OFF": return "Konumu Kapat";
            case "TRACK_START": return "Canlı Takibi Başlat";
            case "TRACK_STOP": return "Takibi Durdur";
            default: return "Durumu Yenile";
        }
    }

    private void startViewerStreams(String secret) {
        stopViewerStreams();
        viewerRunning.set(true);
        viewerStreams = Executors.newFixedThreadPool(2);

        viewerStreams.execute(() -> {
            try {
                String topic = CryptoUtil.locationTopic(secret);
                NtfyClient.streamJson(topic, viewerRunning, message -> handleLocation(secret, message));
            } catch (Exception ignored) {
            }
        });

        viewerStreams.execute(() -> {
            try {
                String topic = CryptoUtil.statusTopic(secret);
                NtfyClient.streamJson(topic, viewerRunning, message -> handleStatus(secret, message));
            } catch (Exception ignored) {
            }
        });
    }

    private void handleLocation(String secret, String message) {
        try {
            JSONObject obj = new JSONObject(CryptoUtil.decryptWithSecret(secret, message));
            long t = obj.optLong("time", 0L);
            if (t <= 0L || t < latestTime) return;

            latestLat = obj.getDouble("lat");
            latestLon = obj.getDouble("lon");
            latestAcc = obj.optInt("acc", -1);
            latestBattery = obj.optInt("battery", -1);
            latestTime = t;

            runOnUiThread(() -> {
                updateViewerInfo();
                updateMap();
            });
        } catch (Exception ignored) {
        }
    }

    private void handleStatus(String secret, String message) {
        try {
            JSONObject obj = new JSONObject(CryptoUtil.decryptWithSecret(secret, message));
            boolean location = obj.optBoolean("locationEnabled", false);
            boolean secure = obj.optBoolean("secureSettings", false);
            boolean tracking = obj.optBoolean("tracking", false);
            int battery = obj.optInt("battery", -1);
            String result = obj.optString("result", "");

            runOnUiThread(() -> {
                if (remoteInfo == null) return;
                StringBuilder s = new StringBuilder();
                if (!result.isEmpty()) s.append(result).append("\n");
                s.append(location ? "📍 Konum AÇIK" : "📍 Konum KAPALI");
                s.append(" • ").append(tracking ? "🟢 Canlı takip AÇIK" : "⚪ Takip kapalı");
                if (battery >= 0) s.append("\nPil %").append(battery);
                s.append(" • ADB yetkisi ").append(secure ? "✅" : "❌");
                remoteInfo.setText(s.toString());
            });
        } catch (Exception ignored) {
        }
    }

    private void loadLatestLocation(String secret) {
        io.execute(() -> {
            try {
                List<String> messages = NtfyClient.pollRaw(CryptoUtil.locationTopic(secret), "latest");
                for (String message : messages) handleLocation(secret, message);
            } catch (Exception ignored) {
            }
        });
    }

    private void updateViewerInfo() {
        if (viewerInfo == null || latestTime <= 0L) return;
        long age = Math.max(0L, System.currentTimeMillis() - latestTime);
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(latestTime));

        StringBuilder s = new StringBuilder();
        if (age < 15_000L) s.append("🟢 CANLI • ");
        else if (age > 120_000L) s.append("⚠ KONUM ESKİ • ");
        else s.append("🟡 ");

        s.append("Son konum ").append(time);
        if (latestAcc >= 0) s.append(" • ±").append(latestAcc).append(" m");
        if (latestBattery >= 0) s.append(" • Pil %").append(latestBattery);
        viewerInfo.setText(s.toString());
    }

    private void loadMap() {
        mapReady = false;
        String html = "<!DOCTYPE html><html><head>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no'>" +
                "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>" +
                "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>" +
                "<style>html,body,#map{height:100%;margin:0;padding:0;background:#eee}</style></head><body>" +
                "<div id='map'></div><script>" +
                "var map=L.map('map',{zoomControl:true}).setView([39.0,35.0],6);" +
                "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);" +
                "var marker=null,circle=null,anim=null,first=true;" +
                "function updateLocation(lat,lon,acc){" +
                "var target=L.latLng(lat,lon);" +
                "if(!marker){marker=L.marker(target).addTo(map).bindPopup('Babam');map.setView(target,18);marker.openPopup();first=false;}" +
                "else{" +
                "var start=marker.getLatLng();var t0=performance.now();if(anim)cancelAnimationFrame(anim);" +
                "function step(t){var q=Math.min(1,(t-t0)/2400);var e=1-Math.pow(1-q,3);" +
                "var p=L.latLng(start.lat+(target.lat-start.lat)*e,start.lng+(target.lng-start.lng)*e);" +
                "marker.setLatLng(p);if(q<1){anim=requestAnimationFrame(step);}else{map.panTo(target,{animate:true,duration:0.5});}}" +
                "anim=requestAnimationFrame(step);" +
                "}" +
                "if(circle){map.removeLayer(circle);}circle=L.circle(target,{radius:Math.max(acc,5),weight:1,fillOpacity:0.08}).addTo(map);" +
                "}" +
                "</script></body></html>";

        mapView.loadDataWithBaseURL(
                "https://local.ailekonum/",
                html,
                "text/html",
                StandardCharsets.UTF_8.name(),
                null
        );
    }

    private void updateMap() {
        if (!mapReady || mapView == null || latestTime <= 0L) return;
        int acc = latestAcc > 0 ? latestAcc : 20;
        String js = String.format(
                Locale.US,
                "updateLocation(%.8f,%.8f,%d);",
                latestLat,
                latestLon,
                acc
        );
        mapView.evaluateJavascript(js, null);
    }

    private void startRemoteAgent() {
        prefs.edit().putBoolean("remote_agent_enabled", true).apply();
        Intent service = new Intent(this, RemoteCommandService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(service);
            } else {
                startService(service);
            }
        } catch (Exception e) {
            prefs.edit().putString("tracking_error", "Ajan başlatılamadı: " + e.getClass().getSimpleName()).apply();
        }
    }

    private void requestBasicPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    private void requestLocationPermissions() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQ_LOCATION
            );
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this)
                    .setTitle("Arka planda konum")
                    .setMessage("Uygulama izinlerinden Konum → “Her zaman izin ver” seçeneğini aç.")
                    .setPositiveButton("Ayarları aç", (d, w) -> openAppSettings())
                    .setNegativeButton("Sonra", null)
                    .show();
        } else {
            toast("Konum izni hazır.");
        }
    }

    private void requestBatteryExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
                toast("Pil optimizasyonu zaten kapalı.");
                return;
            }
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            openAppSettings();
        }
    }

    private void openAppSettings() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("Sıfırlansın mı?")
                .setMessage("Bu telefondaki rol ve eşleştirme bilgileri silinecek.")
                .setPositiveButton("Sıfırla", (d, w) -> {
                    stopService(new Intent(this, RemoteCommandService.class));
                    stopService(new Intent(this, LiveLocationService.class));
                    prefs.edit().clear().apply();
                    showCurrentMode();
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void stopTransientWork() {
        if (senderPairRunnable != null) handler.removeCallbacks(senderPairRunnable);
        if (senderStatusRunnable != null) handler.removeCallbacks(senderStatusRunnable);
        senderPairRunnable = null;
        senderStatusRunnable = null;
        stopViewerStreams();
    }

    private void stopViewerStreams() {
        viewerRunning.set(false);
        if (viewerStreams != null) {
            viewerStreams.shutdownNow();
            viewerStreams = null;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                requestLocationPermissions();
            } else {
                toast("Konum izni gerekli.");
            }
        }
    }

    @Override
    protected void onDestroy() {
        stopTransientWork();
        io.shutdownNow();
        if (mapView != null) mapView.destroy();
        super.onDestroy();
    }
}
