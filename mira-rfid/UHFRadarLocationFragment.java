package com.mira.rfid;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
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
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.FragmentTransaction;

// 🔗 استيراد الموارد والأنشطة والعناصر الخارجية
import com.mira.rfid.R; // ✅ صحيح

import com.example.uhf.activity.UHFMainActivity;
import com.mira.rfid.KeyDwonFragment; // ✅ تغيير المسار; // أو استبدالها بـ Fragment عادية إن تعذر الاستيراد
import com.example.uhf.manager.MiraSettingsManager;
import com.mira.ui.utils.UIHelper;
import com.example.uhf.view.CircleSeekBar;
import com.example.uhf.view.RadarView;

import com.rscja.deviceapi.entity.RadarLocationEntity;
import com.rscja.deviceapi.interfaces.IUHF;
import com.rscja.deviceapi.interfaces.IUHFRadarLocationCallback;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * MIRA Radar Location Fragment
 * 
 * يجمع بين:
 * - رادار تحديد موقع Chainway UHF
 * - استعلام MIRA ID عن القطع
 * - بطاقة معلومات القطعة
 * - عداد إحصائيات (مصرح/ممنوع/غير معروف)
 * - وضع محاكاة للتطوير
 * - تطبيق فوري للإعدادات من MiraSettingsManager
 */
public class UHFRadarLocationFragment extends KeyDwonFragment {

    public final String TAG = "UHFRadarLocationFrag";
    private UHFMainActivity mContext;

    private RadarView radarView;
    private EditText etEPC;
    private Button btStart;
    private Button btStop;
    private CircleSeekBar seekBarPower;
    private boolean inventoryFlag = false;
    private String targetEpc;
    int progress = 5;

    // 🟢 مدير الإعدادات
    private MiraSettingsManager settingsManager;
    private boolean simulationMode;

    // 🟢 MIRA Radar - عناصر جديدة
    private LinearLayout layoutMiraRadarInfo;
    private TextView tvRadarItemStatus, tvRadarItemName, tvRadarItemDetails;
    private TextView tvAuthorizedCount, tvDeniedCount, tvUnknownCount;
    
    private int authorizedCount = 0;
    private int deniedCount = 0;
    private int unknownCount = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_uhf_radar_location, container, false);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        if (mContext != null) {
            mContext.currentFragment = this;
            // 🟢 تهيئة مدير الإعدادات
            settingsManager = MiraSettingsManager.getInstance(mContext);
            simulationMode = settingsManager.getBoolean("radar_simulation", true);
        }

        radarView = view.findViewById(R.id.radarView);
        etEPC = view.findViewById(R.id.etRadarEPC);
        btStart = view.findViewById(R.id.btRadarStart);
        btStop = view.findViewById(R.id.btRadarStop);
        seekBarPower = view.findViewById(R.id.seekBarPower);
        
        // 🟢 ربط عناصر MIRA Radar الجديدة
        layoutMiraRadarInfo = view.findViewById(R.id.layoutMiraRadarInfo);
        tvRadarItemStatus = view.findViewById(R.id.tvRadarItemStatus);
        tvRadarItemName = view.findViewById(R.id.tvRadarItemName);
        tvRadarItemDetails = view.findViewById(R.id.tvRadarItemDetails);
        tvAuthorizedCount = view.findViewById(R.id.tvAuthorizedCount);
        tvDeniedCount = view.findViewById(R.id.tvDeniedCount);
        tvUnknownCount = view.findViewById(R.id.tvUnknownCount);
        
        if (seekBarPower != null) {
            seekBarPower.setEnabled(false);
            seekBarPower.setProgress(5);
        }
        
        if (btStart != null) {
            btStart.setOnClickListener(v -> startLocated());
        }
        if (btStop != null) {
            btStop.setOnClickListener(v -> stopLocated());
        }

        // 🟢 الاستماع لتغييرات الإعدادات
        if (settingsManager != null) {
            settingsManager.registerListener("radar_fragment", (key, value) -> {
                if ("radar_simulation".equals(key)) {
                    simulationMode = (Boolean) value;
                    if (inventoryFlag) {
                        stopLocated();
                        startLocated();
                    }
                }
            });
        }

        view.post(() -> {
            if (mContext != null && mContext.tagList != null) {
                String selectItem = null;
                if (mContext.tagList.size() > mContext.selectIndex && mContext.selectIndex >= 0) {
                    selectItem = mContext.tagList.get(mContext.selectIndex).getEPC();
                }
                if (selectItem != null && !selectItem.isEmpty()) {
                    etEPC.setText(selectItem);
                    targetEpc = selectItem;
                } else {
                    etEPC.setText("");
                }
            }
        });
    }

    // =============================================
    // 🟢 استعلام MIRA API عن قطعة محددة
    // =============================================
    private void queryMiraItem(final String epc) {
        if (settingsManager != null && !settingsManager.getBoolean("auto_query_mira", true)) {
            return;
        }

        new Thread(() -> {
            try {
                String apiUrl = "https://ams.ibreg.org/wp-json/mira-gate/v1/authorize";
                String apiKey = "mira_gate_test071234567890abcdefghijklmnop";
                String gateId = "handheld_c72";

                if (settingsManager != null) {
                    apiUrl = settingsManager.getString("mira_api_url", apiUrl);
                    apiKey = settingsManager.getString("mira_api_key", apiKey);
                    gateId = settingsManager.getString("mira_gate_id", gateId);
                }

                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("X-MIRA-API-Key", apiKey);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setDoOutput(true);

                JSONObject jsonParam = new JSONObject();
                jsonParam.put("epc", epc);
                jsonParam.put("gate_id", gateId);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonParam.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                final int responseCode = conn.getResponseCode();
                InputStream is = (responseCode >= 200 && responseCode < 300) 
                    ? conn.getInputStream() 
                    : conn.getErrorStream();

                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "utf-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line.trim());
                }

                final JSONObject jsonResponse = new JSONObject(response.toString());
                final JSONObject decision = jsonResponse.optJSONObject("decision");
                final JSONObject item = jsonResponse.optJSONObject("item");

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> updateMiraRadarInfo(epc, decision, item));
                }

            } catch (Exception e) {
                Log.e(TAG, "MIRA query error: " + e.getMessage());
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (tvRadarItemStatus != null) {
                            tvRadarItemStatus.setText("⚠️ خطأ في الاتصال بـ MIRA");
                            tvRadarItemStatus.setTextColor(Color.parseColor("#FF9800"));
                        }
                        if (layoutMiraRadarInfo != null) {
                            layoutMiraRadarInfo.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }
        }).start();
    }

    // =============================================
    // 🟢 تحديث بطاقة معلومات MIRA Radar
    // =============================================
    private void updateMiraRadarInfo(String epc, JSONObject decision, JSONObject item) {
        if (settingsManager != null && !settingsManager.getBoolean("show_mira_card", true)) {
            if (layoutMiraRadarInfo != null) {
                layoutMiraRadarInfo.setVisibility(View.GONE);
            }
            return;
        }

        if (layoutMiraRadarInfo != null) {
            layoutMiraRadarInfo.setVisibility(View.VISIBLE);
        }

        boolean allowed = decision != null && decision.optBoolean("allowed", false);
        String decisionMessage = decision != null ? decision.optString("message", "") : "";
        String itemTitle = item != null ? item.optString("title", "غير معروف") : "غير معروف";
        String karat = item != null ? item.optString("karat", "") : "";
        String weight = item != null ? item.optString("weight", "") : "";
        String status = item != null ? item.optString("status", "") : "";

        // تحديث الإحصائيات
        if (item != null && allowed) {
            authorizedCount++;
        } else if (item != null && !allowed) {
            deniedCount++;
        } else {
            unknownCount++;
        }

        if (tvAuthorizedCount != null) tvAuthorizedCount.setText(String.valueOf(authorizedCount));
        if (tvDeniedCount != null) tvDeniedCount.setText(String.valueOf(deniedCount));
        if (tvUnknownCount != null) tvUnknownCount.setText(String.valueOf(unknownCount));

        if (tvRadarItemStatus != null) {
            if (allowed && item != null) {
                tvRadarItemStatus.setText("✅ خروج مصرح");
                tvRadarItemStatus.setTextColor(Color.parseColor("#4CAF50"));
            } else if (!allowed && item != null) {
                tvRadarItemStatus.setText("🚨 غير مصرح بالخروج");
                tvRadarItemStatus.setTextColor(Color.parseColor("#F44336"));
            } else {
                tvRadarItemStatus.setText("⚠️ " + decisionMessage);
                tvRadarItemStatus.setTextColor(Color.parseColor("#FF9800"));
            }
        }

        if (tvRadarItemName != null) {
            tvRadarItemName.setText("📦 " + itemTitle);
        }

        if (tvRadarItemDetails != null) {
            StringBuilder details = new StringBuilder();
            details.append("🏷️ ").append(epc);
            if (!karat.isEmpty()) details.append(" | 💎 ").append(karat);
            if (!weight.isEmpty()) details.append(" | ⚖️ ").append(weight).append("g");
            if (!status.isEmpty()) details.append(" | 📋 ").append(status);
            tvRadarItemDetails.setText(details.toString());
        }
    }

    // =============================================
    // 🟢 دالة البدء
    // =============================================
    @SuppressLint("LongLogTag")
    private void startLocated() {
        if (inventoryFlag) return;

        if (radarView != null) radarView.clearPanel();
        if (etEPC != null) targetEpc = etEPC.getText().toString();
        
        authorizedCount = 0;
        deniedCount = 0;
        unknownCount = 0;
        if (tvAuthorizedCount != null) tvAuthorizedCount.setText("0");
        if (tvDeniedCount != null) tvDeniedCount.setText("0");
        if (tvUnknownCount != null) tvUnknownCount.setText("0");
        
        if (layoutMiraRadarInfo != null) layoutMiraRadarInfo.setVisibility(View.GONE);

        if (simulationMode) {
            startSimulatedRadar();
            return;
        }

        if (mContext == null || mContext.mReader == null) {
            Toast.makeText(getContext(), "Hardware Reader non-initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean result = mContext.mReader.startRadarLocation(mContext, targetEpc, IUHF.Bank_EPC, 32, new IUHFRadarLocationCallback() {
            @Override
            public void getLocationValue(final List<RadarLocationEntity> list) {
                if (radarView != null) radarView.bindingData(list, targetEpc);
                
                if (!TextUtils.isEmpty(targetEpc)) {
                    for (int k = 0; k < list.size(); k++) {
                        if (list.get(k).getTag().equals(targetEpc)) {
                            queryMiraItem(targetEpc);
                            if (mContext != null) mContext.playSoundDelayed(list.get(k).getValue());
                        }
                    }
                } else {
                    if (mContext != null) mContext.playSound(1);
                }
            }

            @Override
            public void getAngleValue(int angle) {
                if (radarView != null) radarView.setRotation(-angle);
            }
        });
        
        if (!result) {
            if (!TextUtils.isEmpty(targetEpc)) {
                queryMiraItem(targetEpc);
                Toast.makeText(mContext, "⚠️ الرادار غير مدعوم - تم جلب معلومات MIRA فقط", Toast.LENGTH_LONG).show();
            } else {
                if (mContext != null) UIHelper.ToastMessage(mContext, "فشل البدء - أدخل EPC صحيح");
            }
            return;
        }

        if (seekBarPower != null) {
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
                    if (mContext != null && mContext.mReader != null) {
                        mContext.mReader.setDynamicDistance(p);
                    }
                }
            });
            seekBarPower.setEnabled(true);
        }
        
        inventoryFlag = true;
        if (btStart != null) btStart.setEnabled(false);
        if (etEPC != null) etEPC.setEnabled(false);
        if (radarView != null) radarView.startRadar();
        
        Log.i(TAG, "startLocated success");
    }

    // =============================================
    // 🟢 محاكي الرادار للتطوير والاختبار
    // =============================================
    private final Handler simulationHandler = new Handler(Looper.getMainLooper());
    private Runnable simulationRunnable;
    private float simulationAngle = 0;

    private void startSimulatedRadar() {
        final String epcToTrack = targetEpc;
        
        queryMiraItem(epcToTrack);
        
        if (radarView != null) radarView.startRadar();
        if (seekBarPower != null) seekBarPower.setEnabled(true);
        inventoryFlag = true;
        if (btStart != null) btStart.setEnabled(false);
        if (etEPC != null) etEPC.setEnabled(false);
        
        simulationRunnable = new Runnable() {
            @Override
            public void run() {
                if (!inventoryFlag) return;
                
                simulationAngle += 5;
                if (simulationAngle >= 360) simulationAngle = 0;
                
                List<RadarLocationEntity> simulatedList = new ArrayList<>();
                if (radarView != null) {
                    radarView.bindingData(simulatedList, epcToTrack);
                    radarView.setRotation(-simulationAngle);
                }
                
                if (settingsManager != null && settingsManager.getBoolean("sound_on_scan", true)) {
                    if (mContext != null) mContext.playSound(1);
                }
                
                simulationHandler.postDelayed(this, 1500);
            }
        };
        simulationHandler.post(simulationRunnable);
        
        Toast.makeText(getContext(), "⚡ وضع المحاكاة", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Simulated radar started");
    }

    // =============================================
    // 🟢 دالة الإيقاف
    // =============================================
    @SuppressLint("LongLogTag")
    private void stopLocated() {
        if (!inventoryFlag) return;

        if (simulationMode) {
            if (simulationRunnable != null) {
                simulationHandler.removeCallbacks(simulationRunnable);
            }
        } else {
            if (mContext != null && mContext.mReader != null) {
                boolean result = mContext.mReader.stopRadarLocation();
                if (!result) {
                    Log.e(TAG, "stopLocated failure");
                    mContext.playSound(2);
                    Toast.makeText(mContext, R.string.uhf_msg_inventory_stop_fail, Toast.LENGTH_SHORT).show();
                }
            }
        }
        
        if (radarView != null) radarView.stopRadar();
        inventoryFlag = false;
        if (btStart != null) btStart.setEnabled(true);
        if (etEPC != null) etEPC.setEnabled(true);
        if (seekBarPower != null) {
            seekBarPower.setOnSeekBarChangeListener(null);
            seekBarPower.setProgress(5);
            seekBarPower.setEnabled(false);
        }
        
        Log.i(TAG, "stopLocated success");
    }

    @Override
    public void myOnKeyDwon() {
        if (inventoryFlag) {
            stopLocated();
        } else {
            startLocated();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (settingsManager != null) {
            simulationMode = settingsManager.getBoolean("radar_simulation", true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopLocated();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (settingsManager != null) {
            settingsManager.unregisterListener("radar_fragment");
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (getFragmentManager() != null) {
            FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
            fragmentTransaction.detach(this).attach(this).commit();
        }

        if (getView() != null) {
            getView().post(() -> {
                if (mContext != null && mContext.tagList != null) {
                    String selectItem = null;
                    if (mContext.tagList.size() > mContext.selectIndex && mContext.selectIndex >= 0) {
                        selectItem = mContext.tagList.get(mContext.selectIndex).getEPC();
                    }
                    if (selectItem != null && !selectItem.isEmpty()) {
                        if (etEPC != null) etEPC.setText(selectItem);
                        targetEpc = selectItem;
                    } else {
                        if (etEPC != null) etEPC.setText("");
                    }
                }
            });
        }
    }
}
