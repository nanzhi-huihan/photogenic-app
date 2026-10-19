package com.smartlock.app.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.smartlock.app.R;
import com.smartlock.app.ble.BleManager;
import com.smartlock.app.protocol.BtProtocol;

/**
 * 设置页面 — 人群模式 + 报警设置 + 系统信息
 */
public class SettingsFragment extends Fragment implements BleManager.BleCallback {

    private BleManager bleManager;
    private Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout modeSelector;
    private Button btnMode0, btnMode1, btnMode2;
    private CheckBox cbStranger, cbDoorOpen, cbHome;
    private TextView tvBtStatus, tvDeviceName;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bleManager = BleManager.getInstance();
        bleManager.setCallback(this);

        modeSelector = view.findViewById(R.id.mode_selector);
        btnMode0 = view.findViewById(R.id.btn_mode_0);
        btnMode1 = view.findViewById(R.id.btn_mode_1);
        btnMode2 = view.findViewById(R.id.btn_mode_2);
        cbStranger = view.findViewById(R.id.cb_stranger);
        cbDoorOpen = view.findViewById(R.id.cb_door_open);
        cbHome = view.findViewById(R.id.cb_home);
        tvBtStatus = view.findViewById(R.id.tv_bt_status);
        tvDeviceName = view.findViewById(R.id.tv_device_info);

        btnMode0.setOnClickListener(v -> setMode(0));
        btnMode1.setOnClickListener(v -> setMode(1));
        btnMode2.setOnClickListener(v -> setMode(2));

        updateInfo();
    }

    @Override
    public void onResume() {
        super.onResume();
        bleManager.setCallback(this);
        updateInfo();
    }

    private void setMode(int mode) {
        btnMode0.setBackgroundColor(mode == 0 ? 0x334CAF50 : 0x1AFFFFFF);
        btnMode1.setBackgroundColor(mode == 1 ? 0x334CAF50 : 0x1AFFFFFF);
        btnMode2.setBackgroundColor(mode == 2 ? 0x334CAF50 : 0x1AFFFFFF);

        if (!bleManager.isConnected()) {
            showToast("请先连接蓝牙");
            return;
        }
        bleManager.send(BtProtocol.CMD_SET_MODE, new byte[]{(byte) mode});

        String[] names = {"通用", "老年人", "儿童"};
        showToast("模式已切换: " + names[mode]);
    }

    private void updateInfo() {
        if (bleManager.isConnected()) {
            tvBtStatus.setText("已连接");
            tvBtStatus.setTextColor(0xFF4CAF50);
            tvDeviceName.setText("设备已连接");
        } else {
            tvBtStatus.setText("未连接");
            tvBtStatus.setTextColor(0xFF888888);
            tvDeviceName.setText("--");
        }
    }

    private void showToast(String msg) {
        android.widget.Toast.makeText(getContext(), msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override public void onScanResult(String name, String address, int rssi) {}
    @Override public void onScanFinished() {}
    @Override public void onConnecting() {}
    @Override public void onConnected(String deviceName) {
        handler.post(() -> {
            tvBtStatus.setText("已连接");
            tvBtStatus.setTextColor(0xFF4CAF50);
            tvDeviceName.setText(deviceName);
        });
    }
    @Override public void onDisconnected() {
        handler.post(() -> {
            tvBtStatus.setText("未连接");
            tvBtStatus.setTextColor(0xFF888888);
            tvDeviceName.setText("--");
        });
    }
    @Override public void onDataReceived(byte cmd, byte[] data) {}
    @Override public void onWriteSuccess() {}
    @Override public void onError(String message) { showToast(message); }
}
