package com.mira.core.network;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * MIRA Core API Client
 * 
 * العميل المركزي للاتصال بالخدمات السحابية لمنظومة MIRA
 * يوفر معالجة موحدة لطلبات POST/GET وتأمين المفاتيح والمهل الزمنية
 */
public class MiraApiClient {

    private static final String TAG = "MiraApiClient";
    private static final int TIMEOUT_MS = 5000;

    public interface ApiResponseCallback {
        void onSuccess(JSONObject response);
        void onError(String errorMessage);
    }

    public static void postRequest(String endpointUrl, String apiKey, JSONObject jsonPayload, ApiResponseCallback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(endpointUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                if (apiKey != null && !apiKey.isEmpty()) {
                    conn.setRequestProperty("X-MIRA-API-Key", apiKey);
                }
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setDoOutput(true);

                if (jsonPayload != null) {
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(jsonPayload.toString().getBytes("utf-8"));
                    }
                }

                int responseCode = conn.getResponseCode();
                InputStream is = (responseCode >= 200 && responseCode < 300)
                        ? conn.getInputStream() : conn.getErrorStream();

                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "utf-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                JSONObject jsonResponse = new JSONObject(response.toString());
                if (callback != null) {
                    callback.onSuccess(jsonResponse);
                }

            } catch (Exception e) {
                Log.e(TAG, "API Request failed: " + e.getMessage());
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }
}
