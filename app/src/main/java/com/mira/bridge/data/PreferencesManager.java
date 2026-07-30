package com.mira.bridge.data;

import android.content.Context;
import android.content.SharedPreferences;
import com.mira.bridge.utils.Constants;

public class PreferencesManager {
    private SharedPreferences prefs;
    
    public PreferencesManager(Context context) {
        prefs = context.getSharedPreferences("mira_bridge_prefs", Context.MODE_PRIVATE);
    }
    
    public String getString(String key, String def) { return prefs.getString(key, def); }
    public boolean getBoolean(String key, boolean def) { return prefs.getBoolean(key, def); }
    public int getInt(String key, int def) { return prefs.getInt(key, def); }
    public void putString(String key, String value) { prefs.edit().putString(key, value).apply(); }
    public void putBoolean(String key, boolean value) { prefs.edit().putBoolean(key, value).apply(); }
    
    public String getApiUrl() { return getString(Constants.PREF_API_URL, Constants.API_BASE_URL + "/authorize"); }
    public String getApiKey() { return getString(Constants.PREF_API_KEY, Constants.API_KEY); }
    public String getGateId() { return getString(Constants.PREF_GATE_ID, Constants.GATE_ID); }
}
