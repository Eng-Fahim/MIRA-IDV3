package com.mira.bridge.ui.scan;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import com.mira.bridge.MiraApp;
import com.mira.bridge.R;
import com.mira.bridge.api.MiraApiClient;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class ScanFragment extends Fragment {

    private EditText etInput;
    private Button btnScan, btnCamera;
    private TextView tvResult, tvStatus;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scan, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        etInput = view.findViewById(R.id.etInput);
        btnScan = view.findViewById(R.id.btnScan);
        btnCamera = view.findViewById(R.id.btnCamera);
        tvResult = view.findViewById(R.id.tvResult);
        tvStatus = view.findViewById(R.id.tvStatus);

        btnScan.setOnClickListener(v -> scanTag(etInput.getText().toString().trim()));
        btnCamera.setOnClickListener(v -> openCamera());
    }

    private void scanTag(String code) {
        if (code.isEmpty()) {
            Toast.makeText(getContext(), "أدخل Serial أو GTIN-13", Toast.LENGTH_SHORT).show();
            return;
        }
        tvStatus.setText("🟡 جاري الاستعلام...");
        
        MiraApp.getInstance().getApiClient().authorize(code, "-50", new MiraApiClient.ApiCallback() {
            @Override
            public void onSuccess(int code, String response) {
                try {
                    JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                    JsonObject decision = json.getAsJsonObject("decision");
                    JsonObject item = json.getAsJsonObject("item");
                    
                    String message = decision.get("message").getAsString();
                    boolean allowed = decision.get("allowed").getAsBoolean();
                    
                    getActivity().runOnUiThread(() -> {
                        tvStatus.setText(allowed ? "✅ مصرح" : "🚨 غير مصرح");
                        tvResult.setText(message);
                        if (item != null) {
                            String title = item.has("title") ? item.get("title").getAsString() : "";
                            String karat = item.has("karat") ? item.get("karat").getAsString() : "";
                            tvResult.setText(title + "\n" + karat + " | " + message);
                        }
                    });
                } catch (Exception e) {
                    getActivity().runOnUiThread(() -> tvStatus.setText("❌ خطأ في البيانات"));
                }
            }
            @Override
            public void onError(String error) {
                getActivity().runOnUiThread(() -> tvStatus.setText("❌ خطأ اتصال"));
            }
        });
    }

    private void openCamera() {
        Toast.makeText(getContext(), "📷 الكاميرا قيد التطوير", Toast.LENGTH_SHORT).show();
    }
}
