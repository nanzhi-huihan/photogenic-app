package com.smartlock.app.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import com.smartlock.app.protocol.BtProtocol;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BLE 连接管理器 — 单例
 * 管理扫描、连接、数据收发
 */
@SuppressLint("MissingPermission")
public class BleManager {

    // BLE UUID (与网页版保持一致)
    public static final UUID SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b3fb5");
    public static final UUID CHAR_UUID    = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b3fb5");

    private static final long SCAN_TIMEOUT = 10000; // 10秒扫描超时
    private static final long CONNECT_TIMEOUT = 8000; // 8秒连接超时

    private static volatile BleManager instance;

    private Context appContext;
    private BluetoothManager btManager;
    private BluetoothAdapter btAdapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic characteristic;
    private Handler mainHandler;

    private volatile boolean isConnected = false;
    private volatile boolean isScanning = false;

    // 数据接收缓冲区（处理分包）
    private final List<Byte> rxBuffer = new ArrayList<>();

    // 回调
    private BleCallback callback;

    public interface BleCallback {
        void onScanResult(String name, String address, int rssi);
        void onScanFinished();
        void onConnecting();
        void onConnected(String deviceName);
        void onDisconnected();
        void onDataReceived(byte cmd, byte[] data);
        void onWriteSuccess();
        void onError(String message);
    }

    private BleManager() {
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static BleManager getInstance() {
        if (instance == null) {
            synchronized (BleManager.class) {
                if (instance == null) {
                    instance = new BleManager();
                }
            }
        }
        return instance;
    }

    public void init(Context context) {
        appContext = context.getApplicationContext();
        btManager = (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
    }

    public void setCallback(BleCallback cb) {
        this.callback = cb;
    }

    public boolean isConnected() { return isConnected; }
    public BluetoothAdapter getAdapter() { return btAdapter; }

    // ===================== 扫描 =====================

    public void startScan() {
        if (btAdapter == null || !btAdapter.isEnabled()) {
            if (callback != null) callback.onError("请先开启蓝牙");
            return;
        }
        scanner = btAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            if (callback != null) callback.onError("BLE扫描初始化失败");
            return;
        }

        isScanning = true;
        // 过滤 HC/SmartLock 前缀
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder().setDeviceName("HC").build());
        filters.add(new ScanFilter.Builder().setDeviceName("SmartLock").build());

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        try {
            scanner.startScan(filters, settings, scanCallback);
        } catch (Exception e) {
            // fallback: 不过滤
            try {
                scanner.startScan(null, settings, scanCallback);
            } catch (Exception e2) {
                if (callback != null) callback.onError("扫描启动失败: " + e2.getMessage());
                return;
            }
        }

        // 超时停止
        mainHandler.postDelayed(() -> {
            if (isScanning) stopScan();
        }, SCAN_TIMEOUT);
    }

    public void stopScan() {
        if (scanner != null && isScanning) {
            try {
                scanner.stopScan(scanCallback);
            } catch (Exception ignored) {}
        }
        isScanning = false;
        if (callback != null) callback.onScanFinished();
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String name = device.getName();
            if (name == null) return;
            // 只显示 HC / SmartLock 相关设备
            if (!name.contains("HC") && !name.contains("SmartLock") && !name.contains("BT")) return;
            if (callback != null) {
                callback.onScanResult(name, device.getAddress(), result.getRssi());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            if (callback != null) callback.onError("扫描失败，错误码: " + errorCode);
        }
    };

    // ===================== 连接 =====================

    public void connect(String address) {
        stopScan();
        if (callback != null) callback.onConnecting();

        BluetoothDevice device = btAdapter.getRemoteDevice(address);
        if (device == null) {
            if (callback != null) callback.onError("设备不存在");
            return;
        }

        // 连接超时
        mainHandler.postDelayed(() -> {
            if (!isConnected && gatt != null) {
                gatt.disconnect();
                gatt.close();
                gatt = null;
                if (callback != null) callback.onError("连接超时");
            }
        }, CONNECT_TIMEOUT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } else {
            gatt = device.connectGatt(appContext, false, gattCallback);
        }
    }

    public void disconnect() {
        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
            gatt = null;
        }
        isConnected = false;
        characteristic = null;
        rxBuffer.clear();
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onConnecting();
                });
                g.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false;
                characteristic = null;
                rxBuffer.clear();
                mainHandler.post(() -> {
                    if (callback != null) callback.onDisconnected();
                });
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = g.getService(SERVICE_UUID);
                if (service == null) {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError("未找到服务 UUID");
                    });
                    return;
                }
                characteristic = service.getCharacteristic(CHAR_UUID);
                if (characteristic == null) {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError("未找到特征值 UUID");
                    });
                    return;
                }

                // 启用通知
                g.setCharacteristicNotification(characteristic, true);

                isConnected = true;
                mainHandler.post(() -> {
                    if (callback != null) callback.onConnected(g.getDevice().getName());
                });
            } else {
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("服务发现失败");
                });
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic ch, int status) {
            mainHandler.post(() -> {
                if (callback != null) callback.onWriteSuccess();
            });
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch) {
            byte[] value = ch.getValue();
            if (value == null || value.length == 0) return;

            // 追加到缓冲区
            synchronized (rxBuffer) {
                for (byte b : value) rxBuffer.add(b);
                parseBuffer();
            }
        }
    };

    /**
     * 解析接收缓冲区中的帧
     */
    private void parseBuffer() {
        while (rxBuffer.size() >= 4) {
            // 查找帧头 0xAA
            int headerIdx = -1;
            for (int i = 0; i < rxBuffer.size(); i++) {
                if ((rxBuffer.get(i) & 0xFF) == 0xAA) {
                    headerIdx = i;
                    break;
                }
            }
            if (headerIdx < 0) {
                rxBuffer.clear(); // 没有帧头，清空
                return;
            }
            // 丢弃帧头之前的数据
            if (headerIdx > 0) {
                rxBuffer.subList(0, headerIdx).clear();
            }
            // 检查是否收到足够数据
            if (rxBuffer.size() < 3) break;
            int dataLen = rxBuffer.get(2) & 0xFF;
            int frameLen = 4 + dataLen;
            if (rxBuffer.size() < frameLen) break;

            // 提取帧
            byte[] frame = new byte[frameLen];
            for (int i = 0; i < frameLen; i++) frame[i] = rxBuffer.get(i);
            rxBuffer.subList(0, frameLen).clear();

            // 校验
            if (BtProtocol.verifyFrame(frame, frameLen)) {
                byte cmd = BtProtocol.extractCmd(frame);
                byte[] data = BtProtocol.extractData(frame, frameLen);
                mainHandler.post(() -> {
                    if (callback != null) callback.onDataReceived(cmd, data);
                });
            }
        }
    }

    // ===================== 发送 =====================

    public void send(byte cmd, byte[] data) {
        if (!isConnected || characteristic == null || gatt == null) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("请先连接蓝牙");
            });
            return;
        }
        byte[] frame = BtProtocol.buildFrame(cmd, data);
        characteristic.setValue(frame);
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        gatt.writeCharacteristic(characteristic);
    }

    public boolean checkBleSupport() {
        return appContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    public boolean isBluetoothEnabled() {
        return btAdapter != null && btAdapter.isEnabled();
    }
}
