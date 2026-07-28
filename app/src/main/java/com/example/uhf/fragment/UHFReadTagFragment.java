package com.example.uhf.fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.example.uhf.R;
import com.example.uhf.activity.UHFMainActivity;
import com.uhf.api.cls.Reader; // اعتماداً على مكدس المكتبة الخاص بجهازك

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class UHFReadTagFragment extends KeyDwonFragment implements View.OnClickListener {

    private static final String TAG = "UHFReadTagFragment";
    private UHFMainActivity mContext;

    // عناصر الواجهة الأصلية للـ RFID
    private RadioGroup RgInventory;
    private RadioButton RbInventorySingle;
    private RadioButton RbInventoryLoop;
    private CheckBox cbFilter;
    private Button BtInventory;
    private Button BtClear;
    private Button btSet;
    private EditText etPtr, etLen, etData;
    private RadioButton rbEPC, rbTID, rbUser;
    private ListView LvTags;
    private TextView tv_count, tv_total, tvTime;
    private CheckBox cbPhase;

    // عناصر MIRA ID المضافة
    private EditText etGtinInput;
    private Button btnCheckGtin;
    private LinearLayout cardMiraResult, layoutMiraLoading, layoutMiraDetails;
    private TextView tvMiraStatus, tvMiraProductName, tvMiraEpcGtin;

    // متغيرات عملية الفحص والعد
    private boolean loopFlag = false;
    private int totalTagCount = 0;
    private long startTime = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.uhf_readtag_fragment, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = (UHFMainActivity) getActivity();

        initViews();
        initMiraViews();
    }

    private void initViews() {
        View view = getView();
        if (view == null) return;

        RgInventory = view.findViewById(R.id.RgInventory);
        RbInventorySingle = view.findViewById(R.id.RbInventorySingle);
        RbInventoryLoop = view.findViewById(R.id.RbInventoryLoop);
        cbFilter = view.findViewById(R.id.cbFilter);
        cbPhase = view.findViewById(R.id.cbPhase);

        BtInventory = view.findViewById(R.id.BtInventory);
        BtClear = view.findViewById(R.id.BtClear);
        btSet = view.findViewById(R.id.btSet);

        etPtr = view.findViewById(R.id.etPtr);
        etLen = view.findViewById(R.id.etLen);
        etData = view.findViewById(R.id.etData);

        rbEPC = view.findViewById(R.id.rbEPC);
        rbTID = view.findViewById(R.id.rbTID);
        rbUser = view.findViewById(R.id.rbUser);

        LvTags = view.findViewById(R.id.LvTags);
        tv_count = view.findViewById(R.id.tv_count);
        tv_total = view.findViewById(R.id.tv_total);
        tvTime = view.findViewById(R.id.tvTime);

        if (BtInventory != null) BtInventory.setOnClickListener(this);
        if (BtClear != null) BtClear.setOnClickListener(this);
        if (btSet != null) btSet.setOnClickListener(this);
    }

    private void initMiraViews() {
        View view = getView();
        if (view == null) return;

        etGtinInput = view.findViewById(R.id.etGtinInput);
        btnCheckGtin = view.findViewById(R.id.btnCheckGtin);
        cardMiraResult = view.findViewById(R.id.cardMiraResult);
        layoutMiraLoading = view.findViewById(R.id.layoutMiraLoading);
        layoutMiraDetails = view.findViewById(R.id.layoutMiraDetails);
        tvMiraStatus = view.findViewById(R.id.tvMiraStatus);
        tvMiraProductName = view.findViewById(R.id.tvMiraProductName);
        tvMiraEpcGtin = view.findViewById(R.id.tvMiraEpcGtin);

        if (btnCheckGtin != null) {
            btnCheckGtin.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (etGtinInput == null) return;
                    String inputCode = etGtinInput.getText().toString().trim();
                    if (!TextUtils.isEmpty(inputCode)) {
                        sendTagToMiraServer(inputCode, "-50");
                    } else {
                        Toast.makeText(mContext, "يرجى إدخال رمز GTIN-13 أو EPC للفحص", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.BtInventory) {
            startInventory();
        } else if (id == R.id.BtClear) {
            clearData();
        } else if (id == R.id.btSet) {
            Toast.makeText(mContext, "تم حفظ الإعدادات", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * بدء عملية قراءة التاقات عبر قارئ الـ RFID للأجهزة المحمولة
     */
    private void startInventory() {
        if (BtInventory.getText().toString().equals(mContext.getString(R.string.btInventory))) {
            // البدء بالقراءة
            if (RbInventoryLoop.isChecked()) {
                loopFlag = true;
                new Thread(new InventoryRunnable()).start();
            } else {
                // قراءة مفردة (Single)
                String strEPC = mContext.mReader.RDR_TagInventory(); 
                if (!TextUtils.isEmpty(strEPC)) {
                    addTagToList(strEPC, "-50");
                }
            }
            BtInventory.setText(mContext.getString(R.string.btStopInventory));
            startTime = System.currentTimeMillis();
        } else {
            // إيقاف القراءة
            loopFlag = false;
            BtInventory.setText(mContext.getString(R.string.btInventory));
            if (startTime > 0) {
                long duration = System.currentTimeMillis() - startTime;
                if (tvTime != null) tvTime.setText(duration + " ms");
            }
        }
    }

    private class InventoryRunnable implements Runnable {
        @Override
        public void run() {
            while (loopFlag) {
                // جلب التاج الممسوح من مكدس مكتبة الجهاز
                String strEPC = mContext.mReader != null ? mContext.mReader.RDR_TagInventory() : null;
                if (!TextUtils.isEmpty(strEPC)) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            addTagToList(strEPC, "-50");
                        }
                    });
                }
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    };

    private void clearData() {
        totalTagCount = 0;
        if (tv_count != null) tv_count.setText("0");
        if (tv_total != null) tv_total.setText("0");
        if (cardMiraResult != null) cardMiraResult.setVisibility(View.GONE);
    }

    /**
     * تتم معالجة التاج المتقط وتمريره لـ MIRA تلقائياً
     */
    public void addTagToList(String epc, String rssi) {
        if (getActivity() == null) return;

        totalTagCount++;
        if (tv_count != null) tv_count.setText(String.valueOf(totalTagCount));
        if (tv_total != null) tv_total.setText(String.valueOf(totalTagCount));

        if (etGtinInput != null) {
            etGtinInput.setText(epc);
        }

        // إرسال الـ EPC المكتشف فوراً إلى سيرفر MIRA ID
        sendTagToMiraServer(epc, rssi);
    }

    /**
     * دالة الاتصال المباشر بـ MIRA Digital Trust API
     */
    private void sendTagToMiraServer(final String epc, final String rssi) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (cardMiraResult != null) cardMiraResult.setVisibility(View.VISIBLE);
                    if (layoutMiraLoading != null) layoutMiraLoading.setVisibility(View.VISIBLE);
                    if (layoutMiraDetails != null) layoutMiraDetails.setVisibility(View.GONE);
                }
            });
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                InputStream inputStream = null;
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
                    jsonParam.put("rssi", rssi);
                    jsonParam.put("gate_id", "handheld_c72");

                    try (OutputStream os = conn.getOutputStream()) {
                        byte[] input = jsonParam.toString().getBytes("utf-8");
                        os.write(input, 0, input.length);
                    }

                    final int responseCode = conn.getResponseCode();
                    if (responseCode >= 200 && responseCode < 300) {
                        inputStream = conn.getInputStream();
                    } else {
                        inputStream = conn.getErrorStream();
                    }

                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "utf-8"));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line.trim());
                    }

                    final String responseData = response.toString();

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (layoutMiraLoading != null) layoutMiraLoading.setVisibility(View.GONE);
                                if (layoutMiraDetails != null) layoutMiraDetails.setVisibility(View.VISIBLE);

                                if (responseCode == 200) {
                                    if (tvMiraStatus != null) {
                                        tvMiraStatus.setText("حالة القطعة: ✅ موثقة ومعتمدة في MIRA");
                                        tvMiraStatus.setTextColor(Color.parseColor("#2E7D32"));
                                    }
                                    if (tvMiraProductName != null) {
                                        tvMiraProductName.setText("استجابة النظام: " + responseData);
                                    }
                                    if (tvMiraEpcGtin != null) {
                                        tvMiraEpcGtin.setText("رمز EPC: " + epc);
                                    }
                                } else {
                                    if (tvMiraStatus != null) {
                                        tvMiraStatus.setText("حالة القطعة: ❌ غير مسجلة (كود: " + responseCode + ")");
                                        tvMiraStatus.setTextColor(Color.RED);
                                    }
                                    if (tvMiraProductName != null) {
                                        tvMiraProductName.setText("الرسالة: " + responseData);
                                    }
                                    if (tvMiraEpcGtin != null) {
                                        tvMiraEpcGtin.setText("رمز EPC: " + epc);
                                    }
                                }
                            }
                        });
                    }

                } catch (final Exception e) {
                    Log.e(TAG, "Error: " + e.getMessage());
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Option
                            public void run() {
                                if (layoutMiraLoading != null) layoutMiraLoading.setVisibility(View.GONE);
                                if (layoutMiraDetails != null) layoutMiraDetails.setVisibility(View.VISIBLE);

                                if (tvMiraStatus != null) {
                                    tvMiraStatus.setText("حالة الاتصال: ⚠️ خطأ في الشبكة");
                                    tvMiraStatus.setTextColor(Color.RED);
                                }
                                if (tvMiraProductName != null) {
                                    tvMiraProductName.setText("السبب: " + e.getMessage());
                                }
                                if (tvMiraEpcGtin != null) {
                                    tvMiraEpcGtin.setText("رمز EPC: " + epc);
                                }
                            }
                        });
                    }
                } finally {
                    if (inputStream != null) {
                        try { inputStream.close(); } catch (Exception ignored) {}
                    }
                }
            }
        }).start();
    }
}
