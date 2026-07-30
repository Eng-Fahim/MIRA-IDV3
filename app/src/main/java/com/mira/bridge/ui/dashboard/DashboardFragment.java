package com.mira.bridge.ui.dashboard;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import com.mira.bridge.MainActivity;
import com.mira.bridge.MiraApp;
import com.mira.bridge.R;
import com.mira.bridge.api.MiraApiClient;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DashboardFragment extends Fragment {

    private Handler handler = new Handler(Looper.getMainLooper());
    private TextView tvGreeting, tvDate, tvTime, tvLocation;
    private TextView tvTotalItems, tvTotalWeight, tvTotalValue;
    private TextView tvSoldToday, tvSoldWeek, tvSoldMonth;
    private TextView tvActiveGates, tvAlertsToday, tvOnlineUsers;
    private TextView tvSystemStatus, tvLastSync;
    private View indicatorApi, indicatorDb, indicatorGate;
    private TextView tvRecent1, tvRecent2, tvRecent3;
    private LinearLayout cardScan, cardGate, cardRadar, cardSettings, cardInventory, cardReports;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupClicks();
        updateUI();
        startRefresh();
    }

    private void initViews(View v) {
        tvGreeting = v.findViewById(R.id.tvGreeting);
        tvDate = v.findViewById(R.id.tvDate);
        tvTime = v.findViewById(R.id.tvTime);
        tvLocation = v.findViewById(R.id.tvLocation);
        tvTotalItems = v.findViewById(R.id.tvTotalItems);
        tvTotalWeight = v.findViewById(R.id.tvTotalWeight);
        tvTotalValue = v.findViewById(R.id.tvTotalValue);
        tvSoldToday = v.findViewById(R.id.tvSoldToday);
        tvSoldWeek = v.findViewById(R.id.tvSoldWeek);
        tvSoldMonth = v.findViewById(R.id.tvSoldMonth);
        tvActiveGates = v.findViewById(R.id.tvActiveGates);
        tvAlertsToday = v.findViewById(R.id.tvAlertsToday);
        tvOnlineUsers = v.findViewById(R.id.tvOnlineUsers);
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
        cardInventory = v.findViewById(R.id.cardInventory);
        cardReports = v.findViewById(R.id.cardReports);
    }

    private void setupClicks() {
        cardScan.setOnClickListener(v2 -> navigate(R.id.nav_scan));
        cardGate.setOnClickListener(v2 -> navigate(R.id.nav_gates));
        cardRadar.setOnClickListener(v2 -> navigate(R.id.nav_scan));
        cardSettings.setOnClickListener(v2 -> navigate(R.id.nav_settings));
        cardInventory.setOnClickListener(v2 -> navigate(R.id.nav_studio));
        cardReports.setOnClickListener(v2 -> navigate(R.id.nav_settings));
    }

    private void navigate(int id) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateTo(id);
        }
    }

    private void updateUI() {
        Date now = new Date();
        SimpleDateFormat df = new SimpleDateFormat("EEEE, dd MMMM yyyy", new Locale("ar"));
        SimpleDateFormat tf = new SimpleDateFormat("hh:mm:ss a", new Locale("ar"));
        tvDate.setText(df.format(now));
        tvTime.setText(tf.format(now));
        tvLocation.setText("📍 المعرض الرئيسي - صنعاء");
        int h = now.getHours();
        tvGreeting.setText(h < 12 ? "صباح الخير ☀️" : h < 17 ? "مساء الخير 🌤️" : "مساء الخير 🌙");
        
        tvTotalItems.setText("5,234");
        tvTotalWeight.setText("12.5 kg");
        tvTotalValue.setText("$245K");
        tvSoldToday.setText("12");
        tvSoldWeek.setText("47");
        tvSoldMonth.setText("189");
        tvActiveGates.setText("3");
        tvAlertsToday.setText("2");
        tvOnlineUsers.setText("8");
        
        indicatorApi.setBackgroundColor(Color.parseColor("#4CAF50"));
        indicatorDb.setBackgroundColor(Color.parseColor("#4CAF50"));
        indicatorGate.setBackgroundColor(Color.parseColor("#4CAF50"));
        tvSystemStatus.setText("🟢 جميع الأنظمة تعمل");
        tvLastSync.setText("آخر مزامنة: " + tf.format(now));
        
        tvRecent1.setText("✅ 12:30 - خروج مصرح: خاتم ذهب 21K");
        tvRecent2.setText("🚨 12:28 - محاولة خروج غير مصرح: سوارة");
        tvRecent3.setText("📋 12:15 - تقرير جرد يومي مكتمل");
        
        fetchLiveData();
    }

    private void fetchLiveData() {
        MiraApp.getInstance().getApiClient().getDashboardData(new MiraApiClient.ApiCallback() {
            @Override
            public void onSuccess(int code, String response) {
                try {
                    JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                    if (json.has("data")) {
                        JsonObject data = json.getAsJsonObject("data");
                        handler.post(() -> {
                            if (data.has("total")) tvTotalItems.setText(data.get("total").getAsString());
                            if (data.has("online")) tvActiveGates.setText(data.get("online").getAsString());
                            tvLastSync.setText("آخر مزامنة: " + new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()));
                        });
                    }
                } catch (Exception ignored) {}
            }
            @Override public void onError(String error) {}
        });
    }

    private void startRefresh() {
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                updateUI();
                handler.postDelayed(this, 30000);
            }
        }, 30000);
    }

    @Override public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
