package com.omer.ailekonum;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.provider.Settings;

public final class SystemLocationController {
    private SystemLocationController() {}

    public static boolean hasSecureSettings(Context context) {
        return context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isEnabled(Context context) {
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return lm != null && lm.isLocationEnabled();
            }
            return Settings.Secure.getInt(
                    context.getContentResolver(),
                    Settings.Secure.LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_OFF
            ) != Settings.Secure.LOCATION_MODE_OFF;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean setEnabled(Context context, boolean enabled) {
        if (!hasSecureSettings(context)) return false;

        try {
            Settings.Secure.putInt(
                    context.getContentResolver(),
                    Settings.Secure.LOCATION_MODE,
                    enabled ? Settings.Secure.LOCATION_MODE_HIGH_ACCURACY : Settings.Secure.LOCATION_MODE_OFF
            );
        } catch (Exception ignored) {
        }

        if (isEnabled(context) != enabled) {
            try {
                Settings.Secure.setLocationProviderEnabled(
                        context.getContentResolver(),
                        LocationManager.GPS_PROVIDER,
                        enabled
                );
            } catch (Exception ignored) {
            }

            try {
                Settings.Secure.setLocationProviderEnabled(
                        context.getContentResolver(),
                        LocationManager.NETWORK_PROVIDER,
                        enabled
                );
            } catch (Exception ignored) {
            }
        }

        try {
            Thread.sleep(500L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return isEnabled(context) == enabled;
    }
}
