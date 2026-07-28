package com.example.uhf.manager;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;

public class MiraSettingsManager {
    
    private static MiraSettingsManager instance;
    private SharedPreferences prefs;
    private Map<String, SettingsChangeListener> listeners = new HashMap<>();
    
    // 🟢 واجهة للاستماع للتغييرات
    public interface SettingsChangeListener {
        void onSettingChanged(String key, Object value);
    }
    
    private MiraSettingsManager(Context context) {
        prefs = context.getSharedPreferences("MIRA_BRIDGE_SETTINGS", Context.MODE_PRIVATE);
    }
    
    public static MiraSettingsManager getInstance(Context context) {
        if (instance == null) {
            instance = new MiraSettingsManager(context.getApplicationContext());
        }
        return instance;
    }
    
    // 🟢 حفظ إعداد
    public void saveSetting(String key, Object value) {
        SharedPreferences.Editor editor = prefs.edit();
        if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        }
        editor.apply();
        
        // إشعار جميع المستمعين
        notifyListeners(key, value);
    }
    
    // 🟢 جلب إعداد
    public String getString(String key, String defaultValue) {
        return prefs.getString(key, defaultValue);
    }
    
    public boolean getBoolean(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
    }
    
    public int getInt(String key, int defaultValue) {
        return prefs.getInt(key, defaultValue);
    }
    
    // 🟢 تسجيل مستمع
    public void registerListener(String tag, SettingsChangeListener listener) {
        listeners.put(tag, listener);
    }
    
    public void unregisterListener(String tag) {
        listeners.remove(tag);
    }
    
    // 🟢 إشعار جميع المستمعين
    private void notifyListeners(String key, Object value) {
        for (SettingsChangeListener listener : listeners.values()) {
            listener.onSettingChanged(key, value);
        }
    }
}
