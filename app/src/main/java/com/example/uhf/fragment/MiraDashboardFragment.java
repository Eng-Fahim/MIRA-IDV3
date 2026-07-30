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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MiraDashboardFragment extends KeyDwonFragment {

    private UHFMainActivity mContext;
    private Handler handler = new Handler(Looper.getMainLooper());

    private TextView tvGreeting, tvDate, tvTime;
    private TextView tvTotalItems, tvTotalWeight, tvTotalValue;
    private TextView tvSoldToday, tvSoldWeek, tvSoldMonth;
    private TextView tvSystemStatus, tvLastSync;
    private View indicatorApi;
    private TextView tvRecent1, tvRecent2, tvRecent3;

    // 🟢 وصول سريع
    private View cardScan, cardGate, cardRadar, cardSettings;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        try {
            return inflater.inflate(R.layout.fragment_mira_dashboard, container, false);
        } catch (Exception e) {
            e.printStackTrace();
            return new View(getActivity());
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        
        try {
            mContext = (UHFMainActivity) getActivity();
        } catch (Exception e) {
            return;
        }
        
        if (mContext == null) return;

        View view = getView();
        if (view == null) return;

        // 🟢 ربط العناصر الأساسية فقط
        try {
            tvGreeting = view.findViewById(R.id.tvGreeting);
            tvDate = view.findViewById(R.id.tvDate);
            tvTime = view.findViewById(R.id.tvTime);
            tvTotalItems = view.findViewById(R.id.tvTotalItems);
            tvTotalWeight = view.findViewById(R.id.tvTotalWeight);
            tvTotalValue = view.findViewById(R.id.tvTotalValue);
            tvSoldToday = view.findViewById(R.id.tvSoldToday);
            tvSoldWeek = view.findViewById(R.id.tvSoldWeek);
            tvSoldMonth = view.findViewById(R.id.tvSoldMonth);
            tvSystemStatus = view.findViewById(R.id.tvSystemStatus);
            tvLastSync = view.findViewById(R.id.tvLastSync);
            indicatorApi = view.findViewById(R.id.indicatorApi);
            tvRecent1 = view.findViewById(R.id.tvRecent1);
            tvRecent2 = view.findViewById(R.id.tvRecent2);
            tvRecent3 = view.findViewById(R.id.tvRecent3);
            cardScan = view.findViewById(R.id.cardScan);
            cardGate = view.findViewById(R.id.cardGate);
            cardRadar = view.findViewById(R.id.cardRadar);
            cardSettings = view.findViewById(R.id.cardSettings);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // 🟢 مستمعات آمنة
        if (cardScan != null) cardScan.setOnClickListener(v -> safeSwitchTab(1));
        if (cardGate != null) cardGate.setOnClickListener(v -> safeSwitchTab(9));
        if (cardRadar != null) cardRadar.setOnClickListener(v -> safeSwitchTab(3));
        if (cardSettings != null) cardSettings.setOnClickListener(v -> safeSwitchTab(2));

        // 🟢 تحميل البيانات
        loadSafeData();
    }

    private void loadSafeData() {
        try {
            // التاريخ
            SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE, dd MMMM yyyy", new Locale("ar"));
            SimpleDateFormat timeFmt = new SimpleDateFormat("hh:mm:ss a", new Locale("ar"));
            Date now = new Date();
            if (tvDate != null) tvDate.setText(dateFmt.format(now));
            if (tvTime != null) tvTime.setText(timeFmt.format(now));

            // تحية
            if (tvGreeting != null) {
                int hour = now.getHours();
                if (hour < 12) tvGreeting.setText("صباح الخير ☀️");
                else if (hour < 17) tvGreeting.setText("مساء الخير 🌤️");
                else tvGreeting.setText("مساء الخير 🌙");
            }

            // أرقام
            if (tvTotalItems != null) tvTotalItems.setText("5,234");
            if (tvTotalWeight != null) tvTotalWeight.setText("12.5 kg");
            if (tvTotalValue != null) tvTotalValue.setText("$245K");
            if (tvSoldToday != null) tvSoldToday.setText("12");
            if (tvSoldWeek != null) tvSoldWeek.setText("47");
            if (tvSoldMonth != null) tvSoldMonth.setText("189");

            // حالة
            if (indicatorApi != null) indicatorApi.setBackgroundColor(Color.parseColor("#4CAF50"));
            if (tvSystemStatus != null) {
                tvSystemStatus.setText("🟢 النظام يعمل");
                tvSystemStatus.setTextColor(Color.parseColor("#4CAF50"));
            }
            if (tvLastSync != null) tvLastSync.setText("آخر مزامنة: " + timeFmt.format(now));

            // نشاط
            if (tvRecent1 != null) tvRecent1.setText("✅ خروج مصرح: خاتم ذهب 21K");
            if (tvRecent2 != null) tvRecent2.setText("🚨 محاولة خروج غير مصرح");
            if (tvRecent3 != null) tvRecent3.setText("📋 تقرير جرد يومي مكتمل");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void safeSwitchTab(int index) {
        try {
            if (mContext != null && mContext.mTabHost != null) {
                mContext.mTabHost.setCurrentTab(index);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void myOnKeyDwon() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
}
