package com.example.uhf.fragment;

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

import com.example.uhf.R;
import com.example.uhf.activity.UHFMainActivity;
import com.example.uhf.tools.UIHelper;
import com.example.uhf.view.CircleSeekBar;
import com.example.uhf.view.RadarView;
import com.rscja.deviceapi.entity.RadarLocationEntity;
import com.rscja.deviceapi.interfaces.IUHF;
import com.rscja.deviceapi.interfaces.IUHFRadarLocationCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class UHFRadarLocationFragment extends KeyDwonFragment {

    public final String TAG = "UHFRadarLocationFrag";
    private UHFMainActivity mContext;

    private RadarView radarView;
    private EditText etEPC;
    private Button btStart;
    private Button btStop;
    private CircleSeekBar seekBarPower;
    private boolean isSingleLabel = false;
    private boolean inventoryFlag = false;
    private String targetEpc;
    int progress = 5;

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
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        mContext.currentFragment = this;

        radarView = getView().findViewById(R.id.radarView);
        etEPC = getView().findViewById(R.id.etRadarEPC);
        btStart = getView().findViewById(R.id.btRadarStart);
        btStop = getView().findViewById(R.id.btRadarStop);
        seekBarPower = getView().findViewById(R.id.seekBarPower);
        
        // 🟢 ربط عناصر MIRA Radar الجديدة
        layoutMiraRadarInfo = getView().findViewById(R.id.layoutMiraRadarInfo);
        tvRadarItemStatus = getView().findViewById(R.id.tvRadarItemStatus);
        tvRadarItemName = getView().findViewById(R.id.tvRadarItemName);
        tvRadarItemDetails = getView().findViewById(R.id.tvRadarItemDetails);
        tvAuthorizedCount = getView().findViewById(R.id.tvAuthorizedCount);
        tvDeniedCount = getView().findViewById(R.id.tvDeniedCount);
        tvUnknownCount = getView().findViewById(R.id.tvUnknownCount);
        
        seekBarPower.setEnabled(false);
        seekBarPower.setProgress(5);
        
        btStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startLocated();
            }
        });
        btStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopLocated();
            }
        });

        getView().post(new Runnable() {
            @Override
            public void run() {
                String selectItem = null;
                if (mContext.tagList.size() > mContext.selectIndex && mContext.selectIndex >= 0) {
                    selectItem = mContext.tagList.get(mContext.selectIndex).getEPC();
                }
                if (selectItem != null && !selectItem.equals("")) {
                    etEPC.setText(selectItem);
                    targetEpc = selectItem;
                } else {
                    etEPC.setText("");
                }
            }
        });
    }

    // 🟢 استعلام MIRA API عن قطعة محددة
    private void queryMiraItem(final String epc) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL("https://ams.ibreg.org/wp-json/mira-gate/v1/authorize");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    conn.setRequestProperty("X-MIRA-API-Key", "mira_gate_test071234567890abcdefghijklmnop");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.setDoOutput(true);

                    JSONObject jsonParam = new JSONObject();
                    jsonParam.put("epc", epc);
                    jsonParam.put("gate_id", "handheld_c72");

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
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateMiraRadarInfo(epc, decision, item);
                            }
                        });
                    }

                } catch (Exception e) {
                    Log.e(TAG, "MIRA query error: " + e.getMessage());
                }
            }
        }).start();
    }

    // 🟢 تحديث بطاقة معلومات MIRA Radar
    private void updateMiraRadarInfo(String epc, JSONObject decision, JSONObject item) {
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

        // تحديث العداد
        if (tvAuthorizedCount != null) tvAuthorizedCount.setText(String.valueOf(authorizedCount));
        if (tvDeniedCount != null) tvDeniedCount.setText(String.valueOf(deniedCount));
        if (tvUnknownCount != null) tvUnknownCount.setText(String.valueOf(unknownCount));

        // حالة القطعة
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

        // اسم القطعة
        if (tvRadarItemName != null) {
            tvRadarItemName.setText("📦 " + itemTitle);
        }

        // تفاصيل إضافية
        if (tvRadarItemDetails != null) {
            StringBuilder details = new StringBuilder();
            details.append("🏷️ ").append(epc);
            if (!karat.isEmpty()) details.append(" | 💎 ").append(karat);
            if (!weight.isEmpty()) details.append(" | ⚖️ ").append(weight).append("g");
            if (!status.isEmpty()) details.append(" | 📋 ").append(status);
            tvRadarItemDetails.setText(details.toString());
        }
    }

    Toast toast = null;

    private void showMSG(int power) {
        if (toast == null) {
            toast = new Toast(mContext);
        }
        toast.cancel();
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setText("الطاقة:" + power);
        toast.show();
    }

    private Handler handler = new Handler(Looper.getMainLooper());

    @SuppressLint("LongLogTag")
    private void startLocated() {
        if (inventoryFlag) return;

        radarView.clearPanel();
        targetEpc = etEPC.getText().toString();
        
        // 🟢 إعادة تعيين العدادات
        authorizedCount = 0;
        deniedCount = 0;
        unknownCount = 0;
        if (tvAuthorizedCount != null) tvAuthorizedCount.setText("0");
        if (tvDeniedCount != null) tvDeniedCount.setText("0");
        if (tvUnknownCount != null) tvUnknownCount.setText("0");

        boolean result = mContext.mReader.startRadarLocation(mContext, targetEpc, IUHF.Bank_EPC, 32, new IUHFRadarLocationCallback() {
            @Override
            public void getLocationValue(final List<RadarLocationEntity> list) {
                radarView.bindingData(list, targetEpc);

                // 🟢 استعلام MIRA عن أول قطعة في القائمة
                if (!list.isEmpty() && !TextUtils.isEmpty(targetEpc)) {
                    for (RadarLocationEntity entity : list) {
                        if (entity.getTag().equals(targetEpc)) {
                            queryMiraItem(targetEpc);
                            mContext.playSoundDelayed(entity.getValue());
                            break;
                        }
                    }
                } else if (!TextUtils.isEmpty(targetEpc)) {
                    // قطعة محددة غير موجودة في النطاق
                    queryMiraItem(targetEpc);
                }

                if (!TextUtils.isEmpty(targetEpc)) {
                    for (int k = 0; k < list.size(); k++) {
                        if (list.get(k).getTag().equals(targetEpc)) {
                            mContext.playSoundDelayed(list.get(k).getValue());
                        }
                    }
                } else {
                    mContext.playSound(1);
                }
            }

            @Override
            public void getAngleValue(int angle) {
                radarView.setRotation(-angle);
            }
        });
        
        if (!result) {
            UIHelper.ToastMessage(mContext, "فشل البدء");
            return;
        }

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
        
        seekBarPower.setEnabled(true);
        inventoryFlag = true;
        btStart.setEnabled(false);
        etEPC.setEnabled(false);
        radarView.startRadar();
        
        Log.i(TAG, "startLocated success");
    }

    @SuppressLint("LongLogTag")
    private void stopLocated() {
        if (!inventoryFlag) return;
        radarView.stopRadar();

        boolean result = mContext.mReader.stopRadarLocation();
        if (!result) {
            Log.e(TAG, "stopLocated failure");
            mContext.playSound(2);
            Toast.makeText(mContext, R.string.uhf_msg_inventory_stop_fail, Toast.LENGTH_SHORT).show();
        } else {
            Log.i(TAG, "stopLocated success");
            inventoryFlag = false;
            btStart.setEnabled(true);
            etEPC.setEnabled(true);
        }
        seekBarPower.setOnSeekBarChangeListener(null);
        seekBarPower.setProgress(5);
        seekBarPower.setEnabled(false);
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
    }

    @Override
    public void onPause() {
        super.onPause();
        stopLocated();
        radarView.stopRadar();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
        fragmentTransaction.detach(this).attach(this).commit();

        getView().post(new Runnable() {
            @Override
            public void run() {
                String selectItem = null;
                if (mContext.tagList.size() > mContext.selectIndex && mContext.selectIndex >= 0) {
                    selectItem = mContext.tagList.get(mContext.selectIndex).getEPC();
                }
                if (selectItem != null && !selectItem.equals("")) {
                    etEPC.setText(selectItem);
                    targetEpc = selectItem;
                } else {
                    etEPC.setText("");
                }
            }
        });
    }
}
