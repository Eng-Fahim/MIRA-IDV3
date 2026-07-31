package com.mira.bridge;

import android.app.Application;

public class MiraApp extends Application {
    private static MiraApp instance;
    public static MiraApp getInstance() { return instance; }
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }
}
