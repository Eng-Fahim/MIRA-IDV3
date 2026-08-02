package com.mira.rfid.fragment;

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

    private volatile boolean loopFlag = false;

    private CheckBox cb_light_filter;
    private EditText etPtr_light_filter, etData_light_filter, etLen_light_filter;
    private RadioButton rbEPC_light_filter, rbTID_light_filter, rbUser_light_filter;
    private Button btn_light_single, btn_light_continuous;

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
        if (mContext == null) return;
        mContext.currentFragment = this;
        settingsManager = MiraSettingsManager.getInstance(mContext);

        if (getView() != null) {
            getView().post(() -> {
                if (!isAdded() || getActivity() == null || mContext == null) return;
                String selectItem = null;
                if (mContext.tagList != null && mContext.tagList.size() > mContext.selectIndex && mContext.selectIndex >= 0) {
                    selectItem = mContext.tagList.get(mContext.selectIndex).getEPC();
                }
                if (selectItem != null && !selectItem.equals("") && etData_light_filter != null && etLen_light_filter != null) {
                    etData_light_filter.setText(selectItem);
                    etLen_light_filter.setText(String.valueOf(selectItem.length() * 4));
                }
            });
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        if (mContext != null) {
            mContext.currentFragment = this;
        }

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

        if (rbEPC_light_filter != null) rbEPC_light_filter.setOnClickListener(this);
        if (rbTID_light_filter != null) rbTID_light_filter.setOnClickListener(this);
        if (rbUser_light_filter != null) rbUser_light_filter.setOnClickListener(this);

        // 🟢 بحث عن قطعة
        if (btnAlertSearch != null) {
            btnAlertSearch.setOnClickListener(v -> {
                if (etAlertSearch == null) return;
                String code = etAlertSearch.getText().toString().trim();
                if (TextUtils.isEmpty(code)) {
                    Toast.makeText(mContext, "أدخل Serial أو GTIN-13", Toast.LENGTH_SHORT).show();
                    return;
                }
                searchMiraItem(code);
            });
        }

        if (btn_light_single != null) {
            btn_light_single.setOnClickListener(v -> lightTag());
        }
        
        if (btn_light_continuous != null) {
            btn_light_continuous.setOnClickListener(v -> {
                if (loopFlag) {
                    stopContinuousAlert();
                } else {
                    startContinuousAlert();
                }
            });
        }

        if (cb_light_filter != null) {
            cb_light_filter.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (layoutFilterOptions != null) {
                    layoutFilterOptions.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                }
                
                if (isChecked && etData_light_filter != null) {
                    String data = etData_light_filter.getText().toString().trim();
                    String rex = "[\\da-fA-F]*";
                    if (data.isEmpty() || !data.matches(rex)) {
                        UIHelper.ToastMessage(mContext, getString(R.string.uhf_msg_filter_data_must_hex));
                        cb_light_filter.setChecked(false);
                    }
                }
            });
        }
    }

    // ============================================
    // 🟢 البحث في MIRA ID
    // ============================================
    private void searchMiraItem(String code) {
        if (layoutAlertItem != null) layoutAlertItem.setVisibility(View.GONE);
        if (tvAlertMessage != null) tvAlertMessage.setText("🔍 جاري البحث...");

        new Thread(() -> {
            try {
                String apiUrl = "https://ams.ibreg.org/wp-json/mira-gate/v1/authorize";
                String apiKey = "mira_gate_test071234567890abcdefghijklmnop";

                if (settingsManager != null) {
                    apiUrl = settingsManager.getString("mira_api_url", apiUrl);
                    apiKey = settingsManager.getString("mira_api_key", apiKey);
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
                    // حماية دورة الحياة لمنع الانهيار
                    if (!isAdded() || getActivity() == null) return;

                    if (item != null) {
                        if (layoutAlertItem != null) layoutAlertItem.setVisibility(View.VISIBLE);
                        if (tvAlertItemTitle != null) tvAlertItemTitle.setText("📦 " + item.optString("title", "غير معروف"));
                        if (tvAlertItemDetails != null) {
                            tvAlertItemDetails.setText("🏷️ " + item.optString("serial", code) +
                                " | 💎 " + item.optString("karat", "") +
                                " | ⚖️ " + item.optString("weight", "") + "g");
                        }

                        if (!allowed) {
                            if (tvAlertItemStatus != null) {
                                tvAlertItemStatus.setText("🚨 غير مصرح بالخروج!");
                                tvAlertItemStatus.setTextColor(Color.parseColor("#F44336"));
                            }
                            if (tvAlertMessage != null) tvAlertMessage.setText("⚠️ هذه القطعة غير مصرحة - استخدم الإنذار");
                        } else {
                            if (tvAlertItemStatus != null) {
                                tvAlertItemStatus.setText("✅ مصرح بالخروج");
                                tvAlertItemStatus.setTextColor(Color.parseColor("#4CAF50"));
                            }
                            if (tvAlertMessage != null) tvAlertMessage.setText("✅ القطعة مصرحة");
                        }

                        // تعبئة حقل EPC للفلتر
                        String serial = item.optString("serial", code);
                        if (etData_light_filter != null) etData_light_filter.setText(serial);
                        if (etLen_light_filter != null) etLen_light_filter.setText(String.valueOf(serial.length() * 4));
                    } else {
                        if (tvAlertMessage != null) tvAlertMessage.setText("❌ القطعة غير موجودة");
                        if (layoutAlertItem != null) layoutAlertItem.setVisibility(View.GONE);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Search error: " + e.getMessage());
                handler.post(() -> {
                    if (!isAdded() || getActivity() == null) return;
                    if (tvAlertMessage != null) tvAlertMessage.setText("❌ خطأ في الاتصال بالسيرفر");
                });
            }
        }).start();
    }

    // ============================================
    // 🟢 إنذار مستمر
    // ============================================
    private void startContinuousAlert() {
        loopFlag = true;
        isAlerting = true;
        if (btn_light_continuous != null) {
            btn_light_continuous.setText("⏹ إيقاف الإنذار");
            btn_light_continuous.setBackgroundColor(Color.parseColor("#4CAF50"));
        }
        if (tvAlertStatus != null) {
            tvAlertStatus.setText("🚨 إنذار مستمر");
            tvAlertStatus.setTextColor(Color.parseColor("#F44336"));
        }
        if (tvAlertIcon != null) tvAlertIcon.setVisibility(View.VISIBLE);
        if (tvAlertMessage != null) tvAlertMessage.setText("🚨 جاري الإنذار المستمر...");

        new Thread(() -> {
            while (loopFlag) {
                lightTag();
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    private void stopContinuousAlert() {
        loopFlag = false;
        isAlerting = false;
        if (btn_light_continuous != null) {
            btn_light_continuous.setText("🚨 إنذار مستمر");
            btn_light_continuous.setBackgroundColor(Color.parseColor("#F44336"));
        }
        if (tvAlertStatus != null) {
            tvAlertStatus.setText("⏸ متوقف");
            tvAlertStatus.setTextColor(Color.parseColor("#EF9A9A"));
        }
        if (tvAlertIcon != null) tvAlertIcon.setVisibility(View.GONE);
        if (tvAlertMessage != null) tvAlertMessage.setText("اضغط وميض أو إنذار مستمر");
    }

    private void lightTag() {
        if (mContext == null || mContext.mReader == null) return;

        boolean isFilterChecked = cb_light_filter != null && cb_light_filter.isChecked();

        if (isFilterChecked) {
            String ptrStr = etPtr_light_filter != null ? etPtr_light_filter.getText().toString() : "";
            String lenStr = etLen_light_filter != null ? etLen_light_filter.getText().toString() : "";
            String filterData = etData_light_filter != null ? etData_light_filter.getText().toString() : "";

            if (ptrStr.isEmpty() || lenStr.isEmpty() || filterData.isEmpty()) {
                handler.post(() -> {
                    if (!isAdded() || getActivity() == null) return;
                    Toast.makeText(mContext, "أكمل بيانات الفلتر", Toast.LENGTH_SHORT).show();
                });
                return;
            }

            try {
                int filterPtr = Integer.parseInt(ptrStr);
                int filterCnt = Integer.parseInt(lenStr);
                int filterBank = RFIDWithUHFUART.Bank_EPC;
                if (rbTID_light_filter != null && rbTID_light_filter.isChecked()) filterBank = RFIDWithUHFUART.Bank_TID;
                else if (rbUser_light_filter != null && rbUser_light_filter.isChecked()) filterBank = RFIDWithUHFUART.Bank_USER;

                mContext.mReader.readData("00000000", filterBank, filterPtr, filterCnt, filterData,
                        IUHF.Bank_RESERVED, 4, 1);
            } catch (Exception e) {
                Log.e(TAG, "Error executing readData: " + e.getMessage());
            }
        } else {
            mContext.mReader.readData("00000000", IUHF.Bank_RESERVED, 4, 1);
        }

        // 🟢 تأثير بصري آمن على خيط UI
        if (isAlerting) {
            handler.post(() -> {
                if (!isAdded() || getActivity() == null) return;
                if (tvAlertIcon != null) {
                    tvAlertIcon.setVisibility(tvAlertIcon.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                }
            });
        }
    }

    @Override
    public void onClick(View v) {
        if (v == null) return;
        int id = v.getId();
        if (id == R.id.rbEPC_light_filter) {
            if (etPtr_light_filter != null) etPtr_light_filter.setText("32");
        } else if (id == R.id.rbTID_light_filter || id == R.id.rbUser_light_filter) {
            if (etPtr_light_filter != null) etPtr_light_filter.setText("0");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (loopFlag) {
            stopContinuousAlert();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (loopFlag) {
            stopContinuousAlert();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (loopFlag) {
            stopContinuousAlert();
        }
    }
}
