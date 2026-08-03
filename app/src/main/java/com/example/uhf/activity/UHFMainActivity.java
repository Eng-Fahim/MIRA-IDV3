package com.example.uhf.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTabHost;

import com.mira.rfid.R;

// 🔒 إدارة الجلسة من وحدة Core
import com.mira.core.SessionManager;

// 📊 شاشات الجرد والـ POS والمجوهرات من وحدة Inventory
import com.mira.inventory.JewelryDashboardFragment;
import com.mira.inventory.MiraInventoryProFragment;
import com.mira.inventory.MiraPosFragment;

import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.ConnectionStatus;

import java.util.ArrayList;
import java.util.HashMap;

public class UHFMainActivity extends BaseTabFragmentActivity {

    private final static String TAG = "MainActivity";
    public FragmentTabHost mTabHost;
    private FragmentManager fm;
    public int selectIndex = -1;
    public ArrayList<UHFTAGInfo> tagList = new ArrayList<UHFTAGInfo>();
    public boolean loopFlag = false;
    private PlaySoundThread playSoundThread = null;
    public Fragment currentFragment;

    // 🔒 مدير الجلسة من وحدة mira-core
    private SessionManager sessionManager;

    // ⭐ POS Mode - مستقبل RFID النشط من وحدة mira-inventory
    private MiraPosFragment activePOSFragment = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 🔒 فحص الأمان المباشر قبل تحميل الواجهة
        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            redirectToLogin();
            return;
        }

        setContentView(R.layout.activity_main);
        checkReadWritePermission();

        String roleTitle = "ADMIN".equalsIgnoreCase(sessionManager.getUserRole()) ? "المدير العام" : "مسؤول الجرد";
        setTitle("MIRA Bridge™ | " + roleTitle);

        initSound();
        initUHF();
        initViewPageData();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(UHFMainActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    public void performLogout() {
        if (sessionManager != null) {
            sessionManager.logoutUser();
            Toast.makeText(this, "تم تسجيل الخروج بنجاح", Toast.LENGTH_SHORT).show();
            redirectToLogin();
        }
    }

    // ============================================
    // 🟢 POS Mode - تسجيل وإلغاء المستقبل النشط
    // ============================================
    public void setActivePOSFragment(MiraPosFragment fragment) {
        this.activePOSFragment = fragment;
        Log.d(TAG, "Active POS Fragment: " + (fragment != null ? "SET" : "CLEARED"));
    }

    // ============================================
    // 🟢 التحقق من حالة القارئ
    // ============================================
    public boolean isReaderReady() {
        try {
            return mReader != null && mReader.getConnectStatus() == ConnectionStatus.CONNECTED;
        } catch (Exception e) {
            return false;
        }
    }

    // ============================================
    // 🟢 استقبال مسح RFID - توجيه ذكي
    // ============================================
    public void onTagRead(String epc) {
        if (activePOSFragment != null && activePOSFragment.isVisible()) {
            activePOSFragment.onRingScanned(epc);
            Log.d(TAG, "RFID → Active POS: " + epc);
            return;
        }

        if (mTabHost != null) {
            String currentTabTag = mTabHost.getCurrentTabTag();
            Fragment fragment = getSupportFragmentManager().findFragmentByTag(currentTabTag);

            if (fragment instanceof MiraPosFragment && fragment.isVisible()) {
                ((MiraPosFragment) fragment).onRingScanned(epc);
                Log.d(TAG, "RFID → POS Tab: " + epc);
                return;
            }
        }

        Log.d(TAG, "RFID scanned but POS not active: " + epc);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (mTabHost != null) {
            String currentTabTag = mTabHost.getCurrentTabTag();
            Fragment fragment = getSupportFragmentManager().findFragmentByTag(currentTabTag);
            if (fragment != null) {
                fragment.onActivityResult(requestCode, resultCode, data);
            }
        }
    }

    // ============================================
    // 🔒 توزيع التبويبات بناءً على الموديولات المتاحة فعلياً
    // ============================================
    protected void initViewPageData() {
        fm = getSupportFragmentManager();
        mTabHost = (FragmentTabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup(this, fm, R.id.realtabcontent);

        String userRole = sessionManager.getUserRole();

        // -------------------------------------------------------------
        // 💎 صلاحيات المدير العام (ADMIN)
        // -------------------------------------------------------------
        if ("ADMIN".equalsIgnoreCase(userRole)) {

            // 💎 Jewelry Dashboard
            mTabHost.addTab(
                mTabHost.newTabSpec("JewelryDashboard").setIndicator("💎"),
                JewelryDashboardFragment.class, null
            );

            // 🛒 MIRA POS Mode
            mTabHost.addTab(
                mTabHost.newTabSpec("POS Mode").setIndicator("🛒"),
                MiraPosFragment.class, null
            );

            // 📊 Inventory Pro
            mTabHost.addTab(
                mTabHost.newTabSpec("Inventory Pro").setIndicator("📊"),
                MiraInventoryProFragment.class, null
            );

        } 
        // -------------------------------------------------------------
        // 📦 صلاحيات الموظف / مسؤول المخزن (INVENTORY)
        // -------------------------------------------------------------
        else {
            mTabHost.addTab(
                mTabHost.newTabSpec("Inventory Pro").setIndicator("📊"),
                MiraInventoryProFragment.class, null
            );
        }
    }

    @Override
    protected void onDestroy() {
        Log.e("zz_pp", "onDestroy()");
        releaseSoundPool();
        if (mReader != null) {
            mReader.free();
        }
        super.onDestroy();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override
    public void exportData() {
        checkReadWritePermission();
        if (loopFlag) {
            Toast.makeText(this, R.string.uhf_msg_scaning, Toast.LENGTH_SHORT).show();
            return;
        }
        if (tagList == null || tagList.isEmpty()) {
            Toast.makeText(this, R.string.uhf_msg_export_data_empty, Toast.LENGTH_SHORT).show();
            return;
        }
    }

    HashMap<Integer, Integer> soundMap = new HashMap<Integer, Integer>();
    private SoundPool soundPool;
    private float volumnRatio;
    private AudioManager am;

    private void initSound() {
        soundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 5);
        soundMap.put(1, soundPool.load(this, R.raw.barcodebeep, 1));
        soundMap.put(2, soundPool.load(this, R.raw.serror, 2));
        am = (AudioManager) this.getSystemService(AUDIO_SERVICE);
        playSoundThread = new PlaySoundThread();
        playSoundThread.start();
    }

    private void releaseSoundPool() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }

    public void playSound(int id) {
        if (soundPool == null) return;
        float audioMaxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float audioCurrentVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        volumnRatio = audioCurrentVolume / audioMaxVolume;
        try {
            soundPool.play(soundMap.get(id), volumnRatio, volumnRatio, 1, 0, 1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkReadWritePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 0);
                finish();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 2);
            }
        }
    }

    private Toast toast;
    public void showToast(String text) {
        if (toast != null) toast.cancel();
        toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        toast.show();
    }
    public void showToast(int resId) { showToast(getString(resId)); }
    public void playSoundDelayed(int speed) { playSoundThread.play(speed); }

    private Object objectLock = new Object();
    private class PlaySoundThread extends Thread {
        private boolean isStop = false;
        int interval = 500;
        long lastPlayTime = SystemClock.elapsedRealtime();
        @Override
        public void run() {
            while (!isStop) {
                long start = 0;
                synchronized (objectLock) {
                    while (!isStop) {
                        if (start == 0) start = SystemClock.elapsedRealtime();
                        else if (SystemClock.elapsedRealtime() - start >= interval) break;
                        else SystemClock.sleep(1);
                    }
                }
                if (SystemClock.elapsedRealtime() - lastPlayTime < 500) playSound(1);
            }
        }
        public void play(int speed) {
            int t = 3;
            if (speed > 85) t = 3;
            else if (speed > 66) t = 100 - speed;
            else if (speed > 33) t = (100 - speed) * 2;
            else t = (100 - speed) * 3;
            interval = t;
            lastPlayTime = SystemClock.elapsedRealtime();
        }
        public void stopPlay() {
            isStop = true;
            synchronized (objectLock) { objectLock.notifyAll(); }
        }
    }
}
