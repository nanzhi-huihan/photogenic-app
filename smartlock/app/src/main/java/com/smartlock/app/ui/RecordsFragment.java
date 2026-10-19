package com.smartlock.app.ui;

import android.graphics.Color;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 记录查询页面
 */
public class RecordsFragment extends Fragment implements BleManager.BleCallback {

    private BleManager bleManager;
    private Handler handler = new Handler(Looper.getMainLooper());

    private TextView tvTotalCount, tvTodayCount;
    private LinearLayout recordsList;
    private final List<LockRecord> records = new ArrayList<>();
    private int filterMethod = 0; // 0=全部

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

        tvTotalCount = view.findViewById(R.id.tv_total_count);
        tvTodayCount = view.findViewById(R.id.tv_today_count);
        recordsList = view.findViewById(R.id.records_list);

        view.findViewById(R.id.btn_refresh).setOnClickListener(v -> {
            if (!bleManager.isConnected()) { showToast("请先连接蓝牙"); return; }
            bleManager.send(BtProtocol.CMD_GET_RECORDS, new byte[]{BtProtocol.toByte(filterMethod)});
            showToast("正在获取记录...");
        });

        view.findViewById(R.id.btn_filter).setOnClickListener(v -> showFilterDialog());

        view.findViewById(R.id.btn_clear).setOnClickListener(v -> {
            if (!bleManager.isConnected()) { showToast("请先连接蓝牙"); return; }
            bleManager.send(BtProtocol.CMD_CLEAR_RECORDS, null);
            records.clear();
            renderRecords();
            showToast("已清空记录");
        });

        renderRecords();
    }

    @Override
    public void onResume() {
        super.onResume();
        bleManager.setCallback(this);
    }

    private void showFilterDialog() {
        String[] methods = {"全部", "密码", "刷卡", "指纹", "人脸", "蓝牙APP", "出门按钮"};
        new android.app.AlertDialog.Builder(getContext())
                .setTitle("筛选开锁方式")
                .setItems(methods, (dialog, which) -> {
                    filterMethod = which;
                    renderRecords();
                })
                .show();
    }

    private void renderRecords() {
        recordsList.removeAllViews();
        if (records.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("📭 暂无记录");
            empty.setTextColor(0x80FFFFFF);
            empty.setPadding(0, 60, 0, 20);
            empty.setGravity(android.view.Gravity.CENTER);
            recordsList.addView(empty);
        } else {
            int displayCount = 0;
            for (LockRecord r : records) {
                if (filterMethod != 0 && r.getMethod().code != filterMethod) continue;
                if (displayCount >= 100) break; // 最多显示100条
                displayCount++;

                View item = LayoutInflater.from(getContext()).inflate(R.layout.item_record, recordsList, false);
                TextView tag = item.findViewById(R.id.tv_tag);
                TextView detail = item.findViewById(R.id.tv_detail);
                TextView userId = item.findViewById(R.id.tv_user_id);
                TextView time = item.findViewById(R.id.tv_time);

                tag.setText(r.getMethod().label);
                tag.setBackgroundColor(r.getMethod().color);

                String methodLabel = r.getMethod().label;
                detail.setText(methodLabel + " 开锁");

                if (r.getUserId() != null && !r.getUserId().isEmpty()) {
                    userId.setText("ID: " + r.getUserId());
                    userId.setVisibility(TextView.VISIBLE);
                } else {
                    userId.setVisibility(TextView.GONE);
                }

                SimpleDateFormat sdf = new SimpleDateFormat("MM-dd\nHH:mm:ss", Locale.getDefault());
                time.setText(sdf.format(new Date(r.getTimestamp() * 1000L)));

                recordsList.addView(item);
            }
            if (displayCount == 0) {
                TextView empty = new TextView(getContext());
                empty.setText("📭 没有符合条件的记录");
                empty.setTextColor(0x80FFFFFF);
                empty.setPadding(0, 40, 0, 20);
                recordsList.addView(empty);
            }
        }

        // 更新统计
        tvTotalCount.setText(String.valueOf(records.size()));
        // 计算今日
        long todayStart = System.currentTimeMillis() / 1000 / 86400 * 86400;
        long todayCount = 0;
        for (LockRecord r : records) {
            if (r.getTimestamp() >= todayStart) todayCount++;
        }
        tvTodayCount.setText(String.valueOf(todayCount));
    }

    private void showToast(String msg) {
        android.widget.Toast.makeText(getContext(), msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    // ==================== BLE 回调 ====================

    @Override public void onScanResult(String n, String a, int r) {}
    @Override public void onScanFinished() {}
    @Override public void onConnecting() {}
    @Override public void onConnected(String deviceName) {}
    @Override public void onDisconnected() {}

    @Override
    public void onDataReceived(byte cmd, byte[] data) {
        handler.post(() -> {
            if (cmd == BtProtocol.CMD_GET_RECORDS) {
                records.clear();
                // 格式: [totalHi][totalLo][recCount] [id1][method1][timestamp4][userIdLen1][userId1...] ...
                if (data.length >= 3) {
                    int total = BtProtocol.bytesToShort(data, 0);
                    int count = BtProtocol.unsigned(data[2]);
                    int offset = 3;
                    for (int i = 0; i < count && offset + 5 < data.length; i++) {
                        int id = BtProtocol.unsigned(data[offset++]);
                        byte method = data[offset++];
                        if (offset + 3 < data.length) {
                            long ts = BtProtocol.bytesToInt(data, offset);
                            offset += 4;
                            int uidLen = (offset < data.length) ? BtProtocol.unsigned(data[offset++]) : 0;
                            StringBuilder uid = new StringBuilder();
                            for (int j = 0; j < uidLen && offset < data.length; j++) {
                                uid.append((char) (data[offset++] & 0xFF));
                            }
                            records.add(new LockRecord(id, ts,
                                    LockRecord.Method.fromCode(method), uid.toString()));
                        }
                    }
                }
                // 按时间倒序排列
                records.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
                renderRecords();
                showToast("已获取 " + records.size() + " 条记录");
            }
        });
    }

    @Override public void onWriteSuccess() {}
    @Override public void onError(String message) {
        handler.post(() -> showToast(message));
    }
}
