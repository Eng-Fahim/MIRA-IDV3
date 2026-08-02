package com.example.uhf.fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.uhf.R;
import com.example.uhf.activity.UHFMainActivity;
import com.example.uhf.manager.MiraSettingsManager;
import com.example.uhf.tools.StringUtils;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.utility.StringUtility;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * MIRA Programmer Fragment
 * 
 * قراءة وكتابة بيانات تاقات RFID مع تكامل MIRA ID
 * - بحث عن القطعة في MIRA
 * - قراءة EPC و User Memory
 * - كتابة بيانات جديدة
 */
public class UHFReadWriteFragment extends KeyDwonFragment implements OnClickListener {
    private static final String TAG = "UHFReadWriteFragment";
    private UHFMainActivity mContext;
    private MiraSettingsManager settingsManager;

    Spinner SpinnerBank;
    EditText EtPtr, EtLen, EtAccessPwd, EtData;
    Button BtRead, BtWrite;

    CheckBox cb_filter;
    EditText etPtr_filter, etData_filter, etLen_filter;
    RadioButton rbEPC_filter, rbTID_filter, rbUser_filter;

    // 🟢 عناصر MIRA Programmer
    private EditText etProgrammerSearch;
    private Button btnProgrammerSearch;
    private LinearLayout layoutProgrammerItem;
    private TextView tvProgrammerStatus, tvProgrammerItemTitle, tvProgrammerItemDetails;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.uhf_read_write_fragment, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        mContext.currentFragment = this;
        settingsManager = MiraSettingsManager.getInstance(mContext);

        // 🟢 عناصر MIRA
        etProgrammerSearch = getView().findViewById(R.id.etProgrammerSearch);
        btnProgrammerSearch = getView().findViewById(R.id.btnProgrammerSearch);
        layoutProgrammerItem = getView().findViewById(R.id.layoutProgrammerItem);
        tvProgrammerStatus = getView().findViewById(R.id.tvProgrammerStatus);
        tvProgrammerItemTitle = getView().findViewById(R.id.tvProgrammerItemTitle);
        tvProgrammerItemDetails = getView().findViewById(R.id.tvProgrammerItemDetails);

        SpinnerBank = getView().findViewById(R.id.SpinnerBank);
        EtPtr = getView().findViewById(R.id.EtPtr);
        EtLen = getView().findViewById(R.id.EtLen);
        EtAccessPwd = getView().findViewById(R.id.EtAccessPwd);
        EtData = getView().findViewById(R.id.EtData);
        etLen_filter = getView().findViewById(R.id.etLen_filter);

        cb_filter = getView().findViewById(R.id.cb_filter);
        etPtr_filter = getView().findViewById(R.id.etPtr_filter);
        etData_filter = getView().findViewById(R.id.etData_filter);
        rbEPC_filter = getView().findViewById(R.id.rbEPC_filter);
        rbEPC_filter.setOnClickListener(this);
        rbTID_filter = getView().findViewById(R.id.rbTID_filter);
        rbTID_filter.setOnClickListener(this);
        rbUser_filter = getView().findViewById(R.id.rbUser_filter);
        rbUser_filter.setOnClickListener(this);

        BtRead = getView().findViewById(R.id.BtRead);
        BtWrite = getView().findViewById(R.id.BtWrite);
        BtRead.setOnClickListener(v -> read());
        BtWrite.setOnClickListener(v -> write());

        // 🟢 بحث MIRA
        btnProgrammerSearch.setOnClickListener(v -> {
            String code = etProgrammerSearch.getText().toString().trim();
            if (TextUtils.isEmpty(code)) {
                Toast.makeText(mContext, "أدخل Serial أو GTIN-13", Toast.LENGTH_SHORT).show();
                return;
            }
            searchMiraItem(code);
        });

        EtData.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                EtLen.setText(String.valueOf(s.toString().trim().length() / 4));
            }
        });

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
            if (isChecked) {
                String data = etData_filter.getText().toString().trim();
                String rex = "[\\da-fA-F]*";
                if (data.isEmpty() || !data.matches(rex)) {
                    mContext.showToast(getString(R.string.uhf_msg_filter_data_must_hex));
                    cb_filter.setChecked(false);
                }
            }
        });
    }

    // ============================================
    // 🟢 البحث في MIRA ID
    // ============================================
    private void searchMiraItem(String code) {
        tvProgrammerStatus.setText("🔍 جاري البحث...");
        tvProgrammerStatus.setTextColor(Color.parseColor("#FF9800"));
        layoutProgrammerItem.setVisibility(View.GONE);

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
                JSONObject barcode = json.optJSONObject("barcode");

                getActivity().runOnUiThread(() -> {
                    if (item != null || barcode != null) {
                        layoutProgrammerItem.setVisibility(View.VISIBLE);
                        String title = item != null ? item.optString("title", "غير معروف") : "باركود غير مرتبط";
                        String serial = barcode != null ? barcode.optString("serial_no", code) :
                                       item != null ? item.optString("serial", code) : code;
                        String karat = item != null ? item.optString("karat", "") : "";
                        String weight = item != null ? item.optString("weight", "") : "";

                        tvProgrammerItemTitle.setText("📦 " + title);
                        tvProgrammerItemDetails.setText("🏷️ " + serial +
                            (karat.isEmpty() ? "" : " | 💎 " + karat) +
                            (weight.isEmpty() ? "" : " | ⚖️ " + weight + "g"));

                        // تعبئة حقل الفلتر تلقائياً
                        etData_filter.setText(serial);
                        etLen_filter.setText(String.valueOf(serial.length() * 4));
                        cb_filter.setChecked(true);

                        tvProgrammerStatus.setText("✅ تم العثور");
                        tvProgrammerStatus.setTextColor(Color.parseColor("#4CAF50"));
                    } else {
                        tvProgrammerStatus.setText("❌ غير موجود");
                        tvProgrammerStatus.setTextColor(Color.RED);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Search error: " + e.getMessage());
                getActivity().runOnUiThread(() -> {
                    tvProgrammerStatus.setText("❌ خطأ");
                    tvProgrammerStatus.setTextColor(Color.RED);
                });
            }
        }).start();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.rbEPC_filter: etPtr_filter.setText("32"); break;
            case R.id.rbTID_filter: etPtr_filter.setText("0"); break;
            case R.id.rbUser_filter: etPtr_filter.setText("0"); break;
        }
    }

    // ============================================
    // 🟢 قراءة (محفوظة مع تحسينات)
    // ============================================
    private void read() {
        String ptrStr = EtPtr.getText().toString().trim();
        if (ptrStr.equals("")) {
            mContext.showToast(R.string.uhf_msg_addr_not_null);
            return;
        } else if (!TextUtils.isDigitsOnly(ptrStr)) {
            mContext.showToast(R.string.uhf_msg_addr_must_decimal);
            return;
        }

        String cntStr = EtLen.getText().toString().trim();
        if (cntStr.equals("")) {
            mContext.showToast(R.string.uhf_msg_len_not_null);
            return;
        } else if (!TextUtils.isDigitsOnly(cntStr)) {
            mContext.showToast(R.string.uhf_msg_len_must_decimal);
            return;
        }

        String pwdStr = EtAccessPwd.getText().toString().trim();
        if (!TextUtils.isEmpty(pwdStr)) {
            if (pwdStr.length() != 8 || !mContext.vailHexInput(pwdStr)) {
                mContext.showToast(R.string.uhf_msg_addr_must_len8);
                return;
            }
        } else {
            pwdStr = "00000000";
        }

        boolean result = false;
        int Bank = SpinnerBank.getSelectedItemPosition();

        if (cb_filter.isChecked()) {
            if (etPtr_filter.getText().toString().isEmpty() || 
                etLen_filter.getText().toString().isEmpty() ||
                etData_filter.getText().toString().isEmpty()) {
                mContext.showToast("أكمل بيانات الفلتر");
                return;
            }

            int filterPtr = Integer.parseInt(etPtr_filter.getText().toString());
            String filterData = etData_filter.getText().toString();
            int filterCnt = Integer.parseInt(etLen_filter.getText().toString());
            int filterBank = getFilterBank();

            String data = mContext.mReader.readData(pwdStr, filterBank, filterPtr, filterCnt, filterData,
                    Bank, Integer.parseInt(ptrStr), Integer.parseInt(cntStr));
            if (data != null && data.length() > 0) {
                result = true;
                EtData.setText(data);
            }
        } else {
            String data = mContext.mReader.readData(pwdStr, Bank, Integer.parseInt(ptrStr), Integer.parseInt(cntStr));
            if (!TextUtils.isEmpty(data)) {
                result = true;
                EtData.setText(data);
            }
        }

        if (result) {
            tvProgrammerStatus.setText("✅ قراءة ناجحة");
            tvProgrammerStatus.setTextColor(Color.parseColor("#4CAF50"));
            mContext.playSound(1);
        } else {
            tvProgrammerStatus.setText("❌ فشل القراءة");
            tvProgrammerStatus.setTextColor(Color.RED);
            mContext.playSound(2);
        }
    }

    // ============================================
    // 🟢 كتابة (محفوظة مع تحسينات)
    // ============================================
    private void write() {
        String strPtr = EtPtr.getText().toString().trim();
        if (StringUtils.isEmpty(strPtr) || !StringUtility.isDecimal(strPtr)) {
            mContext.showToast(R.string.uhf_msg_addr_must_decimal);
            return;
        }

        String strPWD = EtAccessPwd.getText().toString().trim();
        if (StringUtils.isNotEmpty(strPWD)) {
            if (strPWD.length() != 8 || !mContext.vailHexInput(strPWD)) {
                mContext.showToast(R.string.uhf_msg_addr_must_len8);
                return;
            }
        } else {
            strPWD = "00000000";
        }

        String strData = EtData.getText().toString().trim();
        if (StringUtils.isEmpty(strData) || !mContext.vailHexInput(strData)) {
            mContext.showToast(R.string.uhf_msg_write_must_not_null);
            return;
        }

        String cntStr = EtLen.getText().toString().trim();
        if (StringUtils.isEmpty(cntStr) || !StringUtility.isDecimal(cntStr)) {
            mContext.showToast(R.string.uhf_msg_len_must_decimal);
            return;
        }

        if (strData.length() % 4 != 0) {
            mContext.showToast(R.string.uhf_msg_write_must_len4x);
            return;
        }

        int writeLen = Integer.parseInt(cntStr);
        int writePtr = Integer.parseInt(strPtr);
        boolean result = false;
        int Bank = SpinnerBank.getSelectedItemPosition();

        if (cb_filter.isChecked()) {
            int filterPtr = Integer.parseInt(etPtr_filter.getText().toString());
            String filterData = etData_filter.getText().toString();
            int filterCnt = Integer.parseInt(etLen_filter.getText().toString());
            int filterBank = getFilterBank();

            result = mContext.mReader.writeData(strPWD, filterBank, filterPtr, filterCnt, filterData,
                    Bank, writePtr, writeLen, strData);
        } else {
            result = mContext.mReader.writeData(strPWD, Bank, writePtr, writeLen, strData);
        }

        if (result) {
            tvProgrammerStatus.setText("✅ كتابة ناجحة");
            tvProgrammerStatus.setTextColor(Color.parseColor("#4CAF50"));
            mContext.playSound(1);
        } else {
            tvProgrammerStatus.setText("❌ فشل الكتابة");
            tvProgrammerStatus.setTextColor(Color.RED);
            mContext.playSound(2);
        }
    }

    private int getFilterBank() {
        if (rbTID_filter.isChecked()) return RFIDWithUHFUART.Bank_TID;
        if (rbUser_filter.isChecked()) return RFIDWithUHFUART.Bank_USER;
        return RFIDWithUHFUART.Bank_EPC;
    }

    public void myOnKeyDwon() {
        read();
    }
                    }
