package com.mira.bridge;

import android.app.Application;

public class MiraApp extends Application {
    
    private static MiraApp instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static MiraApp getInstance() { return instance; }
}
