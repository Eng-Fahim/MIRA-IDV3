package com.mira.bridge;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

// 🟢 استيراد كلاس R الخاص بالتطبيق (استخدم com.example.uhf.R إذا لم تقم بتغيير namespace في build.gradle بعد)
import com.mira.bridge.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, new DashboardFragment())
                .commit();
        }

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment f = null;
            int id = item.getItemId();
            if (id == R.id.nav_dashboard) f = new DashboardFragment();
            else if (id == R.id.nav_scan) f = new ScanFragment();
            else if (id == R.id.nav_studio) f = new StudioFragment();
            else if (id == R.id.nav_gates) f = new GatesFragment();
            else if (id == R.id.nav_settings) f = new SettingsFragment();

            if (f != null) {
                getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, f)
                    .commit();
            }
            return true;
        });
    }
}

// 🟢 الـ Fragments الملحقة بالواجهة

class DashboardFragment extends Fragment {
    public DashboardFragment() {}
    @Override 
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_dashboard, container, false);
        TextView tvGreeting = v.findViewById(R.id.tvGreeting);
        if (tvGreeting != null) {
            tvGreeting.setText("مرحباً بك");
        }
        return v;
    }
}

class ScanFragment extends Fragment {
    public ScanFragment() {}
    @Override 
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scan, container, false);
    }
}

class StudioFragment extends Fragment {
    public StudioFragment() {}
    @Override 
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_studio, container, false);
    }
}

class GatesFragment extends Fragment {
    public GatesFragment() {}
    @Override 
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_gates, container, false);
    }
}

class SettingsFragment extends Fragment {
    public SettingsFragment() {}
    @Override 
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }
}
