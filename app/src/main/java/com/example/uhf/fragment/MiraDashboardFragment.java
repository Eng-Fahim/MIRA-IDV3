package com.example.uhf.fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
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

public class MiraDashboardFragment extends KeyDwonFragment {

    private UHFMainActivity mContext;
    private MiraSettingsManager settingsManager;
    private Handler handler = new Handler(Looper.getMainLooper());
    
    private TextView tvGreeting, tvDate, tvTime;
    private TextView tvTotalItems, tvSoldToday, tvActiveGates;
    private TextView tvSystemStatus, tvLastSync;
    private View indicatorApi, indicatorDb, indicatorGate;
    private TextView tvRecent1, tvRecent2, tvRecent3;
    private View cardScan, cardGate, cardRadar, cardSettings;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mira_dashboard, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        if (mContext == null) return;
        
        settingsManager = MiraSettingsManager.getInstance(mContext);
        View v = getView();
        if (v == null) return;

        initViews(v);
        setupClicks();
        updateUI();
        startAutoRefresh();
    }

    private void initViews(View v) {
        tvGreeting = v.findViewById(R.id.tvGreeting);
        tvDate = v.findViewById(R.id.tvDate);
        tvTime = v.findViewById(R.id.tvTime);
        tvTotalItems = v.findViewById(R.id.tvTotalItems);
        tvSoldToday = v.findViewById(R.id.tvSoldToday);
        tvActiveGates = v.findViewById(R.id.tvActiveGates);
        tvSystemStatus = v.findViewById(R.id.tvSystemStatus);
        tvLastSync = v.findViewById(R.id.tvLastSync);
        indicatorApi = v.findViewById(R.id.indicatorApi);
        indicatorDb = v.findViewById(R.id.indicatorDb);
        indicatorGate = v.findViewById(R.id.indicatorGate);
        tvRecent1 = v.findViewById(R.id.tvRecent1);
        tvRecent2 = v.findViewById(R.id.tvRecent2);
        tvRecent3 = v.findViewById(R.id.tvRecent3);
        cardScan = v.findViewById(R.id.cardScan);
        cardGate = v.findViewById(R.id.cardGate);
        cardRadar = v.findViewById(R.id.cardRadar);
        cardSettings = v.findViewById(R.id.cardSettings);
    }

    private void setupClicks() {
        if (cardScan != null) cardScan.setOnClickListener(v2 -> switchTab(1));
        if (cardGate != null) cardGate.setOnClickListener(v2 -> switchTab(9));
        if (cardRadar != null) cardRadar.setOnClickListener(v2 -> switchTab(3));
        if (cardSettings != null) cardSettings.setOnClickListener(v2 -> switchTab(2));
    }

    private void updateUI() {
        // وقت وتاريخ
        Date now = new Date();
        SimpleDateFormat df = new SimpleDateFormat("EEEE, dd MMMM yyyy", new Locale("ar"));
        SimpleDateFormat tf = new SimpleDateFormat("hh:mm:ss a", new Locale("ar"));
        if (tvDate != null) tvDate.setText(df.format(now));
        if (tvTime != null) tvTime.setText(tf.format(now));
        
        // تحية
        if (tvGreeting != null) {
            int h = now.getHours();
            tvGreeting.setText(h < 12 ? "صباح الخير ☀️" : h < 17 ? "مساء الخير 🌤️" : "مساء الخير 🌙");
        }

        // KPI - محاكاة (تُستبدل بـ API)
        if (tvTotalItems != null) tvTotalItems.setText("5,234");
        if (tvSoldToday != null) tvSoldToday.setText("12");
        if (tvActiveGates != null) tvActiveGates.setText("3");

        // حالة النظام
        if (indicatorApi != null) indicatorApi.setBackgroundColor(Color.parseColor("#4CAF50"));
        if (indicatorDb != null) indicatorDb.setBackgroundColor(Color.parseColor("#4CAF50"));
        if (indicatorGate != null) indicatorGate.setBackgroundColor(Color.parseColor("#4CAF50"));
        if (tvSystemStatus != null) tvSystemStatus.setText("🟢 جميع الأنظمة تعمل");
        if (tvLastSync != null) tvLastSync.setText("آخر مزامنة: " + tf.format(now));

        // نشاط
        if (tvRecent1 != null) tvRecent1.setText("✅ 12:30 - خروج مصرح: خاتم ذهب 21K");
        if (tvRecent2 != null) tvRecent2.setText("🚨 12:28 - محاولة خروج غير مصرح: سوارة");
        if (tvRecent3 != null) tvRecent3.setText("📋 12:15 - تقرير جرد يومي مكتمل");

        // محاولة جلب بيانات حية
        fetchLiveData();
    }

    private void fetchLiveData() {
        new Thread(() -> {
            try {
                String apiUrl = settingsManager.getString("mira_api_url",
                    "https://ams.ibreg.org/wp-json/mira-gate/v1/gates/stats");
                String apiKey = settingsManager.getString("mira_api_key",
                    "mira_gate_test071234567890abcdefghijklmnop");

                URL url = new URL(apiUrl);
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("GET");
                c.setRequestProperty("X-MIRA-API-Key", apiKey);
                c.setConnectTimeout(3000);
                
                if (c.getResponseCode() == 200) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                    StringBuilder sb = new StringBuilder(); String l;
                    while ((l = r.readLine()) != null) sb.append(l);
                    JSONObject json = new JSONObject(sb.toString());
                    
                    handler.post(() -> {
                        try {
                            if (json.has("data")) {
                                JSONObject d = json.getJSONObject("data");
                                if (tvTotalItems != null && d.has("total"))
                                    tvTotalItems.setText(String.valueOf(d.getInt("total")));
                                if (tvActiveGates != null && d.has("online"))
                                    tvActiveGates.setText(String.valueOf(d.getInt("online")));
                            }
                        } catch (Exception ignored) {}
                    });
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void startAutoRefresh() {
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                updateUI();
                handler.postDelayed(this, 30000);
            }
        }, 30000);
    }

    private void switchTab(int index) {
        if (mContext != null && mContext.mTabHost != null) {
            mContext.mTabHost.setCurrentTab(index);
        }
    }

    @Override public void myOnKeyDwon() {}
    
    @Override public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
