package com.example.uhf.fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.example.uhf.R;
import com.example.uhf.activity.UHFMainActivity;
import com.example.uhf.manager.MiraSettingsManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * MIRA Dashboard - لوحة القيادة الرئيسية
 * 
 * تعرض:
 * - إحصائيات حية من MIRA ID
 * - حالة النظام
 * - وصول سريع للوظائف
 */
public class MiraDashboardFragment extends Fragment {

    private UHFMainActivity mContext;
    private MiraSettingsManager settingsManager;
    private Handler handler = new Handler(Looper.getMainLooper());

    // 🟢 بطاقات الإحصائيات
    private TextView tvTotalItems, tvSoldToday, tvActiveGates;
    private TextView tvLastSync, tvSystemStatus;
    
    // 🟢 الوقت والتاريخ
    private TextView tvDateTime;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mira_dashboard, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        settingsManager = MiraSettingsManager.getInstance(mContext);

        View view = getView();
        if (view == null) return;

        // 🟢 ربط العناصر
        tvTotalItems = view.findViewById(R.id.tvTotalItems);
        tvSoldToday = view.findViewById(R.id.tvSoldToday);
        tvActiveGates = view.findViewById(R.id.tvActiveGates);
        tvLastSync = view.findViewById(R.id.tvLastSync);
        tvSystemStatus = view.findViewById(R.id.tvSystemStatus);
        tvDateTime = view.findViewById(R.id.tvDateTime);

        // 🟢 بطاقات الوصول السريع
        CardView cardScan = view.findViewById(R.id.cardScan);
        CardView cardGate = view.findViewById(R.id.cardGate);
        CardView cardRadar = view.findViewById(R.id.cardRadar);
        CardView cardSettings = view.findViewById(R.id.cardSettings);

        cardScan.setOnClickListener(v -> switchToTab(0));
        cardGate.setOnClickListener(v -> switchToTab(9)); // MIRA Gate
        cardRadar.setOnClickListener(v -> switchToTab(2)); // RADAR
        cardSettings.setOnClickListener(v -> switchToTab(1)); // CONFIG

        // 🟢 بدء التحديثات
        updateDateTime();
        loadDashboardData();
        
        // تحديث كل 30 ثانية
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateDateTime();
                loadDashboardData();
                handler.postDelayed(this, 30000);
            }
        }, 30000);
    }

    // ============================================
    // 🟢 تحديث الوقت
    // ============================================
    private void updateDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, dd MMMM yyyy  |  HH:mm:ss", new Locale("ar"));
        tvDateTime.setText(sdf.format(new Date()));
    }

    // ============================================
    // 🟢 تحميل بيانات لوحة القيادة
    // ============================================
    private void loadDashboardData() {
        // محاكاة بيانات (في الإصدار النهائي: استدعاء MIRA API)
        new Thread(() -> {
            try {
                String apiUrl = settingsManager.getString("mira_api_url",
                    "https://ams.ibreg.org/wp-json/mira-gate/v1/gates/stats");
                String apiKey = settingsManager.getString("mira_api_key",
                    "mira_gate_test071234567890abcdefghijklmnop");

                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("X-MIRA-API-Key", apiKey);
                conn.setConnectTimeout(5000);

                int code = conn.getResponseCode();
                
                handler.post(() -> {
                    if (code == 200) {
                        tvSystemStatus.setText("🟢 متصل بـ MIRA ID");
                        tvSystemStatus.setTextColor(Color.parseColor("#4CAF50"));
                        tvLastSync.setText("آخر مزامنة: " + 
                            new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()));
                    } else {
                        tvSystemStatus.setText("🔴 غير متصل");
                        tvSystemStatus.setTextColor(Color.parseColor("#F44336"));
                    }
                });

            } catch (Exception e) {
                handler.post(() -> {
                    tvSystemStatus.setText("⚫ وضع غير متصل");
                    tvSystemStatus.setTextColor(Color.parseColor("#FF9800"));
                });
            }
        }).start();

        // 🟢 محاكاة إحصائيات (تُستبدل بـ API حقيقي)
        handler.post(() -> {
            tvTotalItems.setText("5,234");
            tvSoldToday.setText("12");
            tvActiveGates.setText("3");
        });
    }

    // ============================================
    // 🟢 التنقل بين التبويبات
    // ============================================
    private void switchToTab(int index) {
        if (mContext != null && mContext.mTabHost != null) {
            mContext.mTabHost.setCurrentTab(index);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
