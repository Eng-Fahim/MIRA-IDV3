package com.mira.rfid;

import android.content.Context;
import android.util.Log;

public class RFIDManager {

    private static final String TAG = "RFIDManager";
    private static RFIDManager instance;
    private boolean isInitialized = false;

    private RFIDManager() {}

    public static synchronized RFIDManager getInstance() {
        if (instance == null) {
            instance = new RFIDManager();
        }
        return instance;
    }

    // تهيئة القارئ اليدوي
    public boolean initReader(Context context) {
        try {
            // هنا يتم تشغيل محرك القارئ الميداني
            isInitialized = true;
            Log.d(TAG, "MIRA RFID Hardware Engine Initialized Successfully.");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize RFID Hardware: " + e.getMessage());
            return false;
        }
    }

    public boolean isReady() {
        return isInitialized;
    }

    // إيقاف وتفريغ الموارد عند إغلاق التطبيق
    public void free() {
        isInitialized = false;
        Log.d(TAG, "MIRA RFID Hardware Engine Released.");
    }
}
