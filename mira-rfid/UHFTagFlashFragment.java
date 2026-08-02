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
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.uhf.R;
import com.example.uhf.activity.UHFMainActivity;
import com.example.uhf.manager.MiraSettingsManager;
import com.example.uhf.tools.UIHelper;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.interfaces.IUHF;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * MIRA Alert Fragment
 * 
 * وميض التاقات للإنذار - مخصص للقطع غير المصرحة
 * - بحث في MIRA ID عن القطعة
 * - عرض حالة القطعة (مصرح/ممنوع)
 * - وميض التاق مرة واحدة أو بشكل مستمر
 */
public class UHFTagFlashFragment extends KeyDwonFragment implements View.OnClickListener {
    private static final String TAG = "UHFTagFlashFragment";
    private UHFMainActivity mContext;
    private MiraSettingsManager settingsManager;
    private Handler handler = new Handler(Looper.getMainLooper());

    private boolean loopFlag = false;

    CheckBox cb_light_filter;
    EditText etPtr_light_filter, etData_light_filter, etLen_light_filter;
    RadioButton rbEPC_light_filter, rbTID_light_filter, rbUser_light_filter;
    Button btn_light_single, btn_light_continuous;

    // 🟢 عناصر MIRA Alert
    private EditText etAlertSearch;
    private Button btnAlertSearch;
    private LinearLayout layoutAlertItem, layoutFilterOptions;
    private TextView tvAlertStatus, tvAlertItemTitle, tvAlertItemDetails, tvAlertItemStatus;
    private TextView tvAlertIcon, tvAlertMessage;
    private boolean isAlerting = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.uhf_light_fragment, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        mContext.currentFragment = this;
        settingsManager = MiraSettingsManager.getInstance(mContext);

        getView().post(() -> {
            String selectItem = null;
            if (mContext.tagList.size() > mContext.selectIndex && mContext.selectIndex >= 0) {
                selectItem = mContext.tagList.get(mContext.selectIndex).getEPC();
            }
            if (selectItem != null && !selectItem.equals("")) {
                etData_light_filter.setText(selectItem);
                etLen_light_filter.setText(String.valueOf(selectItem.length() * 4));
            }
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        mContext.currentFragment = this;

        // 🟢 عناصر MIRA
        etAlertSearch = view.findViewById(R.id.etAlertSearch);
        btnAlertSearch = view.findViewById(R.id.btnAlertSearch);
        layoutAlertItem = view.findViewById(R.id.layoutAlertItem);
        layoutFilterOptions = view.findViewById(R.id.layoutFilterOptions);
        tvAlertStatus = view.findViewById(R.id.tvAlertStatus);
        tvAlertItemTitle = view.findViewById(R.id.tvAlertItemTitle);
        tvAlertItemDetails = view.findViewById(R.id.tvAlertItemDetails);
        tvAlertItemStatus = view.findViewById(R.id.tvAlertItemStatus);
        tvAlertIcon = view.findViewById(R.id.tvAlertIcon);
        tvAlertMessage = view.findViewById(R.id.tvAlertMessage);

        cb_light_filter = view.findViewById(R.id.cb_light_filter);
        etPtr_light_filter = view.findViewById(R.id.etPtr_light_filter);
        etLen_light_filter = view.findViewById(R.id.etLen_light_filter);
        etData_light_filter = view.findViewById(R.id.etData_light_filter);
        rbEPC_light_filter = view.findViewById(R.id.rbEPC_light_filter);
        rbTID_light_filter = view.findViewById(R.id.rbTID_light_filter);
        rbUser_light_filter = view.findViewById(R.id.rbUser_light_filter);
        btn_light_single = view.findViewById(R.id.btn_light_single);
        btn_light_continuous = view.findViewById(R.id.btn_light_continuous);

        rbEPC_light_filter.setOnClickListener(this);
        rbTID_light_filter.setOnClickListener(this);
        rbUser_light_filter.setOnClickListener(this);

        // 🟢 بحث عن قطعة
        btnAlertSearch.setOnClickListener(v -> {
            String code = etAlertSearch.getText().toString().trim();
            if (TextUtils.isEmpty(code)) {
                Toast.makeText(mContext, "أدخل Serial أو GTIN-13", Toast.LENGTH_SHORT).show();
                return;
            }
            searchMiraItem(code);
        });

        btn_light_single.setOnClickListener(v -> lightTag());
        
        btn_light_continuous.setOnClickListener(v -> {
            if (loopFlag) {
                stopContinuousAlert();
            } else {
                startContinuousAlert();
            }
        });

        cb_light_filter.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutFilterOptions.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            
            if (isChecked) {
                String data = etData_light_filter.getText().toString().trim();
                String rex = "[\\da-fA-F]*";
                if (data == null || data.isEmpty() || !data.matches(rex)) {
                    UIHelper.ToastMessage(mContext, getString(R.string.uhf_msg_filter_data_must_hex));
                    cb_light_filter.setChecked(false);
                }
            }
        });
    }

    // ============================================
    // 🟢 البحث في MIRA ID
    // ============================================
    private void searchMiraItem(String code) {
        layoutAlertItem.setVisibility(View.GONE);
        tvAlertMessage.setText("🔍 جاري البحث...");

        new Thread(() -> {
            try {
                String apiUrl = settingsManager.getString("mira_api_url",
                    "https://ams.ibreg.org/wp-json/mira-gate/v1/authorize");
                String apiKey = settingsManager.getString("mira_api_key",
                    "mira_gate_test071234567890abcdefghijklmnop");

                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("X-MIRA-API-Key", apiKey);
                conn.setConnectTimeout(5000);
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
                boolean allowed = decision != null && decision.optBoolean("allowed", false);

                handler.post(() -> {
                    if (item != null) {
                        layoutAlertItem.setVisibility(View.VISIBLE);
                        tvAlertItemTitle.setText("📦 " + item.optString("title", "غير معروف"));
                        tvAlertItemDetails.setText("🏷️ " + item.optString("serial", code) +
                            " | 💎 " + item.optString("karat", "") +
                            " | ⚖️ " + item.optString("weight", "") + "g");

                        if (!allowed) {
                            tvAlertItemStatus.setText("🚨 غير مصرح بالخروج!");
                            tvAlertItemStatus.setTextColor(Color.parseColor("#F44336"));
                            tvAlertMessage.setText("⚠️ هذه القطعة غير مصرحة - استخدم الإنذار");
                        } else {
                            tvAlertItemStatus.setText("✅ مصرح بالخروج");
                            tvAlertItemStatus.setTextColor(Color.parseColor("#4CAF50"));
                            tvAlertMessage.setText("✅ القطعة مصرحة");
                        }

                        // تعبئة حقل EPC للفلتر
                        String serial = item.optString("serial", code);
                        etData_light_filter.setText(serial);
                        etLen_light_filter.setText(String.valueOf(serial.length() * 4));
                    } else {
                        tvAlertMessage.setText("❌ القطعة غير موجودة");
                        layoutAlertItem.setVisibility(View.GONE);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Search error: " + e.getMessage());
                handler.post(() -> tvAlertMessage.setText("❌ خطأ في البحث"));
            }
        }).start();
    }

    // ============================================
    // 🟢 إنذار مستمر
    // ============================================
    private void startContinuousAlert() {
        loopFlag = true;
        isAlerting = true;
        btn_light_continuous.setText("⏹ إيقاف الإنذار");
        btn_light_continuous.setBackgroundColor(Color.parseColor("#4CAF50"));
        tvAlertStatus.setText("🚨 إنذار مستمر");
        tvAlertStatus.setTextColor(Color.parseColor("#F44336"));
        tvAlertIcon.setVisibility(View.VISIBLE);
        tvAlertMessage.setText("🚨 جاري الإنذار المستمر...");

        new Thread(() -> {
            while (loopFlag) {
                lightTag();
                try { Thread.sleep(200); } catch (InterruptedException e) { e.printStackTrace(); }
            }
        }).start();
    }

    private void stopContinuousAlert() {
        loopFlag = false;
        isAlerting = false;
        btn_light_continuous.setText("🚨 إنذار مستمر");
        btn_light_continuous.setBackgroundColor(Color.parseColor("#F44336"));
        tvAlertStatus.setText("⏸ متوقف");
        tvAlertStatus.setTextColor(Color.parseColor("#EF9A9A"));
        tvAlertIcon.setVisibility(View.GONE);
        tvAlertMessage.setText("اضغط وميض أو إنذار مستمر");
    }

    private void lightTag() {
        if (cb_light_filter.isChecked()) {
            if (etPtr_light_filter.getText().toString().isEmpty() ||
                etLen_light_filter.getText().toString().isEmpty() ||
                etData_light_filter.getText().toString().isEmpty()) {
                handler.post(() -> Toast.makeText(mContext, "أكمل بيانات الفلتر", Toast.LENGTH_SHORT).show());
                return;
            }

            int filterPtr = Integer.parseInt(etPtr_light_filter.getText().toString());
            String filterData = etData_light_filter.getText().toString();
            int filterCnt = Integer.parseInt(etLen_light_filter.getText().toString());
            int filterBank = RFIDWithUHFUART.Bank_EPC;
            if (rbTID_light_filter.isChecked()) filterBank = RFIDWithUHFUART.Bank_TID;
            else if (rbUser_light_filter.isChecked()) filterBank = RFIDWithUHFUART.Bank_USER;

            mContext.mReader.readData("00000000", filterBank, filterPtr, filterCnt, filterData,
                    IUHF.Bank_RESERVED, 4, 1);
        } else {
            mContext.mReader.readData("00000000", IUHF.Bank_RESERVED, 4, 1);
        }

        // 🟢 تأثير بصري
        if (isAlerting) {
            handler.post(() -> {
                tvAlertIcon.setVisibility(tvAlertIcon.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            });
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.rbEPC_light_filter:
                etPtr_light_filter.setText("32");
                break;
            case R.id.rbTID_light_filter:
                etPtr_light_filter.setText("0");
                break;
            case R.id.rbUser_light_filter:
                etPtr_light_filter.setText("0");
                break;
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (loopFlag) {
            stopContinuousAlert();
        }
    }
}
