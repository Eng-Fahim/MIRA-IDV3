package com.example.uhf.fragment;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.example.uhf.R;
import com.example.uhf.activity.UHFMainActivity;
import com.example.uhf.tools.CheckUtils;
import com.example.uhf.tools.NumberTool;
import com.example.uhf.tools.StringUtils;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.InventoryParameter;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback;

// 🟢 MIRA Bridge Imports
import com.example.uhf.data.MockUHFReaderImpl;
import com.example.uhf.data.UHFReaderRepository;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.json.JSONObject;

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

    // 🟢 عناصر الفحص اليدوي لـ GTIN-13 / EPC
    private EditText etGtinInput;
    private Button btnCheckGtin;

    // 🟢 عناصر بطاقة نتائج MIRA Digital Trust
    private View cardMiraResult;
    private View layoutMiraLoading;
    private View layoutMiraDetails;
    private TextView tvMiraStatus;
    private TextView tvMiraProductName;
    private TextView tvMiraEpcGtin;

    // 🟢 MIRA Bridge Simulator Instance
    private UHFReaderRepository bridgeReader;

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
        mContext.mReader.setInventoryCallback(null);
        Log.i(TAG, "onDestroyView");
        if (playSoundThread != null) {
            playSoundThread.stopPlay();
            playSoundThread = null;
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Log.i(TAG, "UHFReadTagFragment.onActivityCreated");
        super.onActivityCreated(savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        mContext.currentFragment = this;

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

        // 🟢 ربط عناصر الفحص اليدوي
        etGtinInput = (EditText) getView().findViewById(R.id.etGtinInput);
        btnCheckGtin = (Button) getView().findViewById(R.id.btnCheckGtin);

        // 🟢 ربط عناصر بطاقة النتيجة
        cardMiraResult = getView().findViewById(R.id.cardMiraResult);
        layoutMiraLoading = getView().findViewById(R.id.layoutMiraLoading);
        layoutMiraDetails = getView().findViewById(R.id.layoutMiraDetails);
        tvMiraStatus = (TextView) getView().findViewById(R.id.tvMiraStatus);
        tvMiraProductName = (TextView) getView().findViewById(R.id.tvMiraProductName);
        tvMiraEpcGtin = (TextView) getView().findViewById(R.id.tvMiraEpcGtin);

        // 🟢 تهيئة وتفعيل محاكي MIRA Bridge
        bridgeReader = new MockUHFReaderImpl();
        bridgeReader.connect();
        // ✅ الكود المصحح مع الدعم الصحيح لـ SDK الخاص بأجهزة UHF:
bridgeReader.setTagCallback(new UHFReaderRepository.TagCallback() {
    @Override
    public void onTagRead(String epc, String tid, String rssi) {
        UHFTAGInfo info = new UHFTAGInfo();
        info.setEPC(epc); // 🟢 استخدام الأحرف الكبيرة EPC
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
                    
                    // تمرير البيانات اليدوية عبر طبقة الملاحة المحاكية
                    if (bridgeReader != null) {
                        bridgeReader.injectManualTag(inputCode, null);
                    } else {
                        sendTagToMiraServer(inputCode, "-50");
                    }
                }
            });
        }

        LvTags = (ListView) getView().findViewById(R.id.LvTags);
        adapter = new MyAdapter(mContext);
        
        // 🟢 ضغطة طويلة على Clear لإرسال قراءة تجريبية
        BtClear.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                String mockEpc = "E28011700000020123456789";
                if (bridgeReader != null) {
                    bridgeReader.injectManualTag(mockEpc, null);
                } else {
                    sendTagToMiraServer(mockEpc, "-65");
                }
                Toast.makeText(mContext, "تم إرسال قراءة تجريبية لـ MIRA: " + mockEpc, Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        LvTags.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                adapter.setSelectItem(position);
                adapter.notifyDataSetInvalidated();
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

    private Button btnSetFilter;

    private void initFilter(View view) {
        layout_filter = (ViewGroup) view.findViewById(R.id.layout_filter);
        layout_filter.setVisibility(View.GONE);
        cbFilter = (CheckBox) view.findViewById(R.id.cbFilter);
        cbFilter.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                layout_filter.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
        });

        final EditText etOffset = (EditText) view.findViewById(R.id.etPtr);
        final EditText etLen = (EditText) view.findViewById(R.id.etLen);
        final EditText etData = (EditText) view.findViewById(R.id.etData);
        final RadioButton rbEPC = (RadioButton) view.findViewById(R.id.rbEPC);
        final RadioButton rbTID = (RadioButton) view.findViewById(R.id.rbTID);
        final RadioButton rbUser = (RadioButton) view.findViewById(R.id.rbUser);

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

        btnSetFilter = (Button) view.findViewById(R.id.btSet);
        btnSetFilter.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                int filterBank = RFIDWithUHFUART.Bank_EPC;
                if (rbEPC.isChecked()) {
                    filterBank = RFIDWithUHFUART.Bank_EPC;
                } else if (rbTID.isChecked()) {
                    filterBank = RFIDWithUHFUART.Bank_TID;
                } else if (rbUser.isChecked()) {
                    filterBank = RFIDWithUHFUART.Bank_USER;
                }
                
                if (etLen.getText().toString().isEmpty() || etOffset.getText().toString().isEmpty()) {
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
                cbFilter.setChecked(false);
            }
        });

        rbEPC.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) { if (rbEPC.isChecked()) etOffset.setText("32"); }
        });
        rbTID.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) { if (rbTID.isChecked()) etOffset.setText("0"); }
        });
        rbUser.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) { if (rbUser.isChecked()) etOffset.setText("0"); }
        });
    }

    private void initEPCTamperAlarm(View view) {
        cbEPC_Tam = (CheckBox) view.findViewById(R.id.cbEPC_Tam);
        cbEPC_Tam.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!isChecked) {
                    mContext.mReader.setEPCMode();
                }
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        stopInventory();
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
            
            // 🟢 التوصيل التلقائي لـ MIRA API عند أي مسح ليزري أو إدخال يدوي
            sendTagToMiraServer(epc, info.getRssi());
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
        mContext.tagList.clear();
        adapter.notifyDataSetChanged();
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

    private void readTag() {
        cbFilter.setChecked(false);
        if (BtInventory.getText().equals(mContext.getString(R.string.btInventory))) {
            switch (inventoryFlag) {
                case 0:
                    startTime = SystemClock.elapsedRealtime();
                    UHFTAGInfo uhftagInfo = mContext.mReader.inventorySingleTag();
                    if (uhftagInfo != null) {
                        addDataToList(uhftagInfo);
                        setTotalTime();
                        mContext.playSound(1);
                    } else {
                        mContext.showToast(R.string.uhf_msg_inventory_fail);
                    }
                    break;
                case 1:
                    mContext.mReader.setInventoryCallback(new IUHFInventoryCallback() {
                        @Override
                        public void callback(UHFTAGInfo uhftagInfo) {
                            Message msg = handler.obtainMessage();
                            msg.obj = uhftagInfo;
                            msg.what = 1;
                            handler.sendMessage(msg);
                            playSoundThread.play();
                        }
                    });
                    playSoundThread.cleanData();

                    InventoryParameter inventoryParameter = new InventoryParameter();
                    inventoryParameter.setResultData(new InventoryParameter.ResultData().setNeedPhase(cbPhase.isChecked()));
                    if (mContext.mReader.startInventoryTag(inventoryParameter)) {
                        String time = etTime.getText().toString();
                        if (time.length() > 0 && time.startsWith(".")) {
                            etTime.setText("");
                            time = "";
                        }
                        if (!time.isEmpty()) {
                            maxRunTime = (int) (Float.parseFloat(time) * 1000);
                            clearData();
                        } else {
                            maxRunTime = Long.parseLong(etTime.getHint().toString()) * 1000;
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

    private void setTotalTime() {
        float useTime = (SystemClock.elapsedRealtime() - startTime) / 1000.0F;
        tvTime.setText(NumberTool.getPointDouble(1, useTime) + "s");
    }

    private void setViewEnabled(boolean enabled) {
        RbInventorySingle.setEnabled(enabled);
        RbInventoryLoop.setEnabled(enabled);
        cbFilter.setEnabled(enabled);
        btnSetFilter.setEnabled(enabled);
        cbEPC_Tam.setEnabled(enabled);
        cbPhase.setEnabled(enabled);
    }

    private void stopInventory() {
        handler.removeMessages(MSG_STOP);
        if (mContext.loopFlag) {
            mContext.loopFlag = false;
            setViewEnabled(true);
            if (mContext.mReader.stopInventory()) {
                BtInventory.setText(mContext.getString(R.string.btInventory));
            } else {
                mContext.showToast(R.string.uhf_msg_inventory_stop_fail);
            }
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

                if (mContext.loopFlag) {
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
// 🟢 دالة الاتصال بالخادم وتحديث بطاقة MIRA في الشاشة
private void sendTagToMiraServer(final String epc, final String rssi) {
    if (getActivity() != null) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (cardMiraResult != null) {
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
                
                // ✅ سجل الاستجابة للتصحيح
                Log.d(TAG, "MIRA Response Code: " + responseCode);
                Log.d(TAG, "MIRA Response Body: " + jsonResponseStr);

                if (getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (layoutMiraLoading != null) layoutMiraLoading.setVisibility(View.GONE);
                            if (layoutMiraDetails != null) layoutMiraDetails.setVisibility(View.VISIBLE);

                            try {
                                JSONObject jsonObject = new JSONObject(jsonResponseStr);
                                
                                // ✅ استخراج البيانات من الهيكل الصحيح
                                JSONObject decision = jsonObject.optJSONObject("decision");
                                JSONObject item = jsonObject.optJSONObject("item");
                                
                                String decisionMessage = "";
                                String itemTitle = "";
                                String itemStatus = "";
                                String itemKarat = "";
                                double itemWeight = 0.0;
                                boolean allowed = false;
                                
                                if (decision != null) {
                                    decisionMessage = decision.optString("message", "غير معروف");
                                    allowed = decision.optBoolean("allowed", false);
                                }
                                
                                if (item != null) {
                                    itemTitle = item.optString("title", "غير محدد");
                                    itemStatus = item.optString("status", "");
                                    itemKarat = item.optString("karat", "");
                                    itemWeight = item.optDouble("weight", 0.0);
                                }

                                // ✅ تحديث حالة البطاقة
                                if (tvMiraStatus != null) {
                                    if (allowed && item != null) {
                                        tvMiraStatus.setText("✅ خروج مصرح");
                                        tvMiraStatus.setTextColor(Color.parseColor("#2E7D32"));
                                    } else if (!allowed && item != null) {
                                        tvMiraStatus.setText("🚨 غير مصرح بالخروج");
                                        tvMiraStatus.setTextColor(Color.RED);
                                    } else {
                                        tvMiraStatus.setText("⚠️ " + decisionMessage);
                                        tvMiraStatus.setTextColor(Color.parseColor("#FF9800"));
                                    }
                                }

                                // ✅ اسم المنتج مع التفاصيل
                                if (tvMiraProductName != null) {
                                    StringBuilder productInfo = new StringBuilder();
                                    
                                    if (!itemTitle.isEmpty() && !itemTitle.equals("غير محدد")) {
                                        productInfo.append("📦 ").append(itemTitle);
                                    } else {
                                        productInfo.append("📦 قطعة غير مسجلة");
                                    }
                                    
                                    if (!itemKarat.isEmpty()) {
                                        productInfo.append("\n💎 عيار: ").append(itemKarat);
                                    }
                                    
                                    if (itemWeight > 0) {
                                        productInfo.append("\n⚖️ الوزن: ").append(itemWeight).append(" غرام");
                                    }
                                    
                                    tvMiraProductName.setText(productInfo.toString());
                                }

                                // ✅ الكود مع الحالة
                                if (tvMiraEpcGtin != null) {
                                    StringBuilder epcInfo = new StringBuilder();
                                    epcInfo.append("🏷️ EPC: ").append(epc);
                                    
                                    if (!itemStatus.isEmpty()) {
                                        String statusArabic = "";
                                        switch (itemStatus) {
                                            case "sold":
                                                statusArabic = "مباع";
                                                break;
                                            case "available":
                                                statusArabic = "متاح";
                                                break;
                                            case "reserved":
                                                statusArabic = "محجوز";
                                                break;
                                            default:
                                                statusArabic = itemStatus;
                                        }
                                        epcInfo.append("\n📋 الحالة: ").append(statusArabic);
                                    }
                                    
                                    tvMiraEpcGtin.setText(epcInfo.toString());
                                }

                            } catch (Exception e) {
                                Log.e(TAG, "JSON Parse Error: " + e.getMessage(), e);
                                if (tvMiraStatus != null) {
                                    tvMiraStatus.setText("⚠️ خطأ في تحليل البيانات");
                                    tvMiraStatus.setTextColor(Color.RED);
                                }
                                if (tvMiraProductName != null) {
                                    tvMiraProductName.setText("الرد: " + jsonResponseStr);
                                }
                            }
                        }
                    });
                }

            } catch (final Exception e) {
                Log.e(TAG, "MIRA Connection Error", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (layoutMiraLoading != null) layoutMiraLoading.setVisibility(View.GONE);
                            if (layoutMiraDetails != null) layoutMiraDetails.setVisibility(View.VISIBLE);
                            if (tvMiraStatus != null) {
                                tvMiraStatus.setText("❌ خطأ في الاتصال: " + e.getMessage());
                                tvMiraStatus.setTextColor(Color.RED);
                            }
                            if (tvMiraProductName != null) {
                                tvMiraProductName.setText("تأكد من اتصال الإنترنت");
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
