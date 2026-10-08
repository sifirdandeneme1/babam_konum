package com.omer.ailekonum;

import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class CryptoUtil {
    private static final SecureRandom RANDOM = new SecureRandom();

    private CryptoUtil() {}

    public static String randomSecret() {
        byte[] data = new byte[32];
        RANDOM.nextBytes(data);
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    public static String randomPin() {
        return String.format(Locale.US, "%04d", RANDOM.nextInt(10000));
    }

    public static String normalizePin(String pin) {
        if (pin == null) return "";
        return pin.replaceAll("[^0-9]", "");
    }

    public static long pairingBucket() {
        return System.currentTimeMillis() / (5L * 60L * 1000L);
    }

    public static String commandTopic(String secret) throws Exception {
        return "ailekonum-cmd-" + hashHex("cmd|" + secret).substring(0, 32);
    }

    public static String locationTopic(String secret) throws Exception {
        return "ailekonum-loc-" + hashHex("loc|" + secret).substring(0, 32);
    }

    public static String statusTopic(String secret) throws Exception {
        return "ailekonum-status-" + hashHex("status|" + secret).substring(0, 32);
    }

    public static String pairingTopic(String pin, long bucket) throws Exception {
        return "ailekonum-pair-" + hashHex("pair|" + normalizePin(pin) + "|" + bucket).substring(0, 28);
    }

    public static String encryptWithSecret(String secret, String plain) throws Exception {
        byte[] key = sha256(("secret|" + secret).getBytes(StandardCharsets.UTF_8));
        return encrypt(key, plain);
    }

    public static String decryptWithSecret(String secret, String encoded) throws Exception {
        byte[] key = sha256(("secret|" + secret).getBytes(StandardCharsets.UTF_8));
        return decrypt(key, encoded);
    }

    public static String encryptWithPin(String pin, long bucket, String plain) throws Exception {
        return encrypt(pinKey(pin, bucket), plain);
    }

    public static String decryptWithPin(String pin, long bucket, String encoded) throws Exception {
        return decrypt(pinKey(pin, bucket), encoded);
    }

    private static byte[] pinKey(String pin, long bucket) throws Exception {
        String normalized = normalizePin(pin);
        PBEKeySpec spec = new PBEKeySpec(
                normalized.toCharArray(),
                ("AileKonum|" + bucket).getBytes(StandardCharsets.UTF_8),
                120_000,
                256
        );
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .getEncoded();
    }

    private static String encrypt(byte[] key, String plain) throws Exception {
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        ByteBuffer out = ByteBuffer.allocate(iv.length + encrypted.length);
        out.put(iv);
        out.put(encrypted);
        return Base64.encodeToString(out.array(), Base64.NO_WRAP);
    }

    private static String decrypt(byte[] key, String encoded) throws Exception {
        byte[] all = Base64.decode(encoded.trim(), Base64.DEFAULT);
        if (all.length < 13) throw new IllegalArgumentException("Geçersiz şifreli veri");
        byte[] iv = new byte[12];
        byte[] encrypted = new byte[all.length - 12];
        System.arraycopy(all, 0, iv, 0, 12);
        System.arraycopy(all, 12, encrypted, 0, encrypted.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private static String hashHex(String text) throws Exception {
        byte[] digest = sha256(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        for (byte b : digest) out.append(String.format(Locale.US, "%02x", b & 0xff));
        return out.toString();
    }

    private static byte[] sha256(byte[] input) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(input);
    }
}
