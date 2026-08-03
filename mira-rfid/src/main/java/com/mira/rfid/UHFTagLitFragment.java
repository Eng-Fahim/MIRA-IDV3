package com.mira.rfid.fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mira.rfid.R; // ✅ صحيح

import com.example.uhf.activity.UHFMainActivity;
import com.example.uhf.adapter.TagLedAdapter;
import com.example.uhf.manager.MiraSettingsManager;
import com.rscja.deviceapi.entity.FilterEntity;
import com.rscja.deviceapi.entity.InventoryModeEntity;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.ConnectionStatus;
import com.rscja.deviceapi.interfaces.IUHF;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * MIRA Spotlight Fragment
 * 
 * يسمح للمستخدم بإضافة قطع لإضاءتها (LED Tag) عند المرور على البوابة
 * - بحث في MIRA ID لإضافة قطع
 * - قائمة بالقطع المحددة للإضاءة
 * - تفعيل LED Tag على القطع المحددة
 */
public class UHFTagLitFragment extends KeyDwonFragment {
    private static final String TAG = "UHFTagLitFragment";

    private UHFMainActivity context;
    private Button btnInventory, btnClear, btnAddToSpotlight;
    private CheckBox cbTagLed;
    private RecyclerView rvTagLed;
    private TagLedAdapter adapter;
    private EditText etSpotlightSearch;
    private TextView tvSpotlightCount, tvEmptySpotlight;

    private MiraSettingsManager settingsManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<UHFTAGInfo> tagList = new ArrayList<>();
    private volatile boolean inventoryFlag = false;
    private InventoryModeEntity inventoryMode = null;

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        context = (UHFMainActivity) getActivity();
        if (context == null) return;
        context.currentFragment = this;
        settingsManager = MiraSettingsManager.getInstance(context);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_uhf_tag_led, container, false);
        initView(view);
        return view;
    }

    @Override
    public void myOnKeyDwon() {
        toggleInventory();
    }

    @Override
    public void onStart() {
        super.onStart();

        if (context == null) return;

        handler.postDelayed(() -> {
            if (!isAdded() || context == null || context.mReader == null) return;
            context.mReader.setInventoryCallback(uhftagInfo -> {
                handler.post(() -> {
                    if (!isAdded() || adapter == null) return;
                    adapter.addTagInfo(uhftagInfo);
                    if (context != null) {
                        context.playSound(1);
                    }
                });
            });
        }, 400);

        if (context.mReader != null && context.mReader.getConnectStatus() == ConnectionStatus.CONNECTED) {
            new Thread(() -> {
                SystemClock.sleep(100);
                if (context != null && context.mReader != null) {
                    inventoryMode = context.mReader.getEPCAndTIDUserMode();
                }
            }).start();
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if (context != null && context.mReader != null) {
            // إزالة الـ Callback عند توقف الفراغمنت لعدم حدوث Leak
            context.mReader.setInventoryCallback(null);

            if (context.mReader.getConnectStatus() == ConnectionStatus.CONNECTED) {
                new Thread(() -> {
                    SystemClock.sleep(100);
                    if (context == null || context.mReader == null) return;

                    if (inventoryFlag) {
                        context.mReader.stopInventory();
                        inventoryFlag = false;
                        handler.post(() -> {
                            if (cbTagLed != null) cbTagLed.setEnabled(true);
                            if (btnInventory != null) btnInventory.setText("💡 ابدأ الإضاءة");
                        });
                    }
                    if (inventoryMode != null) {
                        context.mReader.setEPCAndTIDUserMode(inventoryMode);
                    }
                    context.mReader.setFilter(0, 0, 0, "");
                }).start();
            }
        }
    }

    private void initView(View view) {
        btnInventory = view.findViewById(R.id.btnInventory);
        btnClear = view.findViewById(R.id.btnClear);
        cbTagLed = view.findViewById(R.id.cbTagLed);
        rvTagLed = view.findViewById(R.id.rvTagList);
        etSpotlightSearch = view.findViewById(R.id.etSpotlightSearch);
        btnAddToSpotlight = view.findViewById(R.id.btnAddToSpotlight);
        tvSpotlightCount = view.findViewById(R.id.tvSpotlightCount);
        tvEmptySpotlight = view.findViewById(R.id.tvEmptySpotlight);

        if (btnInventory != null) btnInventory.setOnClickListener(v -> toggleInventory());
        if (btnClear != null) btnClear.setOnClickListener(v -> toggleClear());

        // 🟢 إضافة قطعة من MIRA ID
        if (btnAddToSpotlight != null) {
            btnAddToSpotlight.setOnClickListener(v -> {
                if (etSpotlightSearch == null) return;
                String code = etSpotlightSearch.getText().toString().trim();
                if (TextUtils.isEmpty(code)) {
                    Toast.makeText(context, "أدخل Serial أو GTIN-13", Toast.LENGTH_SHORT).show();
                    return;
                }
                searchAndAddItem(code);
            });
        }

        adapter = new TagLedAdapter(tagList);
        adapter.setTagLedClickListener((position, isChecked) -> {
            if (inventoryFlag) {
                if (context != null) context.showToast("أوقف الإضاءة أولاً");
                return false;
            }
            return true;
        });

        if (rvTagLed != null) {
            rvTagLed.setAdapter(adapter);
            rvTagLed.setLayoutManager(new LinearLayoutManager(getContext()));
        }

        updateEmptyState();
    }

    // ============================================
    // 🟢 البحث في MIRA ID وإضافة القطعة
    // ============================================
    private void searchAndAddItem(String code) {
        if (context != null) Toast.makeText(context, "🔍 جاري البحث...", Toast.LENGTH_SHORT).show();

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
                JSONObject barcode = json.optJSONObject("barcode");

                handler.post(() -> {
                    if (!isAdded() || getActivity() == null) return;

                    if (item != null || barcode != null) {
                        String serial = barcode != null ? barcode.optString("serial_no", code) :
                                       item != null ? item.optString("serial", code) : code;
                        String title = item != null ? item.optString("title", "غير معروف") : "باركود غير مرتبط";
                        String epc = serial;

                        // إنشاء UHFTAGInfo للقائمة
                        UHFTAGInfo tagInfo = new UHFTAGInfo();
                        tagInfo.setEPC(epc);
                        tagInfo.setExtraData("TITLE", title);
                        tagInfo.setExtraData("SERIAL", serial);
                        tagInfo.setExtraData("CHECKED", "1");
                        tagInfo.setExtraData("STATE", "0");

                        // التحقق من عدم التكرار
                        boolean exists = false;
                        for (UHFTAGInfo t : tagList) {
                            if (Objects.equals(t.getEPC(), epc)) {
                                exists = true;
                                break;
                            }
                        }

                        if (!exists) {
                            tagList.add(tagInfo);
                            if (adapter != null) adapter.notifyItemInserted(tagList.size() - 1);
                            updateEmptyState();
                            if (etSpotlightSearch != null) etSpotlightSearch.setText("");
                            Toast.makeText(context, "✅ تمت إضافة: " + title, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context, "⚠️ القطعة موجودة بالفعل", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(context, "❌ القطعة غير موجودة في MIRA ID", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Search error: " + e.getMessage());
                handler.post(() -> {
                    if (!isAdded() || getActivity() == null) return;
                    Toast.makeText(context, "❌ خطأ في الاتصال بالسيرفر", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void updateEmptyState() {
        if (tvEmptySpotlight != null) {
            tvEmptySpotlight.setVisibility(tagList.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (tvSpotlightCount != null) {
            tvSpotlightCount.setText(tagList.size() + " قطعة");
        }
    }

    private void toggleClear() {
        if (inventoryFlag) {
            if (context != null) context.showToast("أوقف الإضاءة أولاً");
            return;
        }
        if (adapter != null) adapter.clear();
        updateEmptyState();
        if (context != null) Toast.makeText(context, "🗑️ تم مسح القائمة", Toast.LENGTH_SHORT).show();
    }

    private void toggleInventory() {
        if (inventoryFlag) {
            stopInventory();
        } else {
            if (tagList.isEmpty()) {
                if (context != null) Toast.makeText(context, "⚠️ أضف قطعاً للإضاءة أولاً", Toast.LENGTH_SHORT).show();
                return;
            }
            startInventory();
        }
    }

    private void startInventory() {
        if (context == null || context.mReader == null) return;

        List<FilterEntity> filterList = new ArrayList<>();
        for (int i = 0; i < tagList.size(); i++) {
            UHFTAGInfo uhftagInfo = tagList.get(i);
            if (Objects.equals(uhftagInfo.getExtraData("STATE"), "1")) {
                uhftagInfo.setExtraData("STATE", "0");
                if (adapter != null) adapter.notifyItemChanged(i);
            }
            if (Objects.equals(uhftagInfo.getExtraData("CHECKED"), "1") && !TextUtils.isEmpty(uhftagInfo.getEPC())) {
                String epcHex = uhftagInfo.getEPC().trim();
                FilterEntity filterEntity = new FilterEntity(
                    IUHF.Bank_EPC, 32,
                    epcHex.length() * 4,
                    epcHex
                );
                filterList.add(filterEntity);
            }
        }

        if (filterList.isEmpty()) {
            Toast.makeText(context, "⚠️ حدد قطعاً للإضاءة", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!context.mReader.setFilter(filterList)) {
            context.showToast(R.string.fail);
            return;
        }

        boolean isLedChecked = cbTagLed != null && cbTagLed.isChecked();
        if (!context.mReader.setEPCAndTIDUserMode(
                new InventoryModeEntity.Builder()
                    .setMode(isLedChecked ? InventoryModeEntity.MODE_LED_TAG : InventoryModeEntity.MODE_EPC)
                    .build()
        )) {
            context.showToast(R.string.fail);
            return;
        }

        if (context.mReader.startInventoryTag()) {
            inventoryFlag = true;
            if (cbTagLed != null) cbTagLed.setEnabled(false);
            if (btnInventory != null) {
                btnInventory.setText("⏹ إيقاف الإضاءة");
                btnInventory.setBackgroundColor(Color.parseColor("#F44336"));
            }
            Toast.makeText(context, "💡 جاري إضاءة " + filterList.size() + " قطعة...", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopInventory() {
        if (context == null || context.mReader == null) return;

        if (context.mReader.stopInventory()) {
            inventoryFlag = false;
            if (cbTagLed != null) cbTagLed.setEnabled(true);
            if (btnInventory != null) {
                btnInventory.setText("💡 ابدأ الإضاءة");
                btnInventory.setBackgroundColor(Color.parseColor("#FF9800"));
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (inventoryFlag) {
            stopInventory();
        }
    }
}
