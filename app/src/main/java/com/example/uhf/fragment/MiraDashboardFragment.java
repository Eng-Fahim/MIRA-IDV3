package com.example.uhf.fragment;

import android.content.Context;
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
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MiraDashboardFragment extends KeyDwonFragment {

    private UHFMainActivity mContext;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    
    private TextView tvGreeting, tvDate, tvTime;
    private TextView tvTotalItems, tvSoldToday, tvActiveGates;
    private TextView tvSystemStatus, tvLastSync;
    private View indicatorApi, indicatorDb, indicatorGate;
    private TextView tvRecent1, tvRecent2, tvRecent3;
    private View cardScan, cardGate, cardRadar, cardSettings;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        // التحقق من نوع الـ Activity بأمان لتجنب الـ ClassCastException
        if (context instanceof UHFMainActivity) {
            mContext = (UHFMainActivity) context;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mira_dashboard, container, false);
    }

    @Override
    public void onViewCreated(View v, Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

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

        if (cardScan != null) cardScan.setOnClickListener(v2 -> switchTab(1));
        if (cardGate != null) cardGate.setOnClickListener(v2 -> switchTab(9));
        if (cardRadar != null) cardRadar.setOnClickListener(v2 -> switchTab(3));
        if (cardSettings != null) cardSettings.setOnClickListener(v2 -> switchTab(2));

        updateAll();
        
        // إعداد الـ Runnable بأمان مع التحقق من وجود الـ Fragment في الواجهة
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isAdded() && getActivity() != null) {
                    updateAll();
                    handler.postDelayed(this, 30000);
                }
            }
        };
        handler.postDelayed(updateRunnable, 30000);
    }

    private void updateAll() {
        if (!isAdded()) return;

        Date now = new Date();
        SimpleDateFormat df = new SimpleDateFormat("EEEE، dd MMMM yyyy", new Locale("ar"));
        SimpleDateFormat tf = new SimpleDateFormat("hh:mm:ss a", new Locale("ar"));

        if (tvDate != null) tvDate.setText(df.format(now));
        if (tvTime != null) tvTime.setText(tf.format(now));
        
        if (tvGreeting != null) {
            Calendar calendar = Calendar.getInstance();
            int h = calendar.get(Calendar.HOUR_OF_DAY);
            tvGreeting.setText(h < 12 ? "صباح الخير ☀️" : h < 17 ? "مساء الخير 🌤️" : "مساء الخير 🌙");
        }

        if (tvTotalItems != null) tvTotalItems.setText("5,234");
        if (tvSoldToday != null) tvSoldToday.setText("12");
        if (tvActiveGates != null) tvActiveGates.setText("3");

        if (indicatorApi != null) indicatorApi.setBackgroundColor(Color.parseColor("#4CAF50"));
        if (indicatorDb != null) indicatorDb.setBackgroundColor(Color.parseColor("#4CAF50"));
        if (indicatorGate != null) indicatorGate.setBackgroundColor(Color.parseColor("#4CAF50"));
        if (tvSystemStatus != null) tvSystemStatus.setText("🟢 جميع الأنظمة تعمل");
        if (tvLastSync != null) tvLastSync.setText("آخر مزامنة: " + tf.format(now));

        if (tvRecent1 != null) tvRecent1.setText("✅ 12:30 - خروج مصرح: خاتم ذهب 21K");
        if (tvRecent2 != null) tvRecent2.setText("🚨 12:28 - محاولة خروج غير مصرح: سوارة");
        if (tvRecent3 != null) tvRecent3.setText("📋 12:15 - تقرير جرد يومي مكتمل");
    }

    private void switchTab(int index) {
        if (mContext == null && getActivity() instanceof UHFMainActivity) {
            mContext = (UHFMainActivity) getActivity();
        }
        
        if (mContext != null && mContext.mTabHost != null) {
            mContext.mTabHost.setCurrentTab(index);
        }
    }

    @Override 
    public void myOnKeyDwon() {}
    
    @Override 
    public void onDestroyView() {
        super.onDestroyView();
        // إيقاف الـ Handler عند تدمير الواجهة للحد من التسريب والانهيارات
        if (updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
    }
}
