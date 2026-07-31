package com.mira.bridge;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragmentContainer, new DashboardFragment())
            .commit();

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment f = null;
            int id = item.getItemId();
            if (id == R.id.nav_dashboard) f = new DashboardFragment();
            else if (id == R.id.nav_scan) f = new ScanFragment();
            else if (id == R.id.nav_studio) f = new StudioFragment();
            else if (id == R.id.nav_gates) f = new GatesFragment();
            else if (id == R.id.nav_settings) f = new SettingsFragment();
            if (f != null) getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer, f).commit();
            return true;
        });
    }
}

class DashboardFragment extends Fragment {
    public DashboardFragment() {}
    @Override public View onCreateView(LayoutInflater i, ViewGroup c, Bundle b) {
        View v = i.inflate(R.layout.fragment_dashboard, c, false);
        ((TextView)v.findViewById(R.id.tvGreeting)).setText("مرحباً بك");
        return v;
    }
}

class ScanFragment extends Fragment {
    public ScanFragment() {}
    @Override public View onCreateView(LayoutInflater i, ViewGroup c, Bundle b) {
        return i.inflate(R.layout.fragment_scan, c, false);
    }
}

class StudioFragment extends Fragment {
    public StudioFragment() {}
    @Override public View onCreateView(LayoutInflater i, ViewGroup c, Bundle b) {
        return i.inflate(R.layout.fragment_studio, c, false);
    }
}

class GatesFragment extends Fragment {
    public GatesFragment() {}
    @Override public View onCreateView(LayoutInflater i, ViewGroup c, Bundle b) {
        return i.inflate(R.layout.fragment_gates, c, false);
    }
}

class SettingsFragment extends Fragment {
    public SettingsFragment() {}
    @Override public View onCreateView(LayoutInflater i, ViewGroup c, Bundle b) {
        return i.inflate(R.layout.fragment_settings, c, false);
    }
}
