package com.omer.ailekonum;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NtfyClient {
    public interface MessageHandler {
        void onMessage(String message);
    }

    private NtfyClient() {}

    public static void post(String topic, String message) throws Exception {
        URL url = new URL("https://ntfy.sh/" + topic);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
        c.setRequestProperty("User-Agent", "AileKonum/3.0");
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(body.length);
        try (OutputStream out = c.getOutputStream()) {
            out.write(body);
        }
        int code = c.getResponseCode();
        c.disconnect();
        if (code < 200 || code >= 300) throw new IllegalStateException("ntfy HTTP " + code);
    }

    public static List<String> pollRaw(String topic, String since) throws Exception {
        String suffix = since == null || since.isEmpty() ? "latest" : since;
        URL url = new URL("https://ntfy.sh/" + topic + "/raw?poll=1&since=" + suffix);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(12000);
        c.setRequestProperty("User-Agent", "AileKonum/3.0");
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            c.disconnect();
            throw new IllegalStateException("ntfy HTTP " + code);
        }
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) out.add(line);
            }
        }
        c.disconnect();
        return out;
    }

    public static void streamJson(String topic, AtomicBoolean running, MessageHandler handler) {
        while (running.get()) {
            HttpURLConnection c = null;
            try {
                URL url = new URL("https://ntfy.sh/" + topic + "/json");
                c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(12000);
                c.setReadTimeout(75000);
                c.setRequestProperty("User-Agent", "AileKonum/3.0");
                int code = c.getResponseCode();
                if (code < 200 || code >= 300) throw new IllegalStateException("ntfy HTTP " + code);

                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (running.get() && (line = r.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty()) continue;
                        try {
                            JSONObject obj = new JSONObject(line);
                            if (!"message".equals(obj.optString("event"))) continue;
                            String message = obj.optString("message", "");
                            if (!message.isEmpty()) handler.onMessage(message);
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Exception ignored) {
                try {
                    Thread.sleep(2500L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } finally {
                if (c != null) c.disconnect();
            }
        }
    }
}
