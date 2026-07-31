package com.mira.bridge;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.mira.bridge.ui.dashboard.DashboardFragment;
import com.mira.bridge.ui.scan.ScanFragment;
import com.mira.bridge.ui.studio.StudioFragment;
import com.mira.bridge.ui.gates.GatesFragment;
import com.mira.bridge.ui.settings.SettingsFragment;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottomNavigation);

        if (savedInstanceState == null) {
            loadFragment(new DashboardFragment());
        }

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int id = item.getItemId();
            if (id == R.id.nav_dashboard) fragment = new DashboardFragment();
            else if (id == R.id.nav_scan) fragment = new ScanFragment();
            else if (id == R.id.nav_studio) fragment = new StudioFragment();
            else if (id == R.id.nav_gates) fragment = new GatesFragment();
            else if (id == R.id.nav_settings) fragment = new SettingsFragment();
            
            if (fragment != null) { loadFragment(fragment); return true; }
            return false;
        });
    }

    public void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragmentContainer, fragment).commit();
    }

    public void navigateTo(int menuId) {
        bottomNav.setSelectedItemId(menuId);
    }
}
