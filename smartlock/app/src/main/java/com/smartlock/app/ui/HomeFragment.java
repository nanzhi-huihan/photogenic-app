package com.smartlock.app.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.smartlock.app.R;
import com.smartlock.app.ble.BleManager;
import com.smartlock.app.model.LockRecord;
import com.smartlock.app.protocol.BtProtocol;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页 — 门状态 + 五种解锁方式 + 温湿度 + 事件提示
 */
public class HomeFragment extends Fragment implements BleManager.BleCallback {

    private BleManager bleManager;
    private Handler handler = new Handler(Looper.getMainLooper());

    // UI 控件
    private TextView tvStatus, tvDot, tvDeviceName;
    private TextView tvDoorStatus, tvTempHumi, tvEvent;
    private Button btnConnect, btnUnlock;
    private Button btnMethodPwd, btnMethodCard, btnMethodFinger, btnMethodFace;
    private Button btnMethodBt, btnMethodButton;
    private Button btnRefreshStatus, btnRefreshTemp, btnQueryRecords;

    private boolean isLocked = true;
    private boolean doorClosed = true;

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

        // 连接状态
        tvStatus = view.findViewById(R.id.tv_status);
        tvDot = view.findViewById(R.id.tv_dot);
        tvDeviceName = view.findViewById(R.id.tv_device_name);
        tvDoorStatus = view.findViewById(R.id.tv_door_status);
        tvTempHumi = view.findViewById(R.id.tv_temp_humi);
        tvEvent = view.findViewById(R.id.tv_event);

        // 主按钮
        btnConnect = view.findViewById(R.id.btn_connect);
        btnUnlock = view.findViewById(R.id.btn_unlock);

        // 解锁方式
        btnMethodPwd = view.findViewById(R.id.btn_method_pwd);
        btnMethodCard = view.findViewById(R.id.btn_method_card);
        btnMethodFinger = view.findViewById(R.id.btn_method_finger);
        btnMethodFace = view.findViewById(R.id.btn_method_face);
        btnMethodBt = view.findViewById(R.id.btn_method_bt);
        btnMethodButton = view.findViewById(R.id.btn_method_button);

        // 快捷面板
        btnRefreshStatus = view.findViewById(R.id.btn_refresh_status);
        btnRefreshTemp = view.findViewById(R.id.btn_refresh_temp);
        btnQueryRecords = view.findViewById(R.id.btn_query_records);

        // 绑定事件
        btnConnect.setOnClickListener(v -> toggleConnection());
        btnUnlock.setOnClickListener(v -> remoteUnlock());
        btnMethodPwd.setOnClickListener(v -> showPasswordDialog());
        btnMethodCard.setOnClickListener(v -> triggerCardScan());
        btnMethodFinger.setOnClickListener(v -> triggerFinger());
        btnMethodFace.setOnClickListener(v -> triggerFace());
        btnMethodBt.setOnClickListener(v -> remoteUnlock());
        btnMethodButton.setOnClickListener(v -> sendDoorButton());

        btnRefreshStatus.setOnClickListener(v -> queryStatus());
        btnRefreshTemp.setOnClickListener(v -> queryTempHumi());
        btnQueryRecords.setOnClickListener(v -> {
            // 切换到记录页
            if (getActivity() instanceof com.smartlock.app.MainActivity) {
                ((com.smartlock.app.MainActivity) getActivity).switchToTab(2);
            }
        });

        updateUI(bleManager.isConnected());
    }

    @Override
    public void onResume() {
        super.onResume();
        bleManager.setCallback(this);
        updateUI(bleManager.isConnected());
    }

    // ==================== 连接管理 ====================

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
        tvScan.setText("正在搜索 BLE 设备...");
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

    // ==================== 五种解锁方式 ====================

    /**
     * 远程开锁 (蓝牙APP方式)
     */
    private void remoteUnlock() {
        if (!checkConnected()) return;
        bleManager.send(BtProtocol.CMD_UNLOCK, new byte[0]);
        showEvent("📱 正在远程开锁...");
    }

    /**
     * 密码解锁 — 弹窗输入密码
     */
    private void showPasswordDialog() {
        if (!checkConnected()) return;

        // 创建输入框
        final TextView input = new TextView(getContext());
        input.setTextSize(18);
        input.setTextColor(0xFFFFFFFF);
        input.setPadding(40, 30, 40, 10);
        input.setBackgroundColor(0xFF333333);

        AlertDialog dlg = new AlertDialog.Builder(getContext())
                .setTitle("🔑 密码解锁")
                .setMessage("发送开锁密码到门锁")
                .setPositiveButton("开锁", (d, w) -> {
                    // 默认密码方式开锁指令
                    bleManager.send(BtProtocol.CMD_PASSWORD_UNLOCK,
                            BtProtocol.stringToBytes("1234")); // 演示默认密码
                    showEvent("🔑 密码已发送");
                })
                .setNegativeButton("取消", null)
                .create();
        dlg.show();
    }

    /**
     * 触发 RFID 刷卡
     */
    private void triggerCardScan() {
        if (!checkConnected()) return;
        bleManager.send(BtProtocol.CMD_CARD_GET_LIST, new byte[0]);
        showEvent("💳 请将卡片靠近读卡器...");
    }

    /**
     * 触发指纹识别
     */
    private void triggerFinger() {
        if (!checkConnected()) return;
        // 发送指纹验证请求
        bleManager.send(BtProtocol.CMD_ACK_OK, new byte[]{0x01}); // 触发指纹扫描
        showEvent("👆 请按压指纹模块...");
    }

    /**
     * 触发人脸识别
     */
    private void triggerFace() {
        if (!checkConnected()) return;
        bleManager.send(BtProtocol.CMD_FACE_RECOGNIZE, new byte[0]);
        showEvent("😊 请将人脸对准摄像头...");
    }

    /**
     * 出门按钮 — 模拟内侧开门
     */
    private void sendDoorButton() {
        if (!checkConnected()) return;
        bleManager.send(BtProtocol.CMD_UNLOCK, new byte[]{BtProtocol.METHOD_BUTTON});
        showEvent("🚪 出门按钮触发");
    }

    // ==================== 状态查询 ====================

    private void queryStatus() {
        if (!checkConnected()) return;
        bleManager.send(BtProtocol.CMD_GET_STATUS, new byte[0]);
        showEvent("🔄 正在查询门状态...");
    }

    private void queryTempHumi() {
        if (!checkConnected()) return;
        bleManager.send(BtProtocol.CMD_GET_TEMP_HUMI, new byte[0]);
        showEvent("🌡 正在读取温湿度...");
    }

    // ==================== UI 更新 ====================

    private void updateUI(boolean connected) {
        btnConnect.setText(connected ? "断开连接" : "🔗 连接蓝牙");
        btnUnlock.setEnabled(connected);
        btnMethodPwd.setEnabled(connected);
        btnMethodCard.setEnabled(connected);
        btnMethodFinger.setEnabled(connected);
        btnMethodFace.setEnabled(connected);
        btnMethodBt.setEnabled(connected);
        btnMethodButton.setEnabled(connected);
        btnRefreshStatus.setEnabled(connected);
        btnRefreshTemp.setEnabled(connected);
        btnQueryRecords.setEnabled(connected);

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
            tvDoorStatus.setText("⛔ 已关闭");
            tvTempHumi.setText("--°C / --%");
        }
    }

    private void updateDoorUI() {
        if (doorClosed) {
            tvDoorStatus.setText(isLocked ? "🔒 已锁定" : "🔓 已开锁");
            tvDoorStatus.setTextColor(isLocked ? 0xFFFF5722 : 0xFF4CAF50);
        } else {
            tvDoorStatus.setText("🚪 门已打开");
            tvDoorStatus.setTextColor(0xFFFF9800);
        }
    }

    private void showEvent(String msg) {
        tvEvent.setText(msg);
        tvEvent.setVisibility(TextView.VISIBLE);
        handler.postDelayed(() -> {
            if (tvEvent != null) tvEvent.setVisibility(TextView.GONE);
        }, 3000);
    }

    private boolean checkConnected() {
        if (!bleManager.isConnected()) {
            showToast("请先连接蓝牙");
            return false;
        }
        return true;
    }

    private void showToast(String msg) {
        android.widget.Toast.makeText(getContext(), msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    // ==================== BLE 回调 ====================

    @Override
    public void onScanResult(String name, String address, int rssi) {
        handler.post(() -> {
            scanResults.add(new String[]{name, address});
            if (scanDialog != null && scanDialog.isShowing()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < scanResults.size(); i++) {
                    sb.append(i + 1).append(". ").append(scanResults.get(i)[0])
                      .append("\n   ").append(scanResults.get(i)[1])
                      .append("  (").append(rssi).append("dBm)\n\n");
                }
                TextView tv = new TextView(getContext());
                tv.setText(sb.toString());
                tv.setPadding(48, 20, 48, 20);
                tv.setTextSize(13);
                tv.setTextColor(0xFFFFFFFF);
                scanDialog.setTitle("选择设备 (已发现 " + scanResults.size() + " 个)");
                scanDialog.setView(tv);

                scanDialog.setButton(AlertDialog.BUTTON_POSITIVE, "连接最后一个",
                        (d, w) -> {
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
                showToast("未找到设备，请确认BLE已开启");
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
            // 连接成功后自动查询状态
            handler.postDelayed(() -> {
                bleManager.send(BtProtocol.CMD_GET_STATUS, new byte[0]);
                bleManager.send(BtProtocol.CMD_GET_TEMP_HUMI, new byte[0]);
            }, 500);
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
                case BtProtocol.CMD_GET_STATUS:
                    handleStatusData(data);
                    break;
                case BtProtocol.CMD_GET_TEMP_HUMI:
                    handleTempHumiData(data);
                    break;
                case BtProtocol.CMD_EVT_UNLOCKED:
                    if (data.length >= 1) {
                        byte method = data[0];
                        isLocked = false;
                        updateDoorUI();
                        showEvent(getMethodName(method) + " 开锁成功！");
                    }
                    break;
                case BtProtocol.CMD_EVT_LOCKED:
                    isLocked = true;
                    updateDoorUI();
                    break;
                case BtProtocol.CMD_EVT_DOOR_OPEN:
                    doorClosed = false;
                    updateDoorUI();
                    showEvent("🚪 门已打开");
                    break;
                case BtProtocol.CMD_EVT_DOOR_CLOSED:
                    doorClosed = true;
                    updateDoorUI();
                    break;
                case BtProtocol.CMD_EVT_DOOR_TIMEOUT:
                    showEvent("⚠️ 门未关超时！");
                    break;
                case BtProtocol.CMD_EVT_TAMPER:
                    showEvent("🚨 撬门报警！");
                    break;
                case BtProtocol.CMD_EVT_FAIL_ALARM:
                    showEvent("🚨 连续失败报警！");
                    break;
                case BtProtocol.CMD_EVT_LOW_BATTERY:
                    if (data.length >= 1) {
                        showEvent("🔋 低电量: " + BtProtocol.unsigned(data[0]) + "%");
                    }
                    break;
                case BtProtocol.CMD_EVT_FINGER_OK:
                    showEvent("👆 指纹验证通过");
                    break;
                case BtProtocol.CMD_EVT_FINGER_FAIL:
                    showEvent("👆 指纹验证失败");
                    break;
                case BtProtocol.CMD_EVT_CARD_OK:
                    showEvent("💳 刷卡验证通过");
                    break;
                case BtProtocol.CMD_EVT_CARD_FAIL:
                    showEvent("💳 刷卡验证失败");
                    break;
                case BtProtocol.CMD_EVT_FACE_OK:
                    showEvent("😊 人脸识别通过");
                    break;
                case BtProtocol.CMD_EVT_FACE_FAIL:
                    showEvent("😊 人脸识别失败");
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

    // ==================== 数据解析 ====================

    private void handleStatusData(byte[] data) {
        if (data.length >= 2) {
            doorClosed = (data[0] == 0x01);
            isLocked = (data[1] == 0x01);
            updateDoorUI();
        } else if (data.length >= 1) {
            // 兼容旧协议
            isLocked = (data[0] == 0x02);
            updateDoorUI();
        }
    }

    private void handleTempHumiData(byte[] data) {
        if (data.length >= 4) {
            // 温度2字节(除以10) + 湿度2字节(除以10)
            int tempRaw = BtProtocol.bytesToShort(data, 0);
            int humiRaw = BtProtocol.bytesToShort(data, 2);
            float temp = tempRaw / 10.0f;
            float humi = humiRaw / 10.0f;
            tvTempHumi.setText(String.format("%.1f°C / %.1f%%", temp, humi));
            tvTempHumi.setTextColor(temp > 35 ? 0xFFFF5722 : 0xFF4FC3F7);
        } else if (data.length >= 2) {
            // 简化格式: 1字节温度 + 1字节湿度
            tvTempHumi.setText(data[0] + "°C / " + BtProtocol.unsigned(data[1]) + "%");
        }
    }

    private String getMethodName(byte code) {
        switch (code) {
            case 0x01: return "🔑 密码";
            case 0x02: return "💳 刷卡";
            case 0x03: return "👆 指纹";
            case 0x04: return "😊 人脸";
            case 0x05: return "📱 蓝牙";
            case 0x06: return "🚪 出门按钮";
            default: return "未知";
        }
    }

}
