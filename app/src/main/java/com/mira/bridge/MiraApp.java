package com.mira.bridge;

import android.app.Application;
import com.mira.bridge.api.MiraApiClient;
import com.mira.bridge.data.PreferencesManager;

public class MiraApp extends Application {
    
    private static MiraApp instance;
    private MiraApiClient apiClient;
    private PreferencesManager prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        apiClient = new MiraApiClient();
        prefs = new PreferencesManager(this);
    }

    public static MiraApp getInstance() { return instance; }
    public MiraApiClient getApiClient() { return apiClient; }
    public PreferencesManager getPrefs() { return prefs; }
}
