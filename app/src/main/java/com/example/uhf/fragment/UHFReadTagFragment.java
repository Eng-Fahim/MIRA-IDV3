package com.example.uhf.fragment;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.example.uhf.R;
import com.example.uhf.activity.UHFMainActivity;
import com.example.uhf.manager.MiraSettingsManager;
import com.example.uhf.tools.CheckUtils;
import com.example.uhf.tools.NumberTool;
import com.example.uhf.tools.StringUtils;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.InventoryParameter;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback;

import com.example.uhf.data.MockUHFReaderImpl;
import com.example.uhf.data.UHFReaderRepository;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Intent;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

/**
 * MIRA Bridge Scan Fragment
 * 
 * يدعم:
 * - 📡 RFID UHF (قارئ Chainway)
 * - 📷 كاميرا الهاتف (QR / GS1 DataMatrix)
 * - ⌨️ إدخال يدوي (GTIN-13 / EPC)
 * - 🔀 وضع هجين (RFID + كاميرا + يدوي)
 * - تطبيق فوري للإعدادات من MiraSettingsManager
 */
public class UHFReadTagFragment extends KeyDwonFragment {
    private static final String TAG = "UHFReadTagFragment";
    private int inventoryFlag = 1;
    MyAdapter adapter;
    Button BtClear;
    TextView tvTime, tv_count, tv_total;
    RadioGroup RgInventory;
    RadioButton RbInventorySingle;
    RadioButton RbInventoryLoop;

    private CheckBox cbFilter, cbPhase;
    private ViewGroup layout_filter;
    private CheckBox cbEPC_Tam;

    // 🟢 عناصر الفحص اليدوي
    private EditText etGtinInput;
    private Button btnCheckGtin;

    // 🟢 عناصر بطاقة MIRA
    private View cardMiraResult;
    private View layoutMiraLoading;
    private View layoutMiraDetails;
    private TextView tvMiraStatus;
    private TextView tvMiraProductName;
    private TextView tvMiraEpcGtin;

    // 🟢 عناصر الصورة والشارات
    private ImageView ivMiraItemImage;
    private ProgressBar progressImageLoading;
    private TextView tvNoImagePlaceholder;
    private TextView tvMiraStatusBadge;
    private View statusIndicator;

    // 🟢 عناصر جدول الجرد التجميعي
    private TextView tvBatchTotal;
    private TextView tvBatchAllowed, tvBatchAllowedPct;
    private TextView tvBatchBlocked, tvBatchBlockedPct;
    private TextView tvBatchUnknown, tvBatchUnknownPct;

    private int countAllowed = 0;
    private int countBlocked = 0;
    private int countUnknown = 0;
    private Map<String, Boolean> processedTagsMap = new HashMap<>();

    private UHFReaderRepository bridgeReader;

    // 🟢 مدير الإعدادات
    private MiraSettingsManager settingsManager;
    private String scanMode = "rfid";

    long maxRunTime = 36000000L;
    EditText etTime;
    Button BtInventory;
    public static ListView LvTags;
    public UHFMainActivity mContext;
    private long startTime = SystemClock.elapsedRealtime();
    private int total;

    private final int MSG_STOP = 3;
    Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 1) {
                UHFTAGInfo info = (UHFTAGInfo) msg.obj;
                addDataToList(info);
            } else if (msg.what == 2) {
                if (mContext.loopFlag) {
                    handler.sendEmptyMessageDelayed(2, 10);
                    setTotalTime();
                } else {
                    handler.removeMessages(2);
                }
            } else if (msg.what == MSG_STOP) {
                stopInventory();
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.i(TAG, "UHFReadTagFragment.onCreateView");
        if (playSoundThread == null) {
            playSoundThread = new PlaySoundThread();
            playSoundThread.start();
        }
        return inflater.inflate(R.layout.uhf_readtag_fragment, container, false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mContext != null && mContext.mReader != null) {
            mContext.mReader.setInventoryCallback(null);
        }
        Log.i(TAG, "onDestroyView");
        if (playSoundThread != null) {
            playSoundThread.stopPlay();
            playSoundThread = null;
        }
        if (settingsManager != null) {
            settingsManager.unregisterListener("scan_fragment");
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Log.i(TAG, "UHFReadTagFragment.onActivityCreated");
        super.onActivityCreated(savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        mContext.currentFragment = this;

        // 🟢 تهيئة مدير الإعدادات
        settingsManager = MiraSettingsManager.getInstance(mContext);
        scanMode = settingsManager.getString("scan_mode", "rfid");

        BtClear = (Button) getView().findViewById(R.id.BtClear);
        tvTime = (TextView) getView().findViewById(R.id.tvTime);
        tvTime.setText("0s");
        tv_count = (TextView) getView().findViewById(R.id.tv_count);
        tv_total = (TextView) getView().findViewById(R.id.tv_total);
        RgInventory = (RadioGroup) getView().findViewById(R.id.RgInventory);
        RbInventorySingle = (RadioButton) getView().findViewById(R.id.RbInventorySingle);
        RbInventoryLoop = (RadioButton) getView().findViewById(R.id.RbInventoryLoop);
        etTime = (EditText) getView().findViewById(R.id.etTime);
        BtInventory = (Button) getView().findViewById(R.id.BtInventory);
        cbPhase = (CheckBox) getView().findViewById(R.id.cbPhase);

        etGtinInput = (EditText) getView().findViewById(R.id.etGtinInput);
        btnCheckGtin = (Button) getView().findViewById(R.id.btnCheckGtin);

        // 🟢 ربط عناصر جدول الجرد التجميعي
        tvBatchTotal = (TextView) getView().findViewById(R.id.tvBatchTotal);
        tvBatchAllowed = (TextView) getView().findViewById(R.id.tvBatchAllowed);
        tvBatchAllowedPct = (TextView) getView().findViewById(R.id.tvBatchAllowedPct);
        tvBatchBlocked = (TextView) getView().findViewById(R.id.tvBatchBlocked);
        tvBatchBlockedPct = (TextView) getView().findViewById(R.id.tvBatchBlockedPct);
        tvBatchUnknown = (TextView) getView().findViewById(R.id.tvBatchUnknown);
        tvBatchUnknownPct = (TextView) getView().findViewById(R.id.tvBatchUnknownPct);

        // 🟢 ربط عناصر بطاقة MIRA
        cardMiraResult = getView().findViewById(R.id.cardMiraResult);
        layoutMiraLoading = getView().findViewById(R.id.layoutMiraLoading);
        layoutMiraDetails = getView().findViewById(R.id.layoutMiraDetails);
        tvMiraStatus = (TextView) getView().findViewById(R.id.tvMiraStatus);
        tvMiraProductName = (TextView) getView().findViewById(R.id.tvMiraProductName);
        tvMiraEpcGtin = (TextView) getView().findViewById(R.id.tvMiraEpcGtin);

        // 🟢 ربط عناصر الصورة والشارات
        ivMiraItemImage = (ImageView) getView().findViewById(R.id.ivMiraItemImage);
        progressImageLoading = (ProgressBar) getView().findViewById(R.id.progressImageLoading);
        tvNoImagePlaceholder = (TextView) getView().findViewById(R.id.tvNoImagePlaceholder);
        tvMiraStatusBadge = (TextView) getView().findViewById(R.id.tvMiraStatusBadge);
        statusIndicator = (View) getView().findViewById(R.id.statusIndicator);

        // 🟢 تطبيق إعدادات المسح الحالية
        applyScanModeToUI();

        // 🟢 الاستماع لتغييرات الإعدادات
        settingsManager.registerListener("scan_fragment", new MiraSettingsManager.SettingsChangeListener() {
            @Override
            public void onSettingChanged(String key, Object value) {
                switch (key) {
                    case "scan_mode":
                        scanMode = (String) value;
                        applyScanModeToUI();
                        break;
                    case "show_mira_card":
                        boolean show = (Boolean) value;
                        if (cardMiraResult != null) {
                            cardMiraResult.setVisibility(show ? View.VISIBLE : View.GONE);
                        }
                        break;
                    case "auto_query_mira":
                        // يُطبق في sendTagToMiraServer
                        break;
                    case "sound_on_scan":
                        // يُطبق في playSoundThread
                        break;
                }
            }
        });

        // تهيئة محاكي البيئة
        bridgeReader = new MockUHFReaderImpl();
        bridgeReader.connect();
        bridgeReader.setTagCallback(new UHFReaderRepository.TagCallback() {
            @Override
            public void onTagRead(String epc, String tid, String rssi) {
                UHFTAGInfo info = new UHFTAGInfo();
                info.setEPC(epc);
                info.setTid(tid);
                info.setRssi(rssi);
                Message msg = handler.obtainMessage();
                msg.obj = info;
                msg.what = 1;
                handler.sendMessage(msg);
            }
        });

        if (btnCheckGtin != null) {
            btnCheckGtin.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String inputCode = etGtinInput.getText().toString().trim();
                    if (TextUtils.isEmpty(inputCode)) {
                        Toast.makeText(mContext, "يرجى إدخال رمز GTIN-13 أو EPC للفحص", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    processScannedData(inputCode, "-50");
                }
            });
        }

        LvTags = (ListView) getView().findViewById(R.id.LvTags);
        adapter = new MyAdapter(mContext);

        BtClear.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                String mockEpc = "E28011700000020123456789";
                processScannedData(mockEpc, "-65");
                Toast.makeText(mContext, "تم إرسال قراءة تجريبية لـ MIRA: " + mockEpc, Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        LvTags.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                adapter.setSelectItem(position);
                adapter.notifyDataSetInvalidated();
                UHFTAGInfo selectedTag = mContext.tagList.get(position);
                if (selectedTag != null && !TextUtils.isEmpty(selectedTag.getEPC())) {
                    processScannedData(selectedTag.getEPC(), selectedTag.getRssi());
                }
            }
        });

        LvTags.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long l) {
                ClipboardManager clipboard = (ClipboardManager) view.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("label", mContext.tagList.get(position).getEPC());
                clipboard.setPrimaryClip(clip);
                Toast.makeText(view.getContext(), R.string.msg_copy_clipboard, Toast.LENGTH_SHORT).show();
                return false;
            }
        });

        LvTags.setAdapter(adapter);
        BtClear.setOnClickListener(new BtClearClickListener());
        RgInventory.setOnCheckedChangeListener(new RgInventoryCheckedListener());
        BtInventory.setOnClickListener(new BtInventoryClickListener());

        initFilter(getView());
        initEPCTamperAlarm(getView());
        tv_count.setText(mContext.tagList.size() + "");
        tv_total.setText(total + "");
    }

    // =============================================
    // 🟢 تطبيق وضع المسح على الواجهة
    // =============================================
    private void applyScanModeToUI() {
        if (getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            switch (scanMode) {
                case "camera":
                    // إخفاء أزرار RFID، إظهار زر الكاميرا
                    if (BtInventory != null) BtInventory.setText("📷 فتح الكاميرا");
                    if (etGtinInput != null) etGtinInput.setHint("📷 امسح QR/GS1 أو أدخل يدوياً");
                    break;
                case "hybrid":
    if (BtInventory != null) BtInventory.setText("▶ RFID");
    if (etGtinInput != null) etGtinInput.setHint("🔍 RFID / 📷 QR / ⌨️ EPC");
    // في الوضع الهجين، الضغطة الطويلة تفتح الكاميرا
    if (BtInventory != null) {
        BtInventory.setOnLongClickListener(v -> {
            openCameraScanner();
            return true;
        });
    }
    break;
                case "manual":
                    // إخفاء RFID، إظهار يدوي فقط
                    if (BtInventory != null) BtInventory.setText("⌨️ إدخال يدوي");
                    if (etGtinInput != null) etGtinInput.setHint("⌨️ أدخل GTIN-13 أو EPC");
                    break;
                default: // rfid
                    if (BtInventory != null) BtInventory.setText(mContext.getString(R.string.btInventory));
                    if (etGtinInput != null) etGtinInput.setHint("🔍 أدخل GTIN-13 أو EPC");
                    break;
            }
        });
    }

    // =============================================
    // 🟢 معالجة موحدة للبيانات الممسوحة
    // =============================================
    private void processScannedData(String data, String rssi) {
    if (TextUtils.isEmpty(data)) return;
    
    // 🟢 حماية: التحقق من تهيئة Manager
    if (settingsManager == null && mContext != null) {
        settingsManager = MiraSettingsManager.getInstance(mContext);
    }

    // 🟢 اهتزاز إذا كان مفعلاً - مع حماية
    try {
        if (settingsManager != null && settingsManager.getBoolean("vibrate_on_scan", true) 
            && getActivity() != null) {
            android.os.Vibrator vibrator = (android.os.Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(100);
            }
        }
    } catch (Exception e) {
        Log.e(TAG, "Vibrate error: " + e.getMessage());
    }

    // 🟢 تحليل GS1 DataMatrix
    try {
        if (settingsManager != null && settingsManager.getBoolean("parse_gs1", true) && data.contains("(")) {
            data = parseGS1Data(data);
        }
    } catch (Exception e) {
        Log.e(TAG, "GS1 parse error: " + e.getMessage());
    }

    // 🟢 إرسال إلى MIRA API
    try {
        sendTagToMiraServer(data, rssi);
    } catch (Exception e) {
        Log.e(TAG, "Send error: " + e.getMessage());
        if (getActivity() != null) {
            Toast.makeText(mContext, "⚠️ خطأ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}

    /**
 * 🟢 تحليل GS1 DataMatrix - يستخرج الرقم التسلسلي فقط
 * 
 * أمثلة المدخلات:
 * - "BOU000381"
 * - "BWA-26000101"  
 * - "(21)BOU000381"
 * - "(01)06291337000016(21)BWA-26000101(3103)005200"
 * 
 * في جميع الحالات: الرقم التسلسلي هو ما يهمنا للبحث
 */
private String parseGS1Data(String gs1Data) {
    if (TextUtils.isEmpty(gs1Data)) return gs1Data;
    
    String serial = gs1Data.trim();
    
    try {
        // 🟢 إذا كان GS1 كامل - استخرج من (21)
        if (gs1Data.contains("(21)")) {
            int start = gs1Data.indexOf("(21)") + 4;
            int end = gs1Data.indexOf("(", start);
            if (end == -1) end = gs1Data.length();
            serial = gs1Data.substring(start, end).trim();
        }
        // 🟢 إذا كان GS1 بدون (21) - استخرج من البداية
        else if (gs1Data.contains("(01)")) {
            // قد يكون GTIN فقط بدون Serial - نستخدمه كما هو
            serial = gs1Data;
        }
        // 🟢 إذا كان Serial مباشر - نستخدمه كما هو
        // BOU000381, BWA-26000101, MIRA-20260729-00042
        
        Log.d(TAG, "GS1 Parsed Serial: " + serial);
        
    } catch (Exception e) {
        Log.e(TAG, "GS1 Parse error: " + e.getMessage());
    }
    
    return serial;
}

    // =============================================
    // 🟢 readTag معدلة لدعم أوضاع المسح
    // =============================================
    private void readTag() {
    switch (scanMode) {
        case "camera":
            openCameraScanner(); // ✅ الآن يفتح الكاميرا فعلياً
            break;
        case "manual":
            if (etGtinInput != null) etGtinInput.requestFocus();
            Toast.makeText(mContext, "⌨️ أدخل GTIN-13 أو EPC واضغط فحص", Toast.LENGTH_SHORT).show();
            break;
        case "hybrid":
        case "rfid":
        default:
            readTagRFID();
            break;
    }
}

    // =============================================
// 🟢 فتح كاميرا الماسح فعلياً
private void openCameraScanner() {
    try {
        // استخدام ZXing لمسح الباركود
        IntentIntegrator integrator = new IntentIntegrator(getActivity());
        integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
        integrator.setPrompt("📷 وجه الكاميرا نحو QR/GS1 DataMatrix");
        integrator.setCameraId(0); // الكاميرا الخلفية
        integrator.setBeepEnabled(settingsManager.getBoolean("sound_on_scan", true));
        integrator.setBarcodeImageEnabled(false);
        integrator.setOrientationLocked(true);
        
        // إطار المسح
        if (settingsManager.getBoolean("show_scan_frame", true)) {
            integrator.setPrompt("🎯 ضع الرمز داخل الإطار");
        }
        
        integrator.initiateScan();
    } catch (Exception e) {
        Log.e(TAG, "Camera open error: " + e.getMessage());
        // فشل الكاميرا → الرجوع للإدخال اليدوي
        Toast.makeText(mContext, "⚠️ تعذر فتح الكاميرا - استخدم الإدخال اليدوي", Toast.LENGTH_LONG).show();
        if (etGtinInput != null) etGtinInput.requestFocus();
    }
}

    // =============================================
    // 🟢 قراءة RFID (الكود الأصلي)
    // =============================================
    private void readTagRFID() {
        if (BtInventory.getText().equals(mContext.getString(R.string.btInventory)) || 
            BtInventory.getText().toString().contains("ابدأ")) {
            switch (inventoryFlag) {
                case 0:
                    startTime = SystemClock.elapsedRealtime();
                    boolean singleHardwareSuccess = false;
                    
                    if (mContext != null && mContext.mReader != null) {
                        UHFTAGInfo uhftagInfo = mContext.mReader.inventorySingleTag();
                        if (uhftagInfo != null) {
                            addDataToList(uhftagInfo);
                            setTotalTime();
                            if (settingsManager.getBoolean("sound_on_scan", true)) {
                                mContext.playSound(1);
                            }
                            singleHardwareSuccess = true;
                        }
                    }

                    if (!singleHardwareSuccess && bridgeReader != null) {
                        bridgeReader.injectManualTag("E28011700000020123456789", "-60");
                        setTotalTime();
                    }
                    break;

                case 1:
                    boolean batchHardwareSuccess = false;

                    if (mContext != null && mContext.mReader != null) {
                        mContext.mReader.setInventoryCallback(new IUHFInventoryCallback() {
                            @Override
                            public void callback(UHFTAGInfo uhftagInfo) {
                                Message msg = handler.obtainMessage();
                                msg.obj = uhftagInfo;
                                msg.what = 1;
                                handler.sendMessage(msg);
                                if (playSoundThread != null && settingsManager.getBoolean("sound_on_scan", true)) {
                                    playSoundThread.play();
                                }
                            }
                        });

                        if (playSoundThread != null) playSoundThread.cleanData();

                        InventoryParameter inventoryParameter = new InventoryParameter();
                        if (cbPhase != null) {
                            inventoryParameter.setResultData(new InventoryParameter.ResultData().setNeedPhase(cbPhase.isChecked()));
                        }

                        batchHardwareSuccess = mContext.mReader.startInventoryTag(inventoryParameter);
                    }

                    if (!batchHardwareSuccess) {
                        batchHardwareSuccess = true;
                    }

                    if (batchHardwareSuccess) {
                        String time = (etTime != null) ? etTime.getText().toString() : "";
                        if (time.length() > 0 && time.startsWith(".")) {
                            if (etTime != null) etTime.setText("");
                            time = "";
                        }

                        if (!time.isEmpty()) {
                            maxRunTime = (int) (Float.parseFloat(time) * 1000);
                            clearData();
                        } else {
                            maxRunTime = (etTime != null && etTime.getHint() != null) ? Long.parseLong(etTime.getHint().toString()) * 1000 : 36000000L;
                        }

                        handler.removeMessages(MSG_STOP);
                        handler.sendEmptyMessageDelayed(MSG_STOP, maxRunTime);
                        BtInventory.setText(mContext.getString(R.string.title_stop_Inventory));
                        mContext.loopFlag = true;
                        setViewEnabled(false);
                        startTime = SystemClock.elapsedRealtime();
                        handler.sendEmptyMessageDelayed(2, 10);
                    } else {
                        stopInventory();
                        mContext.showToast(R.string.uhf_msg_inventory_open_fail);
                    }
                    break;
                default:
                    break;
            }
        } else {
            stopInventory();
            setTotalTime();
        }
    }

    // =============================================
    // باقي الدوال (محفوظة من الكود السابق)
    // =============================================

    private Button btnSetFilter;

    private void initFilter(View view) {
        layout_filter = (ViewGroup) view.findViewById(R.id.layout_filter);
        if (layout_filter != null) {
            layout_filter.setVisibility(View.GONE);
        }
        
        cbFilter = (CheckBox) view.findViewById(R.id.cbFilter);
        if (cbFilter != null) {
            cbFilter.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (layout_filter != null) {
                        layout_filter.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                    }
                    if (!isChecked && mContext != null && mContext.mReader != null) {
                        mContext.mReader.setFilter(RFIDWithUHFUART.Bank_EPC, 0, 0, "");
                    }
                }
            });
        }

        final EditText etOffset = (EditText) view.findViewById(R.id.etPtr);
        final EditText etLen = (EditText) view.findViewById(R.id.etLen);
        final EditText etData = (EditText) view.findViewById(R.id.etData);
        final RadioButton rbEPC = (RadioButton) view.findViewById(R.id.rbEPC);
        final RadioButton rbTID = (RadioButton) view.findViewById(R.id.rbTID);
        final RadioButton rbUser = (RadioButton) view.findViewById(R.id.rbUser);

        if (etData != null && etLen != null) {
            etData.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    etLen.setText(String.valueOf(etData.getText().toString().trim().length() * 4));
                }
            });
        }

        btnSetFilter = (Button) view.findViewById(R.id.btSet);
        if (btnSetFilter != null) {
            btnSetFilter.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    int filterBank = RFIDWithUHFUART.Bank_EPC;
                    if (rbEPC != null && rbEPC.isChecked()) {
                        filterBank = RFIDWithUHFUART.Bank_EPC;
                    } else if (rbTID != null && rbTID.isChecked()) {
                        filterBank = RFIDWithUHFUART.Bank_TID;
                    } else if (rbUser != null && rbUser.isChecked()) {
                        filterBank = RFIDWithUHFUART.Bank_USER;
                    }

                    if (etLen == null || etOffset == null || etLen.getText().toString().isEmpty() || etOffset.getText().toString().isEmpty()) {
                        mContext.showToast("يرجى تعبئة العنوان والطول");
                        return;
                    }

                    int ptr = StringUtils.toInt(etOffset.getText().toString(), 0);
                    int len = StringUtils.toInt(etLen.getText().toString(), 0);
                    String data = etData.getText().toString().trim();

                    if (len > 0) {
                        String rex = "[\\da-fA-F]*";
                        if (data.isEmpty() || !data.matches(rex)) {
                            mContext.showToast(getString(R.string.uhf_msg_filter_data_must_hex));
                            return;
                        }
                        if (mContext.mReader.setFilter(filterBank, ptr, len, data)) {
                            mContext.showToast(R.string.uhf_msg_set_filter_succ);
                        } else {
                            mContext.showToast(R.string.uhf_msg_set_filter_fail);
                        }
                    } else {
                        String dataStr = "";
                        if (mContext.mReader.setFilter(RFIDWithUHFUART.Bank_EPC, 0, 0, dataStr)
                                && mContext.mReader.setFilter(RFIDWithUHFUART.Bank_TID, 0, 0, dataStr)
                                && mContext.mReader.setFilter(RFIDWithUHFUART.Bank_USER, 0, 0, dataStr)) {
                            mContext.showToast(R.string.msg_disable_succ);
                        } else {
                            mContext.showToast(R.string.msg_disable_fail);
                        }
                    }
                    if (cbFilter != null) cbFilter.setChecked(false);
                }
            });
        }

        if (rbEPC != null) rbEPC.setOnClickListener(v -> { if (rbEPC.isChecked() && etOffset != null) etOffset.setText("32"); });
        if (rbTID != null) rbTID.setOnClickListener(v -> { if (rbTID.isChecked() && etOffset != null) etOffset.setText("0"); });
        if (rbUser != null) rbUser.setOnClickListener(v -> { if (rbUser.isChecked() && etOffset != null) etOffset.setText("0"); });
    }

    private void initEPCTamperAlarm(View view) {
        cbEPC_Tam = (CheckBox) view.findViewById(R.id.cbEPC_Tam);
        if (cbEPC_Tam != null) {
            cbEPC_Tam.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!isChecked && mContext != null && mContext.mReader != null) {
                    mContext.mReader.setEPCMode();
                }
            });
        }
    }

   @Override
public void onPause() {
    super.onPause();
    stopInventory();
}

// ✅ هنا المكان الصحيح:
@Override
public void onActivityResult(int requestCode, int resultCode, Intent data) {
    IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
    
    if (result != null && result.getContents() != null) {
        String scannedData = result.getContents();
        Log.d(TAG, "Camera scanned: " + scannedData);
        processScannedData(scannedData, "-50");
        Toast.makeText(mContext, "✅ تم المسح: " + scannedData, Toast.LENGTH_SHORT).show();
        
        if (settingsManager.getBoolean("auto_scan", false)) {
            new Handler().postDelayed(() -> openCameraScanner(), 1500);
        }
    } else {
        super.onActivityResult(requestCode, resultCode, data);
    }
}

    private void addDataToList(UHFTAGInfo info) {
        String epc = info.getEPC();
        if (StringUtils.isNotEmpty(epc)) {
            boolean[] exists = new boolean[1];
            int insertIndex = CheckUtils.getInsertIndex(mContext.tagList, info, exists);
            if (exists[0]) {
                info.setCount(mContext.tagList.get(insertIndex).getCount() + 1);
                mContext.tagList.set(insertIndex, info);
            } else {
                mContext.tagList.add(insertIndex, info);
                tv_count.setText(String.valueOf(adapter.getCount()));
            }
            tv_total.setText(String.valueOf(++total));
            adapter.notifyDataSetChanged();

            if (settingsManager.getBoolean("auto_query_mira", true)) {
                sendTagToMiraServer(epc, info.getRssi());
            }
        }
    }

    public class BtClearClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            clearData();
            mContext.selectIndex = -1;
        }
    }

    private void clearData() {
        tv_count.setText("0");
        tv_total.setText("0");
        tvTime.setText("0s");
        total = 0;
        countAllowed = 0;
        countBlocked = 0;
        countUnknown = 0;
        processedTagsMap.clear();
        updateBatchSummaryUI();
        mContext.tagList.clear();
        adapter.notifyDataSetChanged();
        if (cardMiraResult != null) cardMiraResult.setVisibility(View.GONE);
    }

    private void updateBatchSummaryUI() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    int totalRead = processedTagsMap.size();
                    if (tvBatchTotal != null) tvBatchTotal.setText(String.valueOf(totalRead));
                    if (tvBatchAllowed != null) tvBatchAllowed.setText(String.valueOf(countAllowed));
                    if (tvBatchBlocked != null) tvBatchBlocked.setText(String.valueOf(countBlocked));
                    if (tvBatchUnknown != null) tvBatchUnknown.setText(String.valueOf(countUnknown));

                    if (totalRead > 0) {
                        if (tvBatchAllowedPct != null) tvBatchAllowedPct.setText(String.format(Locale.US, "%.1f%%", (countAllowed * 100.0 / totalRead)));
                        if (tvBatchBlockedPct != null) tvBatchBlockedPct.setText(String.format(Locale.US, "%.1f%%", (countBlocked * 100.0 / totalRead)));
                        if (tvBatchUnknownPct != null) tvBatchUnknownPct.setText(String.format(Locale.US, "%.1f%%", (countUnknown * 100.0 / totalRead)));
                    } else {
                        if (tvBatchAllowedPct != null) tvBatchAllowedPct.setText("0%");
                        if (tvBatchBlockedPct != null) tvBatchBlockedPct.setText("0%");
                        if (tvBatchUnknownPct != null) tvBatchUnknownPct.setText("0%");
                    }
                }
            });
        }
    }

    public class RgInventoryCheckedListener implements OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            if (checkedId == RbInventorySingle.getId()) {
                inventoryFlag = 0;
            } else if (checkedId == RbInventoryLoop.getId()) {
                inventoryFlag = 1;
            }
        }
    }

    public class BtInventoryClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            readTag();
        }
    }

    private void setTotalTime() {
        float useTime = (SystemClock.elapsedRealtime() - startTime) / 1000.0F;
        tvTime.setText(NumberTool.getPointDouble(1, useTime) + "s");
    }

    private void setViewEnabled(boolean enabled) {
        if (RbInventorySingle != null) RbInventorySingle.setEnabled(enabled);
        if (RbInventoryLoop != null) RbInventoryLoop.setEnabled(enabled);
        if (cbFilter != null) cbFilter.setEnabled(enabled);
        if (btnSetFilter != null) btnSetFilter.setEnabled(enabled);
        if (cbEPC_Tam != null) cbEPC_Tam.setEnabled(enabled);
        if (cbPhase != null) cbPhase.setEnabled(enabled);
    }

    private void stopInventory() {
        handler.removeMessages(MSG_STOP);
        if (mContext != null && mContext.loopFlag) {
            mContext.loopFlag = false;
            setViewEnabled(true);
            if (mContext.mReader != null) {
                mContext.mReader.stopInventory();
            }
            BtInventory.setText(mContext.getString(R.string.btInventory));
        }
    }

    private String mergeTidEpc(UHFTAGInfo uhftagInfo) {
        String data = "";
        if (uhftagInfo.getReserved() != null && !uhftagInfo.getReserved().isEmpty()) {
            data += "RESERVED:" + uhftagInfo.getReserved();
            data += "\nEPC:" + uhftagInfo.getEPC();
        } else {
            data += TextUtils.isEmpty(uhftagInfo.getTid()) ? uhftagInfo.getEPC() : "EPC:" + uhftagInfo.getEPC();
        }
        if (!TextUtils.isEmpty(uhftagInfo.getTid())
                && !uhftagInfo.getTid().equals("0000000000000000")
                && !uhftagInfo.getTid().equals("000000000000000000000000")) {
            data += "\nTID:" + uhftagInfo.getTid();
        }
        if (uhftagInfo.getUser() != null && uhftagInfo.getUser().length() > 0) {
            data += "\nUSER:" + uhftagInfo.getUser();
        }
        return data;
    }

    @Override
    public void myOnKeyDwon() {
        readTag();
    }

    public final class ViewHolder {
        public TextView tvTag;
        public TextView tvTagCount;
        public TextView tvTagRssi;
        public TextView tvPhase;
    }

    public class MyAdapter extends BaseAdapter {
        private LayoutInflater mInflater;

        public MyAdapter(Context context) {
            this.mInflater = LayoutInflater.from(context);
        }

        public int getCount() { return mContext.tagList.size(); }
        public Object getItem(int arg0) { return mContext.tagList.get(arg0); }
        public long getItemId(int arg0) { return arg0; }

        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                holder = new ViewHolder();
                convertView = mInflater.inflate(R.layout.listtag_items, null);
                holder.tvTag = (TextView) convertView.findViewById(R.id.TvTagUii);
                holder.tvTagCount = (TextView) convertView.findViewById(R.id.TvTagCount);
                holder.tvTagRssi = (TextView) convertView.findViewById(R.id.TvTagRssi);
                holder.tvPhase = (TextView) convertView.findViewById(R.id.TvPhase);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            UHFTAGInfo uhftagInfo = mContext.tagList.get(position);
            holder.tvTag.setText(mergeTidEpc(uhftagInfo));
            holder.tvTagCount.setText(String.valueOf(uhftagInfo.getCount()));
            holder.tvTagRssi.setText(uhftagInfo.getRssi());
            holder.tvPhase.setText(String.valueOf(uhftagInfo.getPhase()));

            if (position == mContext.selectIndex) {
                convertView.setBackgroundColor(mContext.getResources().getColor(R.color.lfile_colorPrimary));
            } else {
                convertView.setBackgroundColor(Color.TRANSPARENT);
            }
            return convertView;
        }

        public void setSelectItem(int select) {
            if (mContext.selectIndex == select) {
                mContext.selectIndex = -1;
            } else {
                mContext.selectIndex = select;
            }
        }
    }

    private Object objectLock = new Object();
    PlaySoundThread playSoundThread = null;

    private class PlaySoundThread extends Thread {
        private boolean isStop = false;
        ConcurrentLinkedQueue queue = new ConcurrentLinkedQueue();
        long count = 0;
        long consumption = 0;

        @Override
        public void run() {
            while (!isStop) {
                if (queue.isEmpty()) {
                    synchronized (objectLock) {
                        try {
                            objectLock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                if (mContext.loopFlag && settingsManager.getBoolean("sound_on_scan", true)) {
                    mContext.playSound(1);
                    queue.poll();
                    consumption++;
                }
                if (count - consumption > 50) {
                    for (int k = 0; k < 25; k++) {
                        queue.poll();
                    }
                    consumption += 25;
                }
            }
        }

        public void play() {
            queue.offer(1);
            synchronized (objectLock) {
                objectLock.notifyAll();
                count++;
            }
        }

        public void cleanData() {
            count = 0;
            consumption = 0;
            queue.clear();
        }

        public void stopPlay() {
            isStop = true;
            count = 0;
            consumption = 0;
            queue.clear();
            synchronized (objectLock) {
                objectLock.notifyAll();
            }
        }
    }

    private void loadItemImage(final String imageUrl) {
        if (TextUtils.isEmpty(imageUrl)) {
            showNoImage();
            return;
        }

        if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (ivMiraItemImage != null) ivMiraItemImage.setVisibility(View.GONE);
                    if (progressImageLoading != null) progressImageLoading.setVisibility(View.VISIBLE);
                    if (tvNoImagePlaceholder != null) tvNoImagePlaceholder.setVisibility(View.GONE);
                }
            });
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(imageUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setDoInput(true);
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(10000);
                    connection.connect();

                    final Bitmap bitmap = BitmapFactory.decodeStream(connection.getInputStream());
                    connection.disconnect();

                    if (bitmap != null && getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (ivMiraItemImage != null) {
                                    ivMiraItemImage.setImageBitmap(bitmap);
                                    ivMiraItemImage.setVisibility(View.VISIBLE);
                                }
                                if (progressImageLoading != null) progressImageLoading.setVisibility(View.GONE);
                                if (tvNoImagePlaceholder != null) tvNoImagePlaceholder.setVisibility(View.GONE);
                            }
                        });
                    } else {
                        showNoImage();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error loading image: " + e.getMessage());
                    showNoImage();
                }
            }
        }).start();
    }

    private void showNoImage() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (ivMiraItemImage != null) ivMiraItemImage.setVisibility(View.GONE);
                    if (progressImageLoading != null) progressImageLoading.setVisibility(View.GONE);
                    if (tvNoImagePlaceholder != null) tvNoImagePlaceholder.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    private void updateStatusBadge(boolean allowed, boolean hasItem) {
        if (tvMiraStatusBadge != null) {
            if (hasItem && allowed) {
                tvMiraStatusBadge.setText("✅ مصرح");
                tvMiraStatusBadge.setBackgroundColor(Color.parseColor("#4CAF50"));
            } else if (hasItem && !allowed) {
                tvMiraStatusBadge.setText("🚨 ممنوع");
                tvMiraStatusBadge.setBackgroundColor(Color.parseColor("#F44336"));
            } else {
                tvMiraStatusBadge.setText("⚠️ غير معروف");
                tvMiraStatusBadge.setBackgroundColor(Color.parseColor("#FF9800"));
            }
        }

        if (statusIndicator != null) {
            if (hasItem && allowed) {
                statusIndicator.setBackgroundColor(Color.parseColor("#4CAF50"));
            } else if (hasItem && !allowed) {
                statusIndicator.setBackgroundColor(Color.parseColor("#F44336"));
            } else {
                statusIndicator.setBackgroundColor(Color.parseColor("#FF9800"));
            }
        }
    }

    private void sendTagToMiraServer(final String epc, final String rssi) {
    // 🟢 حماية: التحقق من التهيئة
    if (settingsManager == null && mContext != null) {
        settingsManager = MiraSettingsManager.getInstance(mContext);
    }
    
    final boolean showCard = settingsManager != null && settingsManager.getBoolean("show_mira_card", true);
    
    if (getActivity() != null) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (showCard && cardMiraResult != null) {
                    cardMiraResult.setVisibility(View.VISIBLE);
                    if (layoutMiraLoading != null) layoutMiraLoading.setVisibility(View.VISIBLE);
                    if (layoutMiraDetails != null) layoutMiraDetails.setVisibility(View.GONE);
                }
            }
        });
    }

    new Thread(new Runnable() {
        @Override
        public void run() {
            InputStream inputStream = null;
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
                if (rssi != null && !rssi.isEmpty()) {
                    jsonParam.put("rssi", rssi);
                }

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

                final String jsonResponseStr = response.toString();
                Log.d(TAG, "MIRA Response: " + responseCode);

                if (getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (layoutMiraLoading != null) layoutMiraLoading.setVisibility(View.GONE);
                            if (layoutMiraDetails != null) layoutMiraDetails.setVisibility(View.VISIBLE);

                            try {
                                JSONObject jsonObject = new JSONObject(jsonResponseStr);
                                JSONObject decision = jsonObject.optJSONObject("decision");
                                JSONObject item = jsonObject.optJSONObject("item");
                                JSONObject barcode = jsonObject.optJSONObject("barcode");

                                boolean allowed = decision != null && decision.optBoolean("allowed", false);
                                String message = decision != null ? decision.optString("message", "") : "";
                                boolean hasItem = (item != null);

                                // تحديث الإحصائيات
                                if (!processedTagsMap.containsKey(epc)) {
                                    processedTagsMap.put(epc, true);
                                    if (hasItem && allowed) countAllowed++;
                                    else if (hasItem && !allowed) countBlocked++;
                                    else countUnknown++;
                                    updateBatchSummaryUI();
                                }

                                // 🟢 عرض serial من الباركود إذا وجد
                                String displayCode = epc;
                                if (barcode != null && !barcode.optString("serial_no", "").isEmpty()) {
                                    displayCode = barcode.optString("serial_no");
                                }

                                // صورة
                                String imageUrl = "";
                                if (item != null) {
                                    JSONArray images = item.optJSONArray("images");
                                    if (images != null && images.length() > 0) {
                                        imageUrl = images.getJSONObject(0).optString("image_url", "");
                                    }
                                }
                                loadItemImage(imageUrl);
                                updateStatusBadge(allowed, hasItem);

                                // الحالة
                                if (tvMiraStatus != null) {
                                    tvMiraStatus.setText(message);
                                    tvMiraStatus.setTextColor(allowed ? Color.parseColor("#2E7D32") : 
                                        (hasItem ? Color.RED : Color.parseColor("#FF9800")));
                                }

                                // اسم المنتج
                                if (tvMiraProductName != null && item != null) {
                                    String title = item.optString("title", "غير محدد");
                                    String karat = item.optString("karat", "");
                                    double weight = item.optDouble("weight", 0.0);
                                    
                                    StringBuilder info = new StringBuilder("📦 ").append(title);
                                    if (!karat.isEmpty()) info.append("\n💎 عيار: ").append(karat);
                                    if (weight > 0) info.append("\n⚖️ الوزن: ").append(weight).append(" غرام");
                                    tvMiraProductName.setText(info.toString());
                                }

                                // الكود
                                if (tvMiraEpcGtin != null) {
                                    String status = item != null ? item.optString("status", "") : "";
                                    StringBuilder epcInfo = new StringBuilder("🏷️ ").append(displayCode);
                                    if (!status.isEmpty()) {
                                        String ar = status.equals("sold") ? "مباع" : status.equals("available") ? "متاح" : status;
                                        epcInfo.append("\n📋 الحالة: ").append(ar);
                                    }
                                    tvMiraEpcGtin.setText(epcInfo.toString());
                                }

                            } catch (Exception e) {
                                Log.e(TAG, "JSON error: " + e.getMessage());
                                if (tvMiraStatus != null) {
                                    tvMiraStatus.setText("⚠️ خطأ في البيانات");
                                    tvMiraStatus.setTextColor(Color.RED);
                                }
                            }
                        }
                    });
                }

            } catch (final Exception e) {
                Log.e(TAG, "Connection error: " + e.getMessage());
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (layoutMiraLoading != null) layoutMiraLoading.setVisibility(View.GONE);
                        if (layoutMiraDetails != null) layoutMiraDetails.setVisibility(View.VISIBLE);
                        if (tvMiraStatus != null) {
                            tvMiraStatus.setText("❌ خطأ اتصال");
                            tvMiraStatus.setTextColor(Color.RED);
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
