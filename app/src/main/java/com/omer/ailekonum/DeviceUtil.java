package com.omer.ailekonum;

import android.content.Context;
import android.os.BatteryManager;

public final class DeviceUtil {
    private DeviceUtil() {}

    public static int battery(Context context) {
        try {
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            return bm == null ? -1 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } catch (Exception e) {
            return -1;
        }
    }
}
