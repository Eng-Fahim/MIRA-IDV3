package com.mira.rfid.manager;

import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;
import android.util.Log;

import com.mira.rfid.R; // ✅ صحيح


/**
 * MIRA RFID Sound Manager
 * 
 * مدير الصوت والصفارات لموديول RFID
 * مسؤول عن إطلاق الصوت فور قراءة التاق أو كشف الإشارة
 */
public class SoundManager {

    private static final String TAG = "MIRASoundManager";
    private static SoundManager instance;
    private SoundPool soundPool;
    private int soundId = 0;
    private boolean isLoaded = false;

    private SoundManager(Context context) {
        try {
            soundPool = new SoundPool.Builder()
                    .setMaxStreams(5)
                    .build();

            soundPool.setOnLoadCompleteListener((sp, sampleId, status) -> {
                if (status == 0) {
                    isLoaded = true;
                }
            });

            // تحميل صوت النغمة الافتراضي من موارد المشروع
            soundId = soundPool.load(context.getApplicationContext(), R.raw.barcodebeep, 1);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing SoundManager: " + e.getMessage());
        }
    }

    public static synchronized SoundManager getInstance(Context context) {
        if (instance == null) {
            instance = new SoundManager(context);
        }
        return instance;
    }

    public void playBeep() {
        if (soundPool != null && isLoaded && soundId != 0) {
            soundPool.play(soundId, 1.0f, 1.0f, 0, 0, 1.0f);
        }
    }

    public void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        instance = null;
    }
}
