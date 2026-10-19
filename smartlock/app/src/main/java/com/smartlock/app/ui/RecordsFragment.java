package com.smartlock.app.ui;

import android.os.Bundle;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 记录页面 — 全部开锁记录
 */
public class RecordsFragment extends Fragment implements BleManager.BleCallback {

    private BleManager bleManager;
    private LinearLayout recordsList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_records, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bleManager = BleManager.getInstance();
        bleManager.setCallback(this);

        recordsList = view.findViewById(R.id.records_list);
        Button btnRefresh = view.findViewById(R.id.btn_refresh);
        Button btnClear = view.findViewById(R.id.btn_clear);

        btnRefresh.setOnClickListener(v -> {
            if (!bleManager.isConnected()) {
                showToast("请先连接蓝牙");
                return;
            }
            bleManager.send(BtProtocol.CMD_GET_RECORDS, new byte[0]);
            showToast("已请求记录");
        });

        btnClear.setOnClickListener(v -> {
            // 本地清空记录
            showToast("已清空");
        });

        renderEmpty();
    }

    @Override
    public void onResume() {
        super.onResume();
        bleManager.setCallback(this);
    }

    private void renderEmpty() {
        recordsList.removeAllViews();
        TextView empty = new TextView(getContext());
        empty.setText("📭 暂无记录");
        empty.setTextColor(0x80FFFFFF);
        empty.setPadding(0, 60, 0, 20);
        recordsList.addView(empty);
    }

    private void showToast(String msg) {
        android.widget.Toast.makeText(getContext(), msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override public void onScanResult(String name, String address, int rssi) {}
    @Override public void onScanFinished() {}
    @Override public void onConnecting() {}
    @Override public void onConnected(String deviceName) {}
    @Override public void onDisconnected() {}
    @Override public void onDataReceived(byte cmd, byte[] data) {
        if (cmd == BtProtocol.CMD_GET_RECORDS) {
            // 解析记录数据（STM32发过来的记录）
        }
    }
    @Override public void onWriteSuccess() {}
    @Override public void onError(String message) {
        showToast(message);
    }
}
