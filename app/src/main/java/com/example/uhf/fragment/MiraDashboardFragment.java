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

import com.example.uhf.R;
import com.example.uhf.activity.UHFMainActivity;
import com.example.uhf.manager.MiraSettingsManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * MIRA Dashboard - لوحة القيادة الرئيسية
 */
public class MiraDashboardFragment extends KeyDwonFragment {  // ✅ KeyDwonFragment بدل Fragment

    private UHFMainActivity mContext;
    private MiraSettingsManager settingsManager;
    private Handler handler = new Handler(Looper.getMainLooper());

    private TextView tvTotalItems, tvSoldToday, tvActiveGates;
    private TextView tvLastSync, tvSystemStatus;
    private TextView tvDateTime;

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

        tvTotalItems = view.findViewById(R.id.tvTotalItems);
        tvSoldToday = view.findViewById(R.id.tvSoldToday);
        tvActiveGates = view.findViewById(R.id.tvActiveGates);
        tvLastSync = view.findViewById(R.id.tvLastSync);
        tvSystemStatus = view.findViewById(R.id.tvSystemStatus);
        tvDateTime = view.findViewById(R.id.tvDateTime);

        CardView cardScan = view.findViewById(R.id.cardScan);
        CardView cardGate = view.findViewById(R.id.cardGate);
        CardView cardRadar = view.findViewById(R.id.cardRadar);
        CardView cardSettings = view.findViewById(R.id.cardSettings);

        if (cardScan != null) cardScan.setOnClickListener(v -> switchToTab(1));
        if (cardGate != null) cardGate.setOnClickListener(v -> switchToTab(10));
        if (cardRadar != null) cardRadar.setOnClickListener(v -> switchToTab(3));
        if (cardSettings != null) cardSettings.setOnClickListener(v -> switchToTab(2));

        updateDateTime();
        loadDashboardData();
    }

    private void updateDateTime() {
        if (tvDateTime == null) return;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("EEEE, dd MMMM yyyy  |  HH:mm:ss", new Locale("ar"));
            tvDateTime.setText(sdf.format(new Date()));
        } catch (Exception e) {
            tvDateTime.setText(new Date().toString());
        }
    }

    private void loadDashboardData() {
        if (tvSystemStatus != null) {
            tvSystemStatus.setText("🟢 متصل بـ MIRA ID");
            tvSystemStatus.setTextColor(Color.parseColor("#4CAF50"));
        }
        if (tvLastSync != null) tvLastSync.setText("آخر مزامنة: --:--");
        if (tvTotalItems != null) tvTotalItems.setText("5,234");
        if (tvSoldToday != null) tvSoldToday.setText("12");
        if (tvActiveGates != null) tvActiveGates.setText("3");
    }

    private void switchToTab(int index) {
        if (mContext != null && mContext.mTabHost != null) {
            mContext.mTabHost.setCurrentTab(index);
        }
    }

    @Override
    public void myOnKeyDwon() {
        // Dashboard لا تحتاج زر
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
