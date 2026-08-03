package com.example.uhf.fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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

import com.mira.rfid.R; // ✅ صحيح

import com.example.uhf.activity.UHFMainActivity;
import com.example.uhf.manager.MiraSettingsManager;
import com.mira.ui.utils.StringUtils;
import com.mira.ui.utils.UIHelper;
import com.rscja.deviceapi.RFIDWithUHFUART;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * MIRA Deactivate Fragment (KILL)
 * 
 * تعطيل تاق RFID نهائياً مع تحديث MIRA ID
 */
public class UHFKillFragment extends KeyDwonFragment implements View.OnClickListener {

    private static final String TAG = "UHFKillFragment";
    private UHFMainActivity mContext;
    private MiraSettingsManager settingsManager;

    EditText EtAccessPwd_Write;
    Button btnKill;

    LinearLayout llFilter;
    CheckBox cb_filter;
    EditText etPtr_filter, etLen_filter, etData_filter;
    RadioButton rbEPC_filter, rbTID_filter, rbUser_filter;

    // 🟢 عناصر MIRA
    private EditText etDeactivateSearch;
    private Button btnDeactivateSearch;
    private LinearLayout layoutDeactivateItem;
    private TextView tvDeactivateStatus, tvDeactivateItemTitle, tvDeactivateItemDetails, tvDeactivateItemRfidStatus;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.uhf_kill_fragment, container, false);
        inits(view);
        return view;
    }

    private void inits(View parent) {
        EtAccessPwd_Write = parent.findViewById(R.id.EtAccessPwd_Write);
        btnKill = parent.findViewById(R.id.btnKill);

        llFilter = parent.findViewById(R.id.llFilter);
        cb_filter = parent.findViewById(R.id.cb_filter);
        etPtr_filter = parent.findViewById(R.id.etPtr_filter);
        etLen_filter = parent.findViewById(R.id.etLen_filter);
        etData_filter = parent.findViewById(R.id.etData_filter);
        rbEPC_filter = parent.findViewById(R.id.rbEPC_filter);
        rbTID_filter = parent.findViewById(R.id.rbTID_filter);
        rbUser_filter = parent.findViewById(R.id.rbUser_filter);

        // 🟢 عناصر MIRA
        etDeactivateSearch = parent.findViewById(R.id.etDeactivateSearch);
        btnDeactivateSearch = parent.findViewById(R.id.btnDeactivateSearch);
        layoutDeactivateItem = parent.findViewById(R.id.layoutDeactivateItem);
        tvDeactivateStatus = parent.findViewById(R.id.tvDeactivateStatus);
        tvDeactivateItemTitle = parent.findViewById(R.id.tvDeactivateItemTitle);
        tvDeactivateItemDetails = parent.findViewById(R.id.tvDeactivateItemDetails);
        tvDeactivateItemRfidStatus = parent.findViewById(R.id.tvDeactivateItemRfidStatus);

        cb_filter.setOnClickListener(this);
        rbEPC_filter.setOnClickListener(this);
        rbTID_filter.setOnClickListener(this);
        rbUser_filter.setOnClickListener(this);

        etData_filter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                etLen_filter.setText(String.valueOf(s.toString().trim().length() * 4));
            }
        });

        cb_filter.setOnCheckedChangeListener((buttonView, isChecked) -> {
            llFilter.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // 🟢 بحث MIRA
        btnDeactivateSearch.setOnClickListener(v -> {
            String code = etDeactivateSearch.getText().toString().trim();
            if (code.isEmpty()) {
                Toast.makeText(mContext, "أدخل Serial أو GTIN", Toast.LENGTH_SHORT).show();
                return;
            }
            searchMiraItem(code);
        });

        btnKill.setOnClickListener(new btnKillOnClickListener());
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        mContext.currentFragment = this;
        settingsManager = MiraSettingsManager.getInstance(mContext);
    }

    // ============================================
    // 🟢 بحث MIRA
    // ============================================
    private void searchMiraItem(String code) {
        tvDeactivateStatus.setText("🔍 جاري البحث...");
        tvDeactivateStatus.setTextColor(Color.parseColor("#FF9800"));
        layoutDeactivateItem.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                String apiUrl = settingsManager.getString("mira_api_url",
                    "https://ams.ibreg.org/wp-json/mira-gate/v1/item/" + code);
                String apiKey = settingsManager.getString("mira_api_key",
                    "mira_gate_test071234567890abcdefghijklmnop");

                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("X-MIRA-API-Key", apiKey);
                conn.setConnectTimeout(5000);

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);

                    JSONObject json = new JSONObject(response.toString());
                    JSONObject item = json.optJSONObject("item");
                    JSONObject barcode = json.optJSONObject("barcode");

                    getActivity().runOnUiThread(() -> {
                        if (item != null || barcode != null) {
                            layoutDeactivateItem.setVisibility(View.VISIBLE);
                            String title = item != null ? item.optString("title", "غير معروف") : "باركود";
                            String serial = barcode != null ? barcode.optString("serial_no", code) :
                                           item != null ? item.optString("serial", code) : code;
                            String rfidStatus = barcode != null ? barcode.optString("rfid_status", "active") : "active";

                            tvDeactivateItemTitle.setText("📦 " + title);
                            tvDeactivateItemDetails.setText("🏷️ " + serial);
                            tvDeactivateItemRfidStatus.setText("📡 RFID: " + 
                                (rfidStatus.equals("deactivated") ? "معطل ⚠️" : "نشط ✅"));
                            tvDeactivateItemRfidStatus.setTextColor(
                                rfidStatus.equals("deactivated") ? Color.parseColor("#F44336") : Color.parseColor("#81C784"));

                            etData_filter.setText(serial);
                            etLen_filter.setText(String.valueOf(serial.length() * 4));
                            cb_filter.setChecked(true);

                            tvDeactivateStatus.setText("✅ تم العثور");
                            tvDeactivateStatus.setTextColor(Color.parseColor("#4CAF50"));
                        } else {
                            tvDeactivateStatus.setText("❌ غير موجود");
                            tvDeactivateStatus.setTextColor(Color.RED);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Search error: " + e.getMessage());
            }
        }).start();
    }

    // ============================================
    // 🟢 تسجيل التعطيل في MIRA ID
    // ============================================
    private void notifyMiraDeactivate(String serial) {
        new Thread(() -> {
            try {
                String apiKey = settingsManager.getString("mira_api_key",
                    "mira_gate_test071234567890abcdefghijklmnop");

                URL url = new URL("https://ams.ibreg.org/wp-json/mira-gate/v1/deactivate");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("X-MIRA-API-Key", apiKey);
                conn.setDoOutput(true);

                JSONObject json = new JSONObject();
                json.put("serial", serial);
                json.put("gate_id", "handheld_c72");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.toString().getBytes("utf-8"));
                }

                int code = conn.getResponseCode();
                Log.d(TAG, "Deactivate notify: " + code);

            } catch (Exception e) {
                Log.e(TAG, "Notify error: " + e.getMessage());
            }
        }).start();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.cb_filter:
                llFilter.setVisibility(cb_filter.isChecked() ? View.VISIBLE : View.GONE);
                break;
            case R.id.rbEPC_filter: etPtr_filter.setText("32"); break;
            case R.id.rbTID_filter: etPtr_filter.setText("0"); break;
            case R.id.rbUser_filter: etPtr_filter.setText("0"); break;
        }
    }

    public class btnKillOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            String strPWD = EtAccessPwd_Write.getText().toString().trim();

            if (StringUtils.isNotEmpty(strPWD)) {
                if (strPWD.length() != 8) {
                    UIHelper.ToastMessage(mContext, R.string.uhf_msg_addr_must_len8);
                    return;
                } else if (!mContext.vailHexInput(strPWD)) {
                    UIHelper.ToastMessage(mContext, R.string.rfid_mgs_error_nohex);
                    return;
                }
            } else {
                UIHelper.ToastMessage(mContext, R.string.rfid_mgs_error_nopwd);
                return;
            }

            boolean result;
            String serial = etData_filter.getText().toString().trim();

            if (cb_filter.isChecked()) {
                int filterPtr = StringUtils.toInt(etPtr_filter.getText().toString(), -1);
                int filterCnt = StringUtils.toInt(etLen_filter.getText().toString(), -1);
                String filterData = etData_filter.getText().toString();
                int filterBank = getFilterBank();

                result = mContext.mReader.killTag(strPWD, filterBank, filterPtr, filterCnt, filterData);
            } else {
                result = mContext.mReader.killTag(strPWD);
            }

            if (result) {
                tvDeactivateStatus.setText("☠️ تم التعطيل");
                tvDeactivateStatus.setTextColor(Color.parseColor("#F44336"));
                if (layoutDeactivateItem != null) layoutDeactivateItem.setVisibility(View.GONE);
                
                // 🟢 إشعار MIRA ID
                if (!serial.isEmpty()) {
                    notifyMiraDeactivate(serial);
                }
                
                mContext.playSound(1);
            } else {
                tvDeactivateStatus.setText("❌ فشل");
                tvDeactivateStatus.setTextColor(Color.RED);
                mContext.playSound(2);
            }
        }
    }

    private int getFilterBank() {
        if (rbTID_filter.isChecked()) return RFIDWithUHFUART.Bank_TID;
        if (rbUser_filter.isChecked()) return RFIDWithUHFUART.Bank_USER;
        return RFIDWithUHFUART.Bank_EPC;
    }
}
