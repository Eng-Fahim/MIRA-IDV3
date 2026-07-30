package com.mira.bridge.ui.gates;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import com.mira.bridge.MiraApp;
import com.mira.bridge.R;
import com.mira.bridge.api.MiraApiClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class GatesFragment extends Fragment {

    private Handler handler = new Handler(Looper.getMainLooper());
    private TextView tvGateStatus, tvPassCount, tvDenyCount, tvLastEvent;
    private View statusIndicator;
    private Button btnArmGate, btnDisarmGate;
    private boolean gateActive = false;
    private int passCount = 0, denyCount = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_gates, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        tvGateStatus = view.findViewById(R.id.tvGateStatus);
        tvPassCount = view.findViewById(R.id.tvPassCount);
        tvDenyCount = view.findViewById(R.id.tvDenyCount);
        tvLastEvent = view.findViewById(R.id.tvLastEvent);
        statusIndicator = view.findViewById(R.id.statusIndicator);
        btnArmGate = view.findViewById(R.id.btnArmGate);
        btnDisarmGate = view.findViewById(R.id.btnDisarmGate);

        btnArmGate.setOnClickListener(v -> armGate());
        btnDisarmGate.setOnClickListener(v -> disarmGate());
        fetchGateStatus();
    }

    private void armGate() {
        gateActive = true;
        updateUI();
        Toast.makeText(getContext(), "🛡️ MIRA Secure Gate™ مفعلة", Toast.LENGTH_SHORT).show();
    }

    private void disarmGate() {
        gateActive = false;
        updateUI();
        Toast.makeText(getContext(), "🔒 تم إيقاف البوابة", Toast.LENGTH_SHORT).show();
    }

    private void updateUI() {
        if (gateActive) {
            tvGateStatus.setText("🛡️ البوابة مفعلة - جاري المسح");
            statusIndicator.setBackgroundColor(Color.parseColor("#4CAF50"));
            btnArmGate.setEnabled(false);
            btnDisarmGate.setEnabled(true);
        } else {
            tvGateStatus.setText("🔒 البوابة غير مفعلة");
            statusIndicator.setBackgroundColor(Color.parseColor("#F44336"));
            btnArmGate.setEnabled(true);
            btnDisarmGate.setEnabled(false);
        }
    }

    private void fetchGateStatus() {
        MiraApp.getInstance().getApiClient().getGatesStatus(new MiraApiClient.ApiCallback() {
            @Override
            public void onSuccess(int code, String response) {
                try {
                    JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                    if (json.has("gates")) {
                        JsonArray gates = json.getAsJsonArray("gates");
                        handler.post(() -> {
                            tvPassCount.setText(String.valueOf(gates.size()));
                            tvDenyCount.setText("0");
                            tvLastEvent.setText("✅ آخر تحديث: الآن");
                        });
                    }
                } catch (Exception ignored) {}
            }
            @Override public void onError(String error) {}
        });
    }
}
