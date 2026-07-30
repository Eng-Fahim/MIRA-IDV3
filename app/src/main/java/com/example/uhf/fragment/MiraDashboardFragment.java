package com.example.uhf.fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import com.example.uhf.R;
import com.example.uhf.activity.UHFMainActivity;
import com.example.uhf.manager.MiraSettingsManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MiraDashboardFragment extends KeyDwonFragment {

    private UHFMainActivity mContext;
    private MiraSettingsManager settingsManager;
    private Handler handler = new Handler(Looper.getMainLooper());

    // 🟢 هيدر
    private TextView tvGreeting, tvDate, tvTime, tvLocation;
    
    // 🟢 بطاقات KPI
    private TextView tvTotalItems, tvTotalWeight, tvTotalValue;
    private TextView tvSoldToday, tvSoldWeek, tvSoldMonth;
    private TextView tvActiveGates, tvAlertsToday, tvOnlineUsers;
    
    // 🟢 حالة النظام
    private TextView tvSystemStatus, tvLastSync, tvApiStatus;
    private View indicatorApi, indicatorDb, indicatorGate;
    
    // 🟢 نشاط حديث
    private LinearLayout layoutRecentActivity;
    private TextView tvRecent1, tvRecent2, tvRecent3;
    
    // 🟢 وصول سريع
    private CardView cardScan, cardGate, cardRadar, cardSettings, cardInventory, cardReports;

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
        View view = getView();
        if (view == null) return;

        initViews(view);
        setupClickListeners();
        startLiveUpdates();
    }

    private void initViews(View view) {
        // 🟢 هيدر
        tvGreeting = view.findViewById(R.id.tvGreeting);
        tvDate = view.findViewById(R.id.tvDate);
        tvTime = view.findViewById(R.id.tvTime);
        tvLocation = view.findViewById(R.id.tvLocation);
        
        // 🟢 KPI
        tvTotalItems = view.findViewById(R.id.tvTotalItems);
        tvTotalWeight = view.findViewById(R.id.tvTotalWeight);
        tvTotalValue = view.findViewById(R.id.tvTotalValue);
        tvSoldToday = view.findViewById(R.id.tvSoldToday);
        tvSoldWeek = view.findViewById(R.id.tvSoldWeek);
        tvSoldMonth = view.findViewById(R.id.tvSoldMonth);
        tvActiveGates = view.findViewById(R.id.tvActiveGates);
        tvAlertsToday = view.findViewById(R.id.tvAlertsToday);
        tvOnlineUsers = view.findViewById(R.id.tvOnlineUsers);
        
        // 🟢 حالة النظام
        tvSystemStatus = view.findViewById(R.id.tvSystemStatus);
        tvLastSync = view.findViewById(R.id.tvLastSync);
        tvApiStatus = view.findViewById(R.id.tvApiStatus);
        indicatorApi = view.findViewById(R.id.indicatorApi);
        indicatorDb = view.findViewById(R.id.indicatorDb);
        indicatorGate = view.findViewById(R.id.indicatorGate);
        
        // 🟢 نشاط حديث
        layoutRecentActivity = view.findViewById(R.id.layoutRecentActivity);
        tvRecent1 = view.findViewById(R.id.tvRecent1);
        tvRecent2 = view.findViewById(R.id.tvRecent2);
        tvRecent3 = view.findViewById(R.id.tvRecent3);
        
        // 🟢 وصول سريع
        cardScan = view.findViewById(R.id.cardScan);
        cardGate = view.findViewById(R.id.cardGate);
        cardRadar = view.findViewById(R.id.cardRadar);
        cardSettings = view.findViewById(R.id.cardSettings);
        cardInventory = view.findViewById(R.id.cardInventory);
        cardReports = view.findViewById(R.id.cardReports);
    }

    private void setupClickListeners() {
        if (cardScan != null) cardScan.setOnClickListener(v -> switchToTab(1));
        if (cardGate != null) cardGate.setOnClickListener(v -> switchToTab(10));
        if (cardRadar != null) cardRadar.setOnClickListener(v -> switchToTab(3));
        if (cardSettings != null) cardSettings.setOnClickListener(v -> switchToTab(2));
        if (cardInventory != null) cardInventory.setOnClickListener(v -> switchToTab(5));
        if (cardReports != null) cardReports.setOnClickListener(v -> switchToTab(1));
    }

    private void startLiveUpdates() {
        updateDateTime();
        updateGreeting();
        loadDashboardData();
        
        // تحديث كل 10 ثواني
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateDateTime();
                loadDashboardData();
                handler.postDelayed(this, 10000);
            }
        }, 10000);
    }

    // ============================================
    // 🟢 التاريخ والوقت
    // ============================================
    private void updateDateTime() {
        try {
            SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE, dd MMMM yyyy", new Locale("ar"));
            SimpleDateFormat timeFmt = new SimpleDateFormat("hh:mm:ss a", new Locale("ar"));
            Date now = new Date();
            if (tvDate != null) tvDate.setText(dateFmt.format(now));
            if (tvTime != null) tvTime.setText(timeFmt.format(now));
        } catch (Exception ignored) {}
    }

    private void updateGreeting() {
        if (tvGreeting == null) return;
        int hour = new Date().getHours();
        String greeting;
        if (hour < 12) greeting = "صباح الخير ☀️";
        else if (hour < 17) greeting = "مساء الخير 🌤️";
        else greeting = "مساء الخير 🌙";
        tvGreeting.setText(greeting);
    }

    // ============================================
    // 🟢 تحميل البيانات
    // ============================================
    private void loadDashboardData() {
        // 🟢 محاكاة KPI (تُستبدل بـ API حقيقي)
        animateNumber(tvTotalItems, "5,234");
        animateNumber(tvTotalWeight, "12.5 kg");
        animateNumber(tvTotalValue, "$245K");
        animateNumber(tvSoldToday, "12");
        animateNumber(tvSoldWeek, "47");
        animateNumber(tvSoldMonth, "189");
        animateNumber(tvActiveGates, "3");
        animateNumber(tvAlertsToday, "2");
        animateNumber(tvOnlineUsers, "8");

        // 🟢 حالة النظام
        updateSystemStatus();
        
        // 🟢 نشاط حديث
        updateRecentActivity();
        
        // 🟢 مزامنة
        if (tvLastSync != null) {
            tvLastSync.setText("آخر مزامنة: " + new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()));
        }
    }

    private void animateNumber(TextView tv, String text) {
        if (tv != null) tv.setText(text);
    }

    private void updateSystemStatus() {
        // 🟢 API Status
        if (indicatorApi != null) indicatorApi.setBackgroundColor(Color.parseColor("#4CAF50"));
        if (tvApiStatus != null) {
            tvApiStatus.setText("API: متصل");
            tvApiStatus.setTextColor(Color.parseColor("#81C784"));
        }
        
        // 🟢 Database
        if (indicatorDb != null) indicatorDb.setBackgroundColor(Color.parseColor("#4CAF50"));
        
        // 🟢 Gate Status
        if (indicatorGate != null) indicatorGate.setBackgroundColor(Color.parseColor("#4CAF50"));
        
        // 🟢 Overall
        if (tvSystemStatus != null) {
            tvSystemStatus.setText("🟢 جميع الأنظمة تعمل");
            tvSystemStatus.setTextColor(Color.parseColor("#4CAF50"));
        }
    }

    private void updateRecentActivity() {
        if (tvRecent1 != null) tvRecent1.setText("✅ 12:30 - خروج مصرح: خاتم ذهب 21K");
        if (tvRecent2 != null) tvRecent2.setText("🚨 12:28 - محاولة خروج غير مصرح: سوارة");
        if (tvRecent3 != null) tvRecent3.setText("📋 12:15 - تقرير جرد يومي مكتمل");
    }

    private void switchToTab(int index) {
        if (mContext != null && mContext.mTabHost != null) {
            mContext.mTabHost.setCurrentTab(index);
        }
    }

    @Override
    public void myOnKeyDwon() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
                                                            }
