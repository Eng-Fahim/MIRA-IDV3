package com.example.uhf.fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.example.uhf.R;

public class MiraDashboardFragment extends KeyDwonFragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mira_dashboard, container, false);
        
        // تشخيص مباشر
        TextView tvGreeting = view.findViewById(R.id.tvGreeting);
        TextView tvTotalItems = view.findViewById(R.id.tvTotalItems);
        
        if (tvGreeting != null) {
            tvGreeting.setText("✅ tvGreeting موجود");
            tvGreeting.setTextColor(Color.WHITE);
            tvGreeting.setTextSize(20);
        } else {
            // العنصر غير موجود في XML
        }
        
        if (tvTotalItems != null) {
            tvTotalItems.setText("✅ tvTotalItems موجود");
            tvTotalItems.setTextColor(Color.WHITE);
        }
        
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void myOnKeyDwon() {}
}
