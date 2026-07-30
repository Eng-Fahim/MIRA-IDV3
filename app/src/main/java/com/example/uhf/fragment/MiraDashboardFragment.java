package com.example.uhf.fragment;

import android.graphics.Color;
import android.os.Bundle;
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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mira_dashboard, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        if (mContext == null || getView() == null) return;

        View v = getView();
        TextView tvGreeting = v.findViewById(R.id.tvGreeting);
        TextView tvDate = v.findViewById(R.id.tvDate);
        TextView tvTime = v.findViewById(R.id.tvTime);
        TextView tvTotalItems = v.findViewById(R.id.tvTotalItems);
        TextView tvTotalWeight = v.findViewById(R.id.tvTotalWeight);
        TextView tvTotalValue = v.findViewById(R.id.tvTotalValue);
        TextView tvSoldToday = v.findViewById(R.id.tvSoldToday);
        TextView tvSoldWeek = v.findViewById(R.id.tvSoldWeek);
        TextView tvSoldMonth = v.findViewById(R.id.tvSoldMonth);
        TextView tvSystemStatus = v.findViewById(R.id.tvSystemStatus);
        TextView tvLastSync = v.findViewById(R.id.tvLastSync);
        View indicatorApi = v.findViewById(R.id.indicatorApi);
        TextView tvRecent1 = v.findViewById(R.id.tvRecent1);
        TextView tvRecent2 = v.findViewById(R.id.tvRecent2);
        TextView tvRecent3 = v.findViewById(R.id.tvRecent3);
        View cardScan = v.findViewById(R.id.cardScan);
        View cardGate = v.findViewById(R.id.cardGate);
        View cardRadar = v.findViewById(R.id.cardRadar);
        View cardSettings = v.findViewById(R.id.cardSettings);

        // تحية
        int hour = new Date().getHours();
        String greeting = hour < 12 ? "صباح الخير ☀️" : hour < 17 ? "مساء الخير 🌤️" : "مساء الخير 🌙";
        setText(tvGreeting, greeting);

        // تاريخ ووقت
        SimpleDateFormat df = new SimpleDateFormat("EEEE, dd MMMM yyyy", new Locale("ar"));
        SimpleDateFormat tf = new SimpleDateFormat("hh:mm:ss a", new Locale("ar"));
        String now = new Date().toString();
        try { setText(tvDate, df.format(new Date())); } catch (Exception e) { setText(tvDate, now); }
        try { setText(tvTime, tf.format(new Date())); } catch (Exception e) { setText(tvTime, now); }

        // KPI
        setText(tvTotalItems, "5,234");
        setText(tvTotalWeight, "12.5 kg");
        setText(tvTotalValue, "$245K");
        setText(tvSoldToday, "12");
        setText(tvSoldWeek, "47");
        setText(tvSoldMonth, "189");

        // حالة
        if (indicatorApi != null) indicatorApi.setBackgroundColor(Color.parseColor("#4CAF50"));
        setText(tvSystemStatus, "🟢 جميع الأنظمة تعمل");
        setText(tvLastSync, "آخر مزامنة: " + tf.format(new Date()));

        // نشاط
        setText(tvRecent1, "✅ خروج مصرح: خاتم ذهب 21K");
        setText(tvRecent2, "🚨 محاولة خروج غير مصرح: سوارة");
        setText(tvRecent3, "📋 تقرير جرد يومي مكتمل");

        // أزرار
        if (cardScan != null) cardScan.setOnClickListener(view -> switchTab(1));
        if (cardGate != null) cardGate.setOnClickListener(view -> switchTab(9));
        if (cardRadar != null) cardRadar.setOnClickListener(view -> switchTab(3));
        if (cardSettings != null) cardSettings.setOnClickListener(view -> switchTab(2));
    }

    private void setText(TextView tv, String text) {
        if (tv != null) tv.setText(text);
    }

    private void switchTab(int index) {
        if (mContext != null && mContext.mTabHost != null) {
            mContext.mTabHost.setCurrentTab(index);
        }
    }

    @Override
    public void myOnKeyDwon() {}
}
