package com.example.uhf.fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.mira.rfid.R; // ✅ صحيح

import com.example.uhf.activity.UHFMainActivity;
import com.example.uhf.manager.MiraSettingsManager;
import com.mira.ui.utils.UIHelper;
import com.example.uhf.view.CircleSeekBar;
import com.example.uhf.view.UhfLocationCanvasView;
import com.rscja.deviceapi.interfaces.IUHF;
import com.rscja.deviceapi.interfaces.IUHFLocationCallback;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * MIRA Locator Fragment
 * 
 * يبحث عن القطعة في MIRA ID أولاً، ثم يستخدم RFID Location لتحديد موقعها
 */
public class UHFLocationFragment extends KeyDwonFragment {

    String TAG = "UHF_LocationFragment";
    private UHFMainActivity mContext;
    private UhfLocationCanvasView llChart;
    private EditText etEPC;
    private Button btStart, btStop, btnMiraSearch;
    private CircleSeekBar seekBarPower;
    
    // 🟢 عناصر MIRA Locator الجديدة
    private LinearLayout layoutItemInfo, layoutSignal;
    private TextView tvLocatorStatus, tvItemTitle, tvItemDetails, tvItemLocation, tvItemStatus;
    private TextView tvSignalValue;
    private ProgressBar signalStrength;
    private View statusIndicator;
    
    private MiraSettingsManager settingsManager;
    private Handler handler = new Handler(Looper.getMainLooper());
    
    final int EPC = 2;
    int progress = 5;
    private String currentSerial = "";
    private String currentItemTitle = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_uhflocation, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        mContext.currentFragment = this;
        
        settingsManager = MiraSettingsManager.getInstance(mContext);
        
        llChart = mContext.findViewById(R.id.llChart);
        etEPC = mContext.findViewById(R.id.etEPC);
        btStart = mContext.findViewById(R.id.btStart);
        btStop = mContext.findViewById(R.id.btStop);
        
        // 🟢 عناصر MIRA
        btnMiraSearch = mContext.findViewById(R.id.btnMiraSearch);
        layoutItemInfo = mContext.findViewById(R.id.layoutItemInfo);
        layoutSignal = mContext.findViewById(R.id.layoutSignal);
        tvLocatorStatus = mContext.findViewById(R.id.tvLocatorStatus);
        tvItemTitle = mContext.findViewById(R.id.tvItemTitle);
        tvItemDetails = mContext.findViewById(R.id.tvItemDetails);
        tvItemLocation = mContext.findViewById(R.id.tvItemLocation);
        tvItemStatus = mContext.findViewById(R.id.tvItemStatus);
        tvSignalValue = mContext.findViewById(R.id.tvSignalValue);
        signalStrength = mContext.findViewById(R.id.signalStrength);
        statusIndicator = mContext.findViewById(R.id.statusIndicator);

        seekBarPower = getView().findViewById(R.id.seekBarPower);
        seekBarPower.setEnabled(false);
        seekBarPower.setProgress(5);
        seekBarPower.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress2, boolean fromUser) {
                progress = progress2;
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int p = 35 - progress;
                mContext.mReader.setDynamicDistance(p);
            }
        });

        // 🟢 زر البحث في MIRA
        btnMiraSearch.setOnClickListener(v -> {
            String code = etEPC.getText().toString().trim();
            if (TextUtils.isEmpty(code)) {
                Toast.makeText(mContext, "أدخل Serial أو GTIN-13", Toast.LENGTH_SHORT).show();
                return;
            }
            searchMiraItem(code);
        });

        btStart.setOnClickListener(v -> startLocation());
        btStop.setOnClickListener(v -> stopLocation());

        getView().post(() -> {
            llChart.clean();
            String selectItem = null;
            if (mContext.tagList.size() > mContext.selectIndex && mContext.selectIndex >= 0) {
                selectItem = mContext.tagList.get(mContext.selectIndex).getEPC();
            }
            if (selectItem != null) {
                etEPC.setText(selectItem);
            } else {
                etEPC.setText("");
            }
        });
    }

    // ============================================
    // 🟢 البحث عن القطعة في MIRA ID
    // ============================================
    private void searchMiraItem(String code) {
        tvLocatorStatus.setText("🔍 جاري البحث...");
        tvLocatorStatus.setTextColor(Color.parseColor("#FF9800"));
        layoutItemInfo.setVisibility(View.GONE);
        
        new Thread(() -> {
            try {
                String apiUrl = settingsManager.getString("mira_api_url", 
                    "https://ams.ibreg.org/wp-json/mira-gate/v1/authorize");
                String apiKey = settingsManager.getString("mira_api_key", 
                    "mira_gate_test071234567890abcdefghijklmnop");

                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("X-MIRA-API-Key", apiKey);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setDoOutput(true);

                JSONObject jsonParam = new JSONObject();
                jsonParam.put("epc", code);
                jsonParam.put("gate_id", "handheld_c72");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonParam.toString().getBytes("utf-8"));
                }

                int responseCode = conn.getResponseCode();
                InputStream is = (responseCode >= 200 && responseCode < 300) 
                    ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "utf-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);

                JSONObject json = new JSONObject(response.toString());
                JSONObject item = json.optJSONObject("item");
                JSONObject decision = json.optJSONObject("decision");
                JSONObject barcode = json.optJSONObject("barcode");

                handler.post(() -> {
                    if (item != null) {
                        boolean allowed = decision != null && decision.optBoolean("allowed", false);
                        
                        currentSerial = item.optString("serial", code);
                        currentItemTitle = item.optString("title", "غير معروف");
                        String karat = item.optString("karat", "");
                        String weight = item.optString("weight", "");
                        String location = item.optString("location", "غير محدد");
                        String status = item.optString("status", "");

                        // تحديث البطاقة
                        layoutItemInfo.setVisibility(View.VISIBLE);
                        tvItemTitle.setText("📦 " + currentItemTitle);
                        tvItemDetails.setText("🏷️ " + currentSerial + 
                            (karat.isEmpty() ? "" : " | 💎 " + karat) +
                            (weight.isEmpty() ? "" : " | ⚖️ " + weight + "g"));
                        tvItemLocation.setText("📍 " + location);
                        
                        String statusAr = status.equals("sold") ? "مباع" : 
                                         status.equals("available") ? "متاح" : status;
                        tvItemStatus.setText(statusAr);
                        
                        // تحديث المؤشر
                        if (allowed) {
                            statusIndicator.setBackgroundColor(Color.parseColor("#4CAF50"));
                            tvItemStatus.setBackgroundColor(Color.parseColor("#4CAF50"));
                        } else {
                            statusIndicator.setBackgroundColor(Color.parseColor("#F44336"));
                            tvItemStatus.setBackgroundColor(Color.parseColor("#F44336"));
                        }

                        tvLocatorStatus.setText("✅ تم العثور");
                        tvLocatorStatus.setTextColor(Color.parseColor("#4CAF50"));
                        
                        // تعبئة حقل EPC تلقائياً للبحث RFID
                        etEPC.setText(currentSerial);
                        
                    } else {
                        tvLocatorStatus.setText("⚠️ غير موجود");
                        tvLocatorStatus.setTextColor(Color.parseColor("#FF9800"));
                        layoutItemInfo.setVisibility(View.GONE);
                        Toast.makeText(mContext, "القطعة غير مسجلة في MIRA ID", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "MIRA search error: " + e.getMessage());
                handler.post(() -> {
                    tvLocatorStatus.setText("❌ خطأ اتصال");
                    tvLocatorStatus.setTextColor(Color.RED);
                });
            }
        }).start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopLocation();
    }

    @Override
    public void myOnKeyDwon() {
        if (btStart.isEnabled()) {
            startLocation();
        } else {
            stopLocation();
        }
    }

    private void startLocation() {
        String epc = etEPC.getText().toString();
        if (epc.equals("")) {
            UIHelper.ToastMessage(mContext, R.string.location_fail);
            return;
        }

        tvLocatorStatus.setText("📡 جاري تحديد الموقع...");
        tvLocatorStatus.setTextColor(Color.parseColor("#1A73E8"));
        layoutSignal.setVisibility(View.VISIBLE);

        boolean result = mContext.mReader.startLocation(mContext, epc, IUHF.Bank_EPC, 32, new IUHFLocationCallback() {
            @Override
            public void getLocationValue(int value, boolean valid) {
                llChart.setData(value);
                
                // 🟢 تحديث مؤشر قوة الإشارة
                int signalPercent = Math.min(100, Math.max(0, 100 - value));
                signalStrength.setProgress(signalPercent);
                tvSignalValue.setText("-" + value + " dBm");
                
                // 🟢 تغيير لون المؤشر حسب القرب
                if (value < 40) {
                    signalStrength.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50")));
                    tvLocatorStatus.setText("✅ قريب جداً - " + currentItemTitle);
                } else if (value < 60) {
                    signalStrength.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FF9800")));
                    tvLocatorStatus.setText("🔍 اقترب أكثر - " + currentItemTitle);
                } else {
                    signalStrength.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#F44336")));
                    tvLocatorStatus.setText("📡 بعيد - " + currentItemTitle);
                }
                
                if (valid) {
                    mContext.playSoundDelayed(value);
                }
            }
        });

        if (!result) {
            UIHelper.ToastMessage(mContext, R.string.psam_msg_fail);
            tvLocatorStatus.setText("❌ فشل تحديد الموقع");
            layoutSignal.setVisibility(View.GONE);
            return;
        }

        seekBarPower.setEnabled(true);
        btStart.setEnabled(false);
        etEPC.setEnabled(false);
    }

    public void stopLocation() {
        mContext.mReader.stopLocation();
        btStart.setEnabled(true);
        etEPC.setEnabled(true);
        seekBarPower.setEnabled(false);
        seekBarPower.setProgress(5);
        layoutSignal.setVisibility(View.GONE);
        tvLocatorStatus.setText("🔍 جاهز");
        tvLocatorStatus.setTextColor(Color.parseColor("#C8E6C9"));
    }
}
