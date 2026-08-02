package com.mira.rfid.manager;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * MIRA RFID Settings Manager
 * 
 * إدارة وتخزين إعدادات القارئ (طاقة البث، الترددات، وضع القراءة)
 */
public class MiraSettingsManager {

    private static final String PREF_NAME = "mira_rfid_settings";
    private static final String KEY_POWER = "rfid_power";
    private static final String KEY_WORK_MODE = "rfid_work_mode";

    private final SharedPreferences prefs;

    public MiraSettingsManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void savePower(int power) {
        prefs.edit().putInt(KEY_POWER, power).apply();
    }

    public int getPower(int defaultPower) {
        return prefs.getInt(KEY_POWER, defaultPower);
    }

    public void saveWorkMode(int mode) {
        prefs.edit().putInt(KEY_WORK_MODE, mode).apply();
    }

    public int getWorkMode(int defaultMode) {
        return prefs.getInt(KEY_WORK_MODE, defaultMode);
    }
}
