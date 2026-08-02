package com.example.uhf.fragment;

import android.app.AlertDialog;
import android.content.DialogInterface;
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
import android.widget.TextView;
import android.widget.Toast;

import com.example.uhf.R;
import com.example.uhf.activity.UHFMainActivity;
import com.example.uhf.manager.MiraSettingsManager;
import com.example.uhf.tools.UIHelper;
import com.rscja.deviceapi.RFIDWithUHFUART;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * MIRA Lock Fragment
 * 
 * قفل تاقات RFID لمنع التعديل - مع تكامل MIRA ID
 */
public class UHFLockFragment extends KeyDwonFragment implements OnClickListener {

    private static final String TAG = "UHFLockFragment";
    private UHFMainActivity mContext;
    private MiraSettingsManager settingsManager;

    EditText EtAccessPwd_Lock, etLockCode;
    Button btnLock;

    CheckBox cb_filter_lock;
    EditText etPtr_filter_lock, etLen_filter_lock, etData_filter_lock;
    RadioButton rbEPC_filter_lock, rbTID_filter_lock, rbUser_filter_lock;

    // 🟢 عناصر MIRA
    private EditText etLockSearch;
    private Button btnLockSearch;
    private LinearLayout layoutLockItem;
    private TextView tvLockStatus, tvLockItemTitle, tvLockItemDetails;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.uhf_lock_fragment, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        mContext.currentFragment = this;
        settingsManager = MiraSettingsManager.getInstance(mContext);

        // 🟢 عناصر MIRA
        etLockSearch = getView().findViewById(R.id.etLockSearch);
        btnLockSearch = getView().findViewById(R.id.btnLockSearch);
        layoutLockItem = getView().findViewById(R.id.layoutLockItem);
        tvLockStatus = getView().findViewById(R.id.tvLockStatus);
        tvLockItemTitle = getView().findViewById(R.id.tvLockItemTitle);
        tvLockItemDetails = getView().findViewById(R.id.tvLockItemDetails);

        etLockCode = getView().findViewById(R.id.etLockCode);
        EtAccessPwd_Lock = getView().findViewById(R.id.EtAccessPwd_Lock);
        btnLock = getView().findViewById(R.id.btnLock);

        etPtr_filter_lock = getView().findViewById(R.id.etPtr_filter_lock);
        etLen_filter_lock = getView().findViewById(R.id.etLen_filter_lock);
        rbEPC_filter_lock = getView().findViewById(R.id.rbEPC_filter_lock);
        rbTID_filter_lock = getView().findViewById(R.id.rbTID_filter_lock);
        rbUser_filter_lock = getView().findViewById(R.id.rbUser_filter_lock);
        cb_filter_lock = getView().findViewById(R.id.cb_filter_lock);
        etData_filter_lock = getView().findViewById(R.id.etData_filter_lock);

        rbEPC_filter_lock.setOnClickListener(this);
        rbTID_filter_lock.setOnClickListener(this);
        rbUser_filter_lock.setOnClickListener(this);

        // 🟢 بحث MIRA
        btnLockSearch.setOnClickListener(v -> {
            String code = etLockSearch.getText().toString().trim();
            if (TextUtils.isEmpty(code)) {
                Toast.makeText(mContext, "أدخل Serial أو GTIN-13", Toast.LENGTH_SHORT).show();
                return;
            }
            searchMiraItem(code);
        });

        etData_filter_lock.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                etLen_filter_lock.setText(String.valueOf(s.toString().trim().length() * 4));
            }
        });

        cb_filter_lock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                String data = etData_filter_lock.getText().toString().trim();
                String rex = "[\\da-fA-F]*";
                if (data == null || data.isEmpty() || !data.matches(rex)) {
                    UIHelper.ToastMessage(mContext, getString(R.string.uhf_msg_filter_data_must_hex));
                    cb_filter_lock.setChecked(false);
                }
            }
        });

        // 🟢 Dialog توليد كود القفل
        etLockCode.setOnClickListener(view -> showLockCodeDialog());

        btnLock.setOnClickListener(new btnLockOnClickListener());
    }

    // ============================================
    // 🟢 بحث MIRA
    // ============================================
    private void searchMiraItem(String code) {
        tvLockStatus.setText("🔍 جاري البحث...");
        tvLockStatus.setTextColor(Color.parseColor("#FF9800"));
        layoutLockItem.setVisibility(View.GONE);

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
                InputStream is = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "utf-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);

                JSONObject json = new JSONObject(response.toString());
                JSONObject item = json.optJSONObject("item");
                JSONObject barcode = json.optJSONObject("barcode");

                getActivity().runOnUiThread(() -> {
                    if (item != null || barcode != null) {
                        layoutLockItem.setVisibility(View.VISIBLE);
                        String title = item != null ? item.optString("title", "غير معروف") : "باركود";
                        String serial = barcode != null ? barcode.optString("serial_no", code) :
                                       item != null ? item.optString("serial", code) : code;

                        tvLockItemTitle.setText("📦 " + title);
                        tvLockItemDetails.setText("🏷️ " + serial);

                        etData_filter_lock.setText(serial);
                        etLen_filter_lock.setText(String.valueOf(serial.length() * 4));
                        cb_filter_lock.setChecked(true);

                        tvLockStatus.setText("✅ تم العثور");
                        tvLockStatus.setTextColor(Color.parseColor("#4CAF50"));
                    } else {
                        tvLockStatus.setText("❌ غير موجود");
                        tvLockStatus.setTextColor(Color.RED);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Search error: " + e.getMessage());
            }
        }).start();
    }

    private void showLockCodeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(R.string.tvLockCode);
        final View vv = LayoutInflater.from(mContext).inflate(R.layout.uhf_dialog_lock_code, null);
        builder.setView(vv);
        builder.setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
            dialogInterface.dismiss();
            etLockCode.getText().clear();
        });
        builder.setPositiveButton(R.string.ok, (dialogInterface, i) -> {
            RadioButton rbOpen = vv.findViewById(R.id.rbOpen);
            RadioButton rbLock = vv.findViewById(R.id.rbLock);
            CheckBox cbPerm = vv.findViewById(R.id.cbPerm);
            CheckBox cbKill = vv.findViewById(R.id.cbKill);
            CheckBox cbAccess = vv.findViewById(R.id.cbAccess);
            CheckBox cbEPC = vv.findViewById(R.id.cbEPC);
            CheckBox cbTid = vv.findViewById(R.id.cbTid);
            CheckBox cbUser = vv.findViewById(R.id.cbUser);

            int[] data = new int[20];
            if (cbUser.isChecked()) { data[11] = 1; if (cbPerm.isChecked()) { data[0] = 1; data[10] = 1; } if (rbLock.isChecked()) data[1] = 1; }
            if (cbTid.isChecked()) { data[13] = 1; if (cbPerm.isChecked()) { data[12] = 1; data[2] = 1; } if (rbLock.isChecked()) data[3] = 1; }
            if (cbEPC.isChecked()) { data[15] = 1; if (cbPerm.isChecked()) { data[14] = 1; data[4] = 1; } if (rbLock.isChecked()) data[5] = 1; }
            if (cbAccess.isChecked()) { data[17] = 1; if (cbPerm.isChecked()) { data[16] = 1; data[6] = 1; } if (rbLock.isChecked()) data[7] = 1; }
            if (cbKill.isChecked()) { data[19] = 1; if (cbPerm.isChecked()) { data[18] = 1; data[8] = 1; } if (rbLock.isChecked()) data[9] = 1; }

            StringBuilder sb = new StringBuilder("0000");
            for (int k = data.length - 1; k >= 0; k--) sb.append(data[k]);
            String code = binaryString2hexString(sb.toString());
            etLockCode.setText(code.replace(" ", "0"));
        });
        builder.create().show();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.rbEPC_filter_lock: etPtr_filter_lock.setText("32"); break;
            case R.id.rbTID_filter_lock: etPtr_filter_lock.setText("0"); break;
            case R.id.rbUser_filter_lock: etPtr_filter_lock.setText("0"); break;
        }
    }

    public class btnLockOnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            String strPWD = EtAccessPwd_Lock.getText().toString().trim();
            String strLockCode = etLockCode.getText().toString().trim();

            if (TextUtils.isEmpty(strPWD) || strPWD.length() != 8 || !mContext.vailHexInput(strPWD)) {
                UIHelper.ToastMessage(mContext, R.string.rfid_mgs_error_nopwd);
                return;
            }
            if (TextUtils.isEmpty(strLockCode)) {
                UIHelper.ToastMessage(mContext, R.string.rfid_mgs_error_nolockcode);
                return;
            }

            boolean result;
            if (cb_filter_lock.isChecked()) {
                String filterData = etData_filter_lock.getText().toString();
                if (filterData == null || filterData.isEmpty()) {
                    UIHelper.ToastMessage(mContext, "بيانات الفلتر مطلوبة");
                    return;
                }
                int filterPtr = Integer.parseInt(etPtr_filter_lock.getText().toString());
                int filterCnt = Integer.parseInt(etLen_filter_lock.getText().toString());
                int filterBank = getFilterBank();
                result = mContext.mReader.lockMem(strPWD, filterBank, filterPtr, filterCnt, filterData, strLockCode);
            } else {
                result = mContext.mReader.lockMem(strPWD, strLockCode);
            }

            if (result) {
                tvLockStatus.setText("🔒 تم القفل");
                tvLockStatus.setTextColor(Color.parseColor("#4CAF50"));
                mContext.playSound(1);
            } else {
                tvLockStatus.setText("❌ فشل");
                tvLockStatus.setTextColor(Color.RED);
                mContext.playSound(2);
            }
        }
    }

    private int getFilterBank() {
        if (rbTID_filter_lock.isChecked()) return RFIDWithUHFUART.Bank_TID;
        if (rbUser_filter_lock.isChecked()) return RFIDWithUHFUART.Bank_USER;
        return RFIDWithUHFUART.Bank_EPC;
    }

    public static String binaryString2hexString(String bString) {
        if (bString == null || bString.equals("") || bString.length() % 8 != 0) return null;
        StringBuilder tmp = new StringBuilder();
        for (int i = 0; i < bString.length(); i += 4) {
            int iTmp = 0;
            for (int j = 0; j < 4; j++) iTmp += Integer.parseInt(bString.substring(i + j, i + j + 1)) << (4 - j - 1);
            tmp.append(Integer.toHexString(iTmp));
        }
        return tmp.toString();
    }
}
