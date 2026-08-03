package com.mira.rfid.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.mira.rfid.R;
import com.mira.rfid.activity.UHFMainActivity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UHFReadWriteFragment extends Fragment implements View.OnClickListener {

    private UHFMainActivity mContext;
    private EditText etData_filter, etPtr_filter, etReadData;
    private CheckBox cb_filter;
    private RadioButton rbEPC_filter, rbTID_filter, rbUser_filter;
    private Button btnRead, btnWrite, btnSearchMira;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_uhf_read_write, container, false);
        mContext = (UHFMainActivity) getActivity();

        initViews(view);
        return view;
    }

    private void initViews(View view) {
        etData_filter = view.findViewById(R.id.etData_filter);
        etPtr_filter = view.findViewById(R.id.etPtr_filter);
        etReadData = view.findViewById(R.id.etReadData);

        cb_filter = view.findViewById(R.id.cb_filter);

        rbEPC_filter = view.findViewById(R.id.rbEPC_filter);
        rbTID_filter = view.findViewById(R.id.rbTID_filter);
        rbUser_filter = view.findViewById(R.id.rbUser_filter);

        if (rbEPC_filter != null) rbEPC_filter.setOnClickListener(this);
        if (rbTID_filter != null) rbTID_filter.setOnClickListener(this);
        if (rbUser_filter != null) rbUser_filter.setOnClickListener(this);

        btnRead = view.findViewById(R.id.btnRead);
        btnWrite = view.findViewById(R.id.btnWrite);
        btnSearchMira = view.findViewById(R.id.btnSearchMira);

        if (btnRead != null) btnRead.setOnClickListener(this);
        if (btnWrite != null) btnWrite.setOnClickListener(this);
        if (btnSearchMira != null) btnSearchMira.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (!isAdded() || mContext == null) return;

        int id = v.getId();
        if (id == R.id.rbEPC_filter) {
            if (etPtr_filter != null) etPtr_filter.setText("32");
        } else if (id == R.id.rbTID_filter) {
            if (etPtr_filter != null) etPtr_filter.setText("0");
        } else if (id == R.id.rbUser_filter) {
            if (etPtr_filter != null) etPtr_filter.setText("0");
        } else if (id == R.id.btnRead) {
            readTagData();
        } else if (id == R.id.btnWrite) {
            writeTagData();
        } else if (id == R.id.btnSearchMira) {
            searchMiraItemBySerial();
        }
    }

    private void readTagData() {
        Toast.makeText(mContext, "جاري قراءة بيانات التاق...", Toast.LENGTH_SHORT).show();
    }

    private void writeTagData() {
        Toast.makeText(mContext, "جاري كتابة البيانات على التاق...", Toast.LENGTH_SHORT).show();
    }

    private void searchMiraItemBySerial() {
        if (etReadData == null) return;
        String serialNumber = etReadData.getText().toString().trim();

        if (TextUtils.isEmpty(serialNumber)) {
            Toast.makeText(mContext, "الرجاء إدخال أو قراءة رقم السيريال أولاً", Toast.LENGTH_SHORT).show();
            return;
        }

        // تحويل النص النصي للسيريال إلى HEX لتوافقه مع عتاد الفلترة للقارئ
        String hexFormattedSerial = convertStringToHex(serialNumber);

        if (etData_filter != null) {
            etData_filter.setText(hexFormattedSerial);
        }
        if (cb_filter != null) {
            cb_filter.setChecked(true);
        }

        final String miraEndpoint = "https://ams.ibreg.org/wp-json/mira-gate/v1/item?serial=" + serialNumber;

        executorService.execute(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(miraEndpoint)
                        .get()
                        .build();

                Response response = client.newCall(request).execute();
                final String responseData = response.body() != null ? response.body().string() : "";
                boolean isSuccessful = response.isSuccessful();
                response.close();

                mainHandler.post(() -> {
                    if (isAdded() && mContext != null) {
                        if (isSuccessful && !TextUtils.isEmpty(responseData)) {
                            Toast.makeText(mContext, "تم العثور على المنتج في سجلات MIRA", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(mContext, "لم يتم العثور على السيريال في السحابة", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

            } catch (IOException e) {
                mainHandler.post(() -> {
                    if (isAdded() && mContext != null) {
                        Toast.makeText(mContext, "خطأ في الاتصال بالخادم السحابي", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private String convertStringToHex(String input) {
        if (input == null) return "";
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mContext = null;
    }
}
