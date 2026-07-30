package com.mira.bridge.api;

import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mira.bridge.MiraApp;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

public class MiraApiClient {
    
    private static final String TAG = "MiraApi";
    private final OkHttpClient client;
    private final Gson gson;
    
    public MiraApiClient() {
        client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();
        gson = new Gson();
    }
    
    private String getBaseUrl() {
        String url = MiraApp.getInstance().getPrefs().getString("api_url", 
            com.mira.bridge.utils.Constants.API_BASE_URL + "/authorize");
        return url.replace("/authorize", "");
    }
    
    private String getApiKey() {
        return MiraApp.getInstance().getPrefs().getString("api_key", 
            com.mira.bridge.utils.Constants.API_KEY);
    }
    
    public void authorize(String epc, String rssi, ApiCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("epc", epc);
        body.addProperty("gate_id", MiraApp.getInstance().getPrefs().getGateId());
        if (rssi != null) body.addProperty("rssi", rssi);
        post("/authorize", body, callback);
    }
    
    public void getDashboardData(ApiCallback callback) {
        get("/gates/stats", callback);
    }
    
    public void getGatesStatus(ApiCallback callback) {
        get("/gates-status", callback);
    }
    
    public void getItemDetail(String code, ApiCallback callback) {
        get("/item/" + code, callback);
    }
    
    public void getSettings(ApiCallback callback) {
        get("/settings/" + MiraApp.getInstance().getPrefs().getGateId(), callback);
    }
    
    public void saveSettings(JsonObject settings, ApiCallback callback) {
        post("/settings", settings, callback);
    }
    
    private void post(String path, JsonObject body, ApiCallback callback) {
        String url = getBaseUrl() + path;
        RequestBody requestBody = RequestBody.create(
            body.toString(), MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).post(requestBody)
            .addHeader("X-MIRA-API-Key", getApiKey())
            .addHeader("Content-Type", "application/json").build();
        execute(request, callback);
    }
    
    private void get(String path, ApiCallback callback) {
        String url = getBaseUrl() + path;
        Request request = new Request.Builder().url(url).get()
            .addHeader("X-MIRA-API-Key", getApiKey()).build();
        execute(request, callback);
    }
    
    private void execute(Request request, ApiCallback callback) {
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (callback != null) callback.onError(e.getMessage());
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "{}";
                if (callback != null) callback.onSuccess(response.code(), body);
            }
        });
    }
    
    public interface ApiCallback {
        void onSuccess(int code, String response);
        void onError(String error);
    }
}
