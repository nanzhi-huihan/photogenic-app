package com.smartlock.app.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 开门记录
 */
public class LockRecord {

    public enum Method {
        PASSWORD(0x01, "密码", 0xFF2196F3),
        CARD(0x02, "刷卡", 0xFF9C27B0),
        FINGERPRINT(0x03, "指纹", 0xFFFF9800),
        FACE(0x04, "人脸", 0xFFE91E63),
        BLUETOOTH(0x05, "蓝牙APP", 0xFF4CAF50),
        BUTTON(0x06, "出门按钮", 0xFF607D8B);

        public final byte code;
        public final String label;
        public final int color;

        Method(int code, String label, int color) {
            this.code = (byte) code;
            this.label = label;
            this.color = color;
        }

        public static Method fromCode(byte code) {
            for (Method m : values()) if (m.code == code) return m;
            return PASSWORD;
        }
    }

    private int id;
    private long timestamp;    // Unix时间戳(秒)
    private Method method;
    private String userId;     // 用户ID

    public LockRecord() {}

    public LockRecord(int id, long timestamp, Method method, String userId) {
        this.id = id;
        this.timestamp = timestamp;
        this.method = method;
        this.userId = userId;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public Method getMethod() { return method; }
    public void setMethod(Method method) { this.method = method; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTimeStr() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timestamp * 1000L));
    }

    public String getShortTimeStr() {
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp * 1000L));
    }
}
