package com.mira.bridge.ui.settings;

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

public class SettingsFragment extends Fragment {

    private EditText etApiUrl, etApiKey, etGateId;
    private Button btnSave, btnTest;
    private TextView tvStatus;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        etApiUrl = view.findViewById(R.id.etApiUrl);
        etApiKey = view.findViewById(R.id.etApiKey);
        etGateId = view.findViewById(R.id.etGateId);
        btnSave = view.findViewById(R.id.btnSave);
        btnTest = view.findViewById(R.id.btnTest);
        tvStatus = view.findViewById(R.id.tvStatus);

        // تحميل الإعدادات الحالية
        etApiUrl.setText(MiraApp.getInstance().getPrefs().getApiUrl());
        etApiKey.setText(MiraApp.getInstance().getPrefs().getApiKey());
        etGateId.setText(MiraApp.getInstance().getPrefs().getGateId());

        btnSave.setOnClickListener(v -> saveSettings());
        btnTest.setOnClickListener(v -> testConnection());
    }

    private void saveSettings() {
        MiraApp.getInstance().getPrefs().putString("api_url", etApiUrl.getText().toString());
        MiraApp.getInstance().getPrefs().putString("api_key", etApiKey.getText().toString());
        MiraApp.getInstance().getPrefs().putString("gate_id", etGateId.getText().toString());
        
        JsonObject settings = new JsonObject();
        settings.addProperty("api_url", etApiUrl.getText().toString());
        settings.addProperty("api_key", etApiKey.getText().toString());
        settings.addProperty("gate_id", etGateId.getText().toString());
        
        MiraApp.getInstance().getApiClient().saveSettings(settings, new MiraApiClient.ApiCallback() {
            @Override public void onSuccess(int code, String response) {
                getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "✅ تم الحفظ", Toast.LENGTH_SHORT).show());
            }
            @Override public void onError(String error) {
                getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "✅ تم الحفظ محلياً", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void testConnection() {
        tvStatus.setText("🟡 جاري الاختبار...");
        MiraApp.getInstance().getApiClient().getDashboardData(new MiraApiClient.ApiCallback() {
            @Override
            public void onSuccess(int code, String response) {
                getActivity().runOnUiThread(() -> {
                    tvStatus.setText("🟢 متصل بـ MIRA ID ✓");
                });
            }
            @Override
            public void onError(String error) {
                getActivity().runOnUiThread(() -> {
                    tvStatus.setText("🔴 فشل الاتصال");
                });
            }
        });
    }
          }
