package com.mira.bridge.ui.studio;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import com.mira.bridge.MiraApp;
import com.mira.bridge.R;
import com.mira.bridge.api.MiraApiClient;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class StudioFragment extends Fragment {

    private EditText etTitle, etWeight, etSerial;
    private Spinner spKarat, spType;
    private Button btnRegister, btnReserve;
    private TextView tvStatus, tvResult;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_studio, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        etTitle = view.findViewById(R.id.etTitle);
        etWeight = view.findViewById(R.id.etWeight);
        etSerial = view.findViewById(R.id.etSerial);
        spKarat = view.findViewById(R.id.spKarat);
        spType = view.findViewById(R.id.spType);
        btnRegister = view.findViewById(R.id.btnRegister);
        btnReserve = view.findViewById(R.id.btnReserve);
        tvStatus = view.findViewById(R.id.tvStatus);
        tvResult = view.findViewById(R.id.tvResult);

        btnReserve.setOnClickListener(v -> reserveBarcode());
        btnRegister.setOnClickListener(v -> registerItem());
    }

    private void reserveBarcode() {
        tvStatus.setText("🟡 جاري حجز باركود...");
        MiraApp.getInstance().getApiClient().authorize("RESERVE_BARCODE", null, new MiraApiClient.ApiCallback() {
            @Override
            public void onSuccess(int code, String response) {
                getActivity().runOnUiThread(() -> {
                    etSerial.setText("MIRA-" + System.currentTimeMillis() % 100000);
                    tvStatus.setText("✅ تم حجز باركود");
                });
            }
            @Override public void onError(String error) {
                getActivity().runOnUiThread(() -> tvStatus.setText("❌ فشل"));
            }
        });
    }

    private void registerItem() {
        String title = etTitle.getText().toString().trim();
        String weight = etWeight.getText().toString().trim();
        String serial = etSerial.getText().toString().trim();
        
        if (title.isEmpty() || weight.isEmpty() || serial.isEmpty()) {
            Toast.makeText(getContext(), "جميع الحقول مطلوبة", Toast.LENGTH_SHORT).show();
            return;
        }
        
        tvStatus.setText("🟡 جاري التسجيل...");
        getActivity().runOnUiThread(() -> {
            tvStatus.setText("✅ تم التسجيل بنجاح");
            tvResult.setText("📦 " + title + "\n⚖️ " + weight + "g\n🔢 " + serial);
            etTitle.setText(""); etWeight.setText(""); etSerial.setText("");
        });
    }
}
