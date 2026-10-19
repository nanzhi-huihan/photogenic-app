package com.smartlock.app;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.smartlock.app.ble.BleManager;
import com.smartlock.app.ui.CardsFragment;
import com.smartlock.app.ui.HomeFragment;
import com.smartlock.app.ui.RecordsFragment;
import com.smartlock.app.ui.SettingsFragment;

/**
 * 主页面 — 底部导航 + 4个Fragment
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_BT_PERMISSIONS = 100;

    private BottomNavigationView bottomNav;
    private Fragment homeFrag, recordsFrag, cardsFrag, settingsFrag;
    private Fragment currentFrag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化 BLE
        BleManager.getInstance().init(this);

        bottomNav = findViewById(R.id.bottom_nav);

        // 初始化 Fragment
        homeFrag = new HomeFragment();
        recordsFrag = new RecordsFragment();
        cardsFrag = new CardsFragment();
        settingsFrag = new SettingsFragment();

        // 默认显示首页
        switchFragment(homeFrag);
        bottomNav.setSelectedItemId(R.id.nav_home);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                switchFragment(homeFrag);
                return true;
            } else if (id == R.id.nav_records) {
                switchFragment(recordsFrag);
                return true;
            } else if (id == R.id.nav_cards) {
                switchFragment(cardsFrag);
                return true;
            } else if (id == R.id.nav_settings) {
                switchFragment(settingsFrag);
                return true;
            }
            return false;
        });

        checkPermissions();
    }

    private void switchFragment(Fragment target) {
        if (target == currentFrag) return;
        androidx.fragment.app.FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        if (currentFrag != null) tx.hide(currentFrag);
        if (!target.isAdded()) {
            tx.add(R.id.fragment_container, target);
        } else {
            tx.show(target);
        }
        tx.commit();
        currentFrag = target;
    }

    private void checkPermissions() {
        if (!BleManager.getInstance().checkBleSupport()) {
            android.widget.Toast.makeText(this, "设备不支持BLE蓝牙", android.widget.Toast.LENGTH_LONG).show();
            return;
        }

        if (!BleManager.getInstance().isBluetoothEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(intent);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] perms = {
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
            boolean need = false;
            for (String p : perms) {
                if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                    need = true;
                    break;
                }
            }
            if (need) {
                ActivityCompat.requestPermissions(this, perms, REQ_BT_PERMISSIONS);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        BleManager.getInstance().disconnect();
    }
}
