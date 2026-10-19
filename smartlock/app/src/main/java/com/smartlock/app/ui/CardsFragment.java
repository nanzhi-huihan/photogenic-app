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
import com.smartlock.app.model.CardInfo;
import com.smartlock.app.protocol.BtProtocol;

import java.util.ArrayList;
import java.util.List;

/**
 * 卡片管理页面
 */
public class CardsFragment extends Fragment implements BleManager.BleCallback {

    private BleManager bleManager;
    private Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout cardsList;
    private final List<CardInfo> cards = new ArrayList<>();

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

        cardsList = view.findViewById(R.id.cards_list);
        Button btnAdd = view.findViewById(R.id.btn_add_card);
        Button btnRefresh = view.findViewById(R.id.btn_refresh_cards);

        btnAdd.setOnClickListener(v -> {
            if (!bleManager.isConnected()) {
                showToast("请先连接蓝牙");
                return;
            }
            bleManager.send(BtProtocol.CMD_ADD_CARD, new byte[0]);
            showToast("请在门禁上刷卡添加");
        });

        btnRefresh.setOnClickListener(v -> {
            if (!bleManager.isConnected()) {
                showToast("请先连接蓝牙");
                return;
            }
            bleManager.send(BtProtocol.CMD_LIST_CARD, new byte[0]);
            showToast("已刷新");
        });

        renderCards();
    }

    @Override
    public void onResume() {
        super.onResume();
        bleManager.setCallback(this);
    }

    private void renderCards() {
        cardsList.removeAllViews();
        if (cards.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("💳 暂无卡片");
            empty.setTextColor(0x80FFFFFF);
            empty.setPadding(0, 60, 0, 20);
            cardsList.addView(empty);
            return;
        }
        for (CardInfo card : cards) {
            View item = LayoutInflater.from(getContext()).inflate(R.layout.item_card, cardsList, false);
            TextView nameView = item.findViewById(R.id.tv_card_name);
            TextView uidView = item.findViewById(R.id.tv_card_uid);
            Button btnDel = item.findViewById(R.id.btn_del_card);

            nameView.setText(card.name);
            uidView.setText(card.uid);
            btnDel.setOnClickListener(v -> {
                if (bleManager.isConnected()) {
                    byte[] uidBytes = hexToBytes(card.uid);
                    bleManager.send(BtProtocol.CMD_DEL_CARD, uidBytes);
                    cards.remove(card);
                    renderCards();
                    showToast("卡片已删除");
                } else {
                    showToast("请先连接蓝牙");
                }
            });
            cardsList.addView(item);
        }
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
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
        // 处理卡片列表返回
    }
    @Override public void onWriteSuccess() {}
    @Override public void onError(String message) { showToast(message); }
}
