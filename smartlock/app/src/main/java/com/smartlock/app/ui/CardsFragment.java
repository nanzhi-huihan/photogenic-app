package com.smartlock.app.ui;

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
import com.smartlock.app.model.CardInfo;
import com.smartlock.app.model.FingerprintInfo;
import com.smartlock.app.model.FaceInfo;
import com.smartlock.app.protocol.BtProtocol;

import java.util.ArrayList;
import java.util.List;

/**
 * 身份管理 — 卡片/指纹/人脸 三合一
 */
public class CardsFragment extends Fragment implements BleManager.BleCallback {

    private BleManager bleManager;
    private Handler handler = new Handler(Looper.getMainLooper());

    private Button btnTabCard, btnTabFinger, btnTabFace;
    private LinearLayout panelCard, panelFinger, panelFace;
    private LinearLayout cardsList, fingersList, facesList;

    private final List<CardInfo> cards = new ArrayList<>();
    private final List<FingerprintInfo> fingers = new ArrayList<>();
    private final List<FaceInfo> faces = new ArrayList<>();

    private int currentTab = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cards, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bleManager = BleManager.getInstance();
        bleManager.setCallback(this);

        // Tab 按钮
        btnTabCard = view.findViewById(R.id.btn_tab_card);
        btnTabFinger = view.findViewById(R.id.btn_tab_finger);
        btnTabFace = view.findViewById(R.id.btn_tab_face);

        panelCard = view.findViewById(R.id.panel_card);
        panelFinger = view.findViewById(R.id.panel_finger);
        panelFace = view.findViewById(R.id.panel_face);

        cardsList = view.findViewById(R.id.cards_list);
        fingersList = view.findViewById(R.id.fingers_list);
        facesList = view.findViewById(R.id.faces_list);

        // Tab 切换
        btnTabCard.setOnClickListener(v -> switchTab(0));
        btnTabFinger.setOnClickListener(v -> switchTab(1));
        btnTabFace.setOnClickListener(v -> switchTab(2));

        // 卡片操作
        view.findViewById(R.id.btn_add_card).setOnClickListener(v -> sendCmd(BtProtocol.CMD_CARD_ADD, null, "请将卡片靠近读卡器..."));
        view.findViewById(R.id.btn_refresh_cards).setOnClickListener(v -> sendCmd(BtProtocol.CMD_CARD_GET_LIST, null, "正在获取卡片列表..."));
        view.findViewById(R.id.btn_clear_cards).setOnClickListener(v -> sendCmd(BtProtocol.CMD_CARD_DELETE, new byte[]{(byte) 0xFF}, "已清空所有卡片"));

        // 指纹操作
        view.findViewById(R.id.btn_enroll_finger).setOnClickListener(v -> sendCmd(BtProtocol.CMD_FINGER_ENROLL, null, "正在录入指纹，请按压传感器..."));
        view.findViewById(R.id.btn_refresh_fingers).setOnClickListener(v -> sendCmd(BtProtocol.CMD_FINGER_GET_LIST, null, "正在获取指纹列表..."));
        view.findViewById(R.id.btn_clear_fingers).setOnClickListener(v -> sendCmd(BtProtocol.CMD_FINGER_CLEAR, null, "已清空所有指纹"));

        // 人脸操作
        view.findViewById(R.id.btn_enroll_face).setOnClickListener(v -> sendCmd(BtProtocol.CMD_FACE_ENROLL, null, "正在录入人脸，请对准摄像头..."));
        view.findViewById(R.id.btn_refresh_faces).setOnClickListener(v -> sendCmd(BtProtocol.CMD_FACE_GET_LIST, null, "正在获取人脸列表..."));
        view.findViewById(R.id.btn_clear_faces).setOnClickListener(v -> sendCmd(BtProtocol.CMD_FACE_CLEAR, null, "已清空所有人脸"));

        switchTab(0);
        renderAll();
    }

    @Override
    public void onResume() {
        super.onResume();
        bleManager.setCallback(this);
    }

    private void switchTab(int tab) {
        currentTab = tab;
        btnTabCard.setBackgroundResource(tab == 0 ? R.drawable.bg_mode_selected : R.drawable.bg_mode_normal);
        btnTabFinger.setBackgroundResource(tab == 1 ? R.drawable.bg_mode_selected : R.drawable.bg_mode_normal);
        btnTabFace.setBackgroundResource(tab == 2 ? R.drawable.bg_mode_selected : R.drawable.bg_mode_normal);

        panelCard.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        panelFinger.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        panelFace.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);
    }

    private void sendCmd(byte cmd, byte[] data, String toast) {
        if (!bleManager.isConnected()) {
            showToast("请先连接蓝牙");
            return;
        }
        bleManager.send(cmd, data != null ? data : new byte[0]);
        showToast(toast);
    }

    // ==================== 渲染列表 ====================

    private void renderAll() {
        renderCards();
        renderFingers();
        renderFaces();
    }

    private void renderCards() {
        cardsList.removeAllViews();
        if (cards.isEmpty()) {
            cardsList.addView(makeEmptyView("💳 暂无卡片，点击添加"));
            return;
        }
        for (CardInfo card : cards) {
            View item = LayoutInflater.from(getContext()).inflate(R.layout.item_card, cardsList, false);
            TextView nameView = item.findViewById(R.id.tv_card_name);
            TextView uidView = item.findViewById(R.id.tv_card_uid);
            TextView levelView = item.findViewById(R.id.tv_card_level);
            Button btnDel = item.findViewById(R.id.btn_del_card);
            Button btnLevel = item.findViewById(R.id.btn_card_level);

            nameView.setText(card.getName());
            uidView.setText(card.getUid());
            levelView.setText(card.getLevel().label);
            levelView.setBackgroundColor(levelColor(card.getLevel().code));

            btnLevel.setOnClickListener(v -> cycleCardLevel(card));
            btnDel.setOnClickListener(v -> {
                byte[] uidBytes = hexToBytes(card.getUid());
                sendCmd(BtProtocol.CMD_CARD_DELETE, uidBytes, "卡片已删除");
                cards.remove(card);
                renderCards();
            });
            cardsList.addView(item);
        }
    }

    private void renderFingers() {
        fingersList.removeAllViews();
        if (fingers.isEmpty()) {
            fingersList.addView(makeEmptyView("👆 暂无指纹，点击录入"));
            return;
        }
        for (FingerprintInfo f : fingers) {
            LinearLayout item = new LinearLayout(getContext());
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(android.view.Gravity.CENTER_VERTICAL);
            item.setPadding(0, 8, 0, 8);

            TextView tv = new TextView(getContext());
            tv.setText("👆 #" + f.getId() + "  " + f.getName());
            tv.setTextColor(0xFFDDFFFF);
            tv.setTextSize(14);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            tv.setLayoutParams(lp);

            Button btnDel = new Button(getContext());
            btnDel.setText("✕");
            btnDel.setTextColor(0xFFFFFFFF);
            btnDel.setBackgroundColor(0xFFF44336);
            btnDel.setOnClickListener(v -> {
                sendCmd(BtProtocol.CMD_FINGER_DELETE, new byte[]{BtProtocol.toByte(f.getId())}, "指纹已删除");
                fingers.remove(f);
                renderFingers();
            });

            item.addView(tv);
            item.addView(btnDel);
            fingersList.addView(item);
        }
    }

    private void renderFaces() {
        facesList.removeAllViews();
        if (faces.isEmpty()) {
            facesList.addView(makeEmptyView("😊 无人脸数据，点击录入"));
            return;
        }
        for (FaceInfo f : faces) {
            LinearLayout item = new LinearLayout(getContext());
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(android.view.Gravity.CENTER_VERTICAL);
            item.setPadding(0, 8, 0, 8);

            TextView tv = new TextView(getContext());
            tv.setText("😊 #" + f.getId() + "  " + f.getName());
            tv.setTextColor(0xFFDDFFFF);
            tv.setTextSize(14);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            tv.setLayoutParams(lp);

            Button btnDel = new Button(getContext());
            btnDel.setText("✕");
            btnDel.setTextColor(0xFFFFFFFF);
            btnDel.setBackgroundColor(0xFFF44336);
            btnDel.setOnClickListener(v -> {
                sendCmd(BtProtocol.CMD_FACE_DELETE, new byte[]{BtProtocol.toByte(f.getId())}, "人脸已删除");
                faces.remove(f);
                renderFaces();
            });

            item.addView(tv);
            item.addView(btnDel);
            facesList.addView(item);
        }
    }

    private TextView makeEmptyView(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextColor(0x80FFFFFF);
        tv.setPadding(0, 40, 0, 20);
        tv.setGravity(android.view.Gravity.CENTER);
        return tv;
    }

    private int levelColor(byte code) {
        switch (code) {
            case 0x01: return 0xFFE91E63; // 管理员 粉
            case 0x02: return 0xFF4CAF50; // 普通 绿
            case 0x03: return 0xFFFF9800; // 临时 橙
            case 0x04: return 0xFF888888; // 禁用 灰
            default: return 0xFF2196F3;
        }
    }

    private void cycleCardLevel(CardInfo card) {
        CardInfo.Level[] levels = CardInfo.Level.values();
        int next = (card.getLevel().ordinal() + 1) % levels.length;
        card.setLevel(levels[next]);
        byte[] data = new byte[]{BtProtocol.toByte(card.getId()), levels[next].code};
        sendCmd(BtProtocol.CMD_CARD_SET_LEVEL, data, "权限已改为 " + levels[next].label);
        renderCards();
    }

    private byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[0];
        hex = hex.replace(":", "").replace(" ", "").toUpperCase();
        int len = hex.length();
        byte[] data = new byte[Math.max(1, len / 2)];
        for (int i = 0; i < len && i < data.length * 2; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + (i + 1 < len ? Character.digit(hex.charAt(i + 1), 16) : 0));
        }
        return data;
    }

    private void showToast(String msg) {
        android.widget.Toast.makeText(getContext(), msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    // ==================== BLE 回调 ====================

    @Override public void onScanResult(String name, String address, int rssi) {}
    @Override public void onScanFinished() {}
    @Override public void onConnecting() {}
    @Override public void onConnected(String deviceName) {}
    @Override public void onDisconnected() {}

    @Override
    public void onDataReceived(byte cmd, byte[] data) {
        handler.post(() -> {
            switch (cmd) {
                case BtProtocol.CMD_CARD_GET_LIST:
                    // 解析卡片列表
                    cards.clear();
                    // 格式: [count] [id1][uidLen1][uid1...] [id2][uidLen2][uid2...] ...
                    if (data.length > 0) {
                        int count = BtProtocol.unsigned(data[0]);
                        int offset = 1;
                        for (int i = 0; i < count && offset < data.length; i++) {
                            int id = BtProtocol.unsigned(data[offset++]);
                            int uidLen = (offset < data.length) ? BtProtocol.unsigned(data[offset++]) : 0;
                            StringBuilder uid = new StringBuilder();
                            for (int j = 0; j < uidLen && offset < data.length; j++) {
                                uid.append(String.format("%02X", BtProtocol.unsigned(data[offset++])));
                                if (j < uidLen - 1) uid.append(":");
                            }
                            // 读取权限等级
                            byte levelCode = 0x02; // 默认普通用户
                            if (offset < data.length) {
                                levelCode = data[offset++];
                            }
                            cards.add(new CardInfo(id, uid.toString(), "卡片#" + id,
                                    CardInfo.Level.fromCode(levelCode), System.currentTimeMillis() / 1000));
                        }
                    }
                    renderCards();
                    break;
                case BtProtocol.CMD_FINGER_GET_LIST:
                    fingers.clear();
                    if (data.length > 0) {
                        int count = BtProtocol.unsigned(data[0]);
                        for (int i = 0; i < count && (i + 1) < data.length; i++) {
                            int fid = BtProtocol.unsigned(data[i + 1]);
                            fingers.add(new FingerprintInfo(fid, "指纹#" + fid, 0));
                        }
                    }
                    renderFingers();
                    break;
                case BtProtocol.CMD_FACE_GET_LIST:
                    faces.clear();
                    if (data.length > 0) {
                        int count = BtProtocol.unsigned(data[0]);
                        for (int i = 0; i < count && (i + 1) < data.length; i++) {
                            int fid = BtProtocol.unsigned(data[i + 1]);
                            faces.add(new FaceInfo(fid, "人脸#" + fid, 0));
                        }
                    }
                    renderFaces();
                    break;
                case BtProtocol.CMD_ACK_OK:
                    showToast("操作成功");
                    break;
                case BtProtocol.CMD_ACK_ERROR:
                    if (data.length >= 1) {
                        String[] errors = {"成功", "超时", "已满", "未找到", "重复", "验证失败", "参数错误", "忙", "取消"};
                        int err = BtProtocol.unsigned(data[0]);
                        showToast("操作失败: " + (err < errors.length ? errors[err] : "未知错误"));
                    }
                    break;
            }
        });
    }

    @Override public void onWriteSuccess() {}
    @Override public void onError(String message) {
        handler.post(() -> showToast(message));
    }
}
