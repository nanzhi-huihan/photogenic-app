package com.smartlock.app.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.smartlock.app.R;
import com.smartlock.app.ble.BleManager;
import com.smartlock.app.model.LockRecord;
import com.smartlock.app.protocol.BtProtocol;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 首页 — 开锁按钮 + 密码解锁 + 最近记录
 */
public class HomeFragment extends Fragment implements BleManager.BleCallback {

    private BleManager bleManager;
    private Handler handler = new Handler(Looper.getMainLooper());

    private TextView tvStatus, tvDot;
    private TextView tvDeviceName;
    private Button btnConnect, btnUnlock;
    private EditText etPassword;
    private Button btnSendPwd;
    private LinearLayout recentList;
    private boolean isLocked = true;

    private final List<LockRecord> records = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bleManager = BleManager.getInstance();
        bleManager.setCallback(this);

        tvStatus = view.findViewById(R.id.tv_status);
        tvDot = view.findViewById(R.id.tv_dot);
        tvDeviceName = view.findViewById(R.id.tv_device_name);
        btnConnect = view.findViewById(R.id.btn_connect);
        btnUnlock = view.findViewById(R.id.btn_unlock);
        etPassword = view.findViewById(R.id.et_password);
        btnSendPwd = view.findViewById(R.id.btn_send_pwd);
        recentList = view.findViewById(R.id.recent_list);

        btnConnect.setOnClickListener(v -> toggleConnection());
        btnUnlock.setOnClickListener(v -> toggleLock());
        btnSendPwd.setOnClickListener(v -> sendPassword());

        updateUI(bleManager.isConnected());

        // 加载已有记录
        renderRecords();
    }

    @Override
    public void onResume() {
        super.onResume();
        bleManager.setCallback(this);
        updateUI(bleManager.isConnected());
    }

    private void toggleConnection() {
        if (bleManager.isConnected()) {
            bleManager.disconnect();
        } else {
            bleManager.startScan();
            showScanDialog();
        }
    }

    private AlertDialog scanDialog;
    private final List<String[]> scanResults = new ArrayList<>();

    private void showScanDialog() {
        scanResults.clear();
        TextView tvScan = new TextView(getContext());
        tvScan.setText("正在搜索蓝牙设备...");
        tvScan.setPadding(48, 40, 48, 20);
        tvScan.setTextSize(14);

        scanDialog = new AlertDialog.Builder(getContext())
                .setTitle("选择蓝牙设备")
                .setView(tvScan)
                .setNegativeButton("取消", (d, w) -> bleManager.stopScan())
                .create();
        scanDialog.show();
    }

    private void dismissScanDialog() {
        if (scanDialog != null && scanDialog.isShowing()) {
            scanDialog.dismiss();
        }
    }

    private void toggleLock() {
        if (!bleManager.isConnected()) {
            showToast("请先连接蓝牙");
            return;
        }
        if (isLocked) {
            bleManager.send(BtProtocol.CMD_UNLOCK, new byte[0]);
            showToast("正在开锁...");
            handler.postDelayed(() -> {
                isLocked = false;
                updateUnlockUI(true);
                addRecord(LockRecord.Type.BLUETOOTH, "远程开锁");
            }, 800);
        } else {
            bleManager.send(BtProtocol.CMD_LOCK, new byte[0]);
            showToast("正在关锁...");
            handler.postDelayed(() -> {
                isLocked = true;
                updateUnlockUI(false);
            }, 800);
        }
    }

    private void sendPassword() {
        if (!bleManager.isConnected()) {
            showToast("请先连接蓝牙");
            return;
        }
        String pwd = etPassword.getText().toString().trim();
        if (pwd.length() < 4) {
            showToast("请输入至少4位密码");
            return;
        }
        byte[] data = pwd.getBytes();
        bleManager.send(BtProtocol.CMD_PASSWORD, data);
        showToast("密码已发送");
        addRecord(LockRecord.Type.PASSWORD, "蓝牙远程密码开锁");
        etPassword.setText("");
    }

    private void updateUI(boolean connected) {
        btnConnect.setText(connected ? "断开连接" : "连接蓝牙");
        btnUnlock.setEnabled(connected);
        btnSendPwd.setEnabled(connected);
        if (connected) {
            tvStatus.setText("已连接");
            tvStatus.setTextColor(0xFF4CAF50);
            tvDot.setText("●");
            tvDot.setTextColor(0xFF4CAF50);
        } else {
            tvStatus.setText("未连接");
            tvStatus.setTextColor(0xFF888888);
            tvDot.setText("●");
            tvDot.setTextColor(0xFFF44336);
            tvDeviceName.setText("--");
        }
    }

    private void updateUnlockUI(boolean opened) {
        btnUnlock.setText(opened ? "🔓 已开锁" : "🔒 点击解锁");
        btnUnlock.setBackgroundColor(opened ? 0xFF4CAF50 : 0x33FFFFFF);
    }

    private void addRecord(LockRecord.Type type, String detail) {
        SimpleDateFormat df = new SimpleDateFormat("MM-dd", Locale.CHINA);
        SimpleDateFormat tf = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);
        Date now = new Date();
        records.add(0, new LockRecord(type, detail, df.format(now), tf.format(now)));
        if (records.size() > 50) records.remove(records.size() - 1);
        renderRecords();
    }

    private void renderRecords() {
        recentList.removeAllViews();
        if (records.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("📭 暂无记录");
            empty.setTextColor(0x80FFFFFF);
            empty.setPadding(0, 40, 0, 20);
            recentList.addView(empty);
            return;
        }
        // 只显示最近5条
        int count = Math.min(records.size(), 5);
        for (int i = 0; i < count; i++) {
            LockRecord r = records.get(i);
            View item = LayoutInflater.from(getContext()).inflate(R.layout.item_record, recentList, false);
            TextView tag = item.findViewById(R.id.tv_tag);
            TextView detail = item.findViewById(R.id.tv_detail);
            TextView time = item.findViewById(R.id.tv_time);

            tag.setText(r.type.label);
            tag.setBackgroundColor(r.type.color);
            detail.setText(r.detail);
            time.setText(r.date + " " + r.time);

            recentList.addView(item);
        }
    }

    private void showToast(String msg) {
        android.widget.Toast.makeText(getContext(), msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    // ===== BleCallback =====

    @Override
    public void onScanResult(String name, String address, int rssi) {
        handler.post(() -> {
            scanResults.add(new String[]{name, address});
            if (scanDialog != null && scanDialog.isShowing()) {
                String[] items = new String[scanResults.size()];
                for (int i = 0; i < scanResults.size(); i++) {
                    items[i] = scanResults.get(i)[0] + "\n  " + scanResults.get(i)[1] + "  " + rssi + "dBm";
                }
                scanDialog.setTitle("选择蓝牙设备 (已发现 " + scanResults.size() + " 个)");
                // 更新列表
                TextView tv = new TextView(getContext());
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < scanResults.size(); i++) {
                    sb.append(i + ". ").append(scanResults.get(i)[0])
                      .append("\n   ").append(scanResults.get(i)[1])
                      .append("\n\n");
                }
                tv.setText(sb.toString());
                tv.setPadding(48, 20, 48, 20);
                tv.setTextSize(13);
                scanDialog.setView(tv);

                scanDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "取消",
                        (dialog, which) -> bleManager.stopScan());
                scanDialog.setButton(AlertDialog.BUTTON_POSITIVE, "连接",
                        (dialog, which) -> {
                            if (!scanResults.isEmpty()) {
                                bleManager.connect(scanResults.get(scanResults.size() - 1)[1]);
                            }
                        });
            }
        });
    }

    @Override
    public void onScanFinished() {
        handler.post(() -> {
            dismissScanDialog();
            if (scanResults.isEmpty()) {
                showToast("未找到设备，请确认HC-05已开启");
            }
        });
    }

    @Override
    public void onConnecting() {
        handler.post(() -> {
            dismissScanDialog();
            tvStatus.setText("连接中...");
            tvStatus.setTextColor(0xFFFF9800);
        });
    }

    @Override
    public void onConnected(String deviceName) {
        handler.post(() -> {
            tvDeviceName.setText(deviceName);
            updateUI(true);
            showToast("蓝牙连接成功！");
            addRecord(LockRecord.Type.BLUETOOTH, "手机蓝牙连接成功");
        });
    }

    @Override
    public void onDisconnected() {
        handler.post(() -> {
            updateUI(false);
            showToast("蓝牙已断开");
        });
    }

    @Override
    public void onDataReceived(byte cmd, byte[] data) {
        handler.post(() -> {
            switch (cmd) {
                case BtProtocol.CMD_STATUS_REPORT:
                    if (data.length >= 1) {
                        if (data[0] == 0x01) {
                            isLocked = false;
                            updateUnlockUI(true);
                        } else if (data[0] == 0x02) {
                            isLocked = true;
                            updateUnlockUI(false);
                        }
                    }
                    break;
                case BtProtocol.CMD_NOTIFICATION:
                    if (data.length >= 1) {
                        String msg = BtProtocol.bytesToString(data);
                        handleNotification(data[0], msg);
                    }
                    break;
            }
        });
    }

    @Override
    public void onWriteSuccess() { }

    @Override
    public void onError(String message) {
        handler.post(() -> {
            dismissScanDialog();
            showToast(message);
        });
    }

    private void handleNotification(byte type, String msg) {
        String title;
        switch (type) {
            case BtProtocol.NOTIFY_STRANGER:
                title = "⚠️ 陌生人检测";
                break;
            case BtProtocol.NOTIFY_DOOR_OPEN:
                title = "⚠️ 门未关提醒";
                break;
            case BtProtocol.NOTIFY_HOME:
                title = "🏠 到家通知";
                break;
            case BtProtocol.NOTIFY_LATE:
                title = "⏰ 超时未归";
                break;
            default:
                title = "通知";
        }
        new AlertDialog.Builder(getContext())
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("知道了", null)
                .show();
    }
}
