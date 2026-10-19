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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.smartlock.app.R;
import com.smartlock.app.ble.BleManager;
import com.smartlock.app.protocol.BtProtocol;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 设置页面 — 时间校准/自动关门/报警/密码/恢复出厂
 */
public class SettingsFragment extends Fragment implements BleManager.BleCallback {

    private BleManager bleManager;
    private Handler handler = new Handler(Looper.getMainLooper());

    private TextView tvBtStatus, tvDeviceInfo, tvCurrentTime, tvAutoLockDelay;
    private CheckBox cbBuzzer, cbStranger, cbDoorOpen, cbTamper;
    private int autoLockDelay = 5;

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

        tvBtStatus = view.findViewById(R.id.tv_bt_status);
        tvDeviceInfo = view.findViewById(R.id.tv_device_info);
        tvCurrentTime = view.findViewById(R.id.tv_current_time);
        tvAutoLockDelay = view.findViewById(R.id.tv_auto_lock_delay);

        cbBuzzer = view.findViewById(R.id.cb_buzzer);
        cbStranger = view.findViewById(R.id.cb_stranger);
        cbDoorOpen = view.findViewById(R.id.cb_door_open);
        cbTamper = view.findViewById(R.id.cb_tamper);

        view.findViewById(R.id.btn_sync_time).setOnClickListener(v -> syncTime());

        view.findViewById(R.id.btn_delay_down).setOnClickListener(v -> {
            if (autoLockDelay > 0) { autoLockDelay--; updateDelayUI(); sendDelay(); }
        });
        view.findViewById(R.id.btn_delay_up).setOnClickListener(v -> {
            if (autoLockDelay < 60) { autoLockDelay++; updateDelayUI(); sendDelay(); }
        });

        cbBuzzer.setOnCheckedChangeListener((b, checked) -> {
            if (bleManager.isConnected())
                bleManager.send(BtProtocol.CMD_SET_BUZZER, new byte[]{checked ? (byte) 1 : (byte) 0});
        });

        view.findViewById(R.id.btn_set_password).setOnClickListener(v -> showSetPasswordDialog());

        view.findViewById(R.id.btn_factory_reset).setOnClickListener(v -> {
            new AlertDialog.Builder(getContext())
                .setTitle("确认恢复出厂")
                .setMessage("将清除所有卡片、指纹、人脸、记录和设置！")
                .setPositiveButton("确认恢复", (d, w) -> {
                    if (bleManager.isConnected()) {
                        bleManager.send(BtProtocol.CMD_FACTORY_RESET, null);
                        showToast("已发送恢复出厂指令");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
        });

        updateInfo();
        updateDelayUI();
    }

    @Override
    public void onResume() {
        super.onResume();
        bleManager.setCallback(this);
        updateInfo();
    }

    private void syncTime() {
        if (!bleManager.isConnected()) { showToast("请先连接蓝牙"); return; }
        SimpleDateFormat sdf = new SimpleDateFormat("yy MM dd HH mm ss", Locale.getDefault());
        Date now = new Date();
        String[] parts = sdf.format(now).split(" ");
        SimpleDateFormat daySdf = new SimpleDateFormat("u", Locale.getDefault());
        int dayOfWeek = Integer.parseInt(daySdf.format(now));

        byte[] timeData = new byte[7];
        for (int i = 0; i < 6; i++) timeData[i] = BtProtocol.toByte(Integer.parseInt(parts[i]));
        timeData[6] = BtProtocol.toByte(dayOfWeek);

        bleManager.send(BtProtocol.CMD_SET_TIME, timeData);
        showToast("时间已同步: " + formatNow());
        tvCurrentTime.setText(formatNow());
    }

    private String formatNow() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    private void updateDelayUI() { tvAutoLockDelay.setText(autoLockDelay + " 秒"); }

    private void sendDelay() {
        if (bleManager.isConnected())
            bleManager.send(BtProtocol.CMD_SET_AUTO_LOCK, new byte[]{BtProtocol.toByte(autoLockDelay)});
    }

    private void showSetPasswordDialog() {
        if (!bleManager.isConnected()) { showToast("请先连接蓝牙"); return; }
        TextView input = new TextView(getContext());
        input.setTextSize(20);
        input.setTextColor(0xFFFFFFFF);
        input.setPadding(40, 30, 40, 10);
        input.setBackgroundColor(0xFF333333);

        new AlertDialog.Builder(getContext())
            .setTitle("修改系统密码")
            .setMessage("输入新的6位数字密码")
            .setView(input)
            .setPositiveButton("确认修改", (d, w) -> {
                String pwd = input.getText().toString().trim();
                if (pwd.length() == 6 && pwd.matches("\\d{6}")) {
                    bleManager.send(BtProtocol.CMD_SET_PASSWORD, BtProtocol.stringToBytes(pwd));
                    showToast("密码已修改");
                } else { showToast("请输入6位数字密码"); }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void updateInfo() {
        if (bleManager.isConnected()) {
            tvBtStatus.setText("已连接"); tvBtStatus.setTextColor(0xFF4CAF50);
            tvDeviceInfo.setText("智能门禁 #" + android.os.Build.MODEL);
        } else {
            tvBtStatus.setText("未连接"); tvBtStatus.setTextColor(0xFF888888);
            tvDeviceInfo.setText("--");
        }
        tvCurrentTime.setText(formatNow());
    }

    private void showToast(String msg) {
        android.widget.Toast.makeText(getContext(), msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override public void onScanResult(String n, String a, int r) {}
    @Override public void onScanFinished() {}
    @Override public void onConnecting() {}
    @Override
    public void onConnected(String deviceName) {
        handler.post(() -> {
            tvBtStatus.setText("已连接"); tvBtStatus.setTextColor(0xFF4CAF50);
            tvDeviceInfo.setText(deviceName);
        });
    }
    @Override
    public void onDisconnected() {
        handler.post(() -> {
            tvBtStatus.setText("未连接"); tvBtStatus.setTextColor(0xFF888888);
            tvDeviceInfo.setText("--");
        });
    }
    @Override
    public void onDataReceived(byte cmd, byte[] data) {
        handler.post(() -> {
            if (cmd == BtProtocol.CMD_ACK_OK) showToast("操作成功");
            else if (cmd == BtProtocol.CMD_ACK_ERROR && data.length >= 1)
                showToast("操作失败: 错误码 " + BtProtocol.unsigned(data[0]));
            else if (cmd == BtProtocol.CMD_GET_TIME && data.length >= 7)
                tvCurrentTime.setText(String.format(Locale.getDefault(),
                    "%04d-%02d-%02d %02d:%02d:%02d",
                    2000 + BtProtocol.unsigned(data[0]), BtProtocol.unsigned(data[1]),
                    BtProtocol.unsigned(data[2]), BtProtocol.unsigned(data[3]),
                    BtProtocol.unsigned(data[4]), BtProtocol.unsigned(data[5])));
        });
    }
    @Override public void onWriteSuccess() {}
    @Override public void onError(String message) { handler.post(() -> showToast(message)); }
}
