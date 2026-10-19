package com.smartlock.app.model;

public class LockRecord {

    public enum Type {
        PASSWORD("密码", 0xFF2196F3),
        CARD("刷卡", 0xFF9C27B0),
        FINGERPRINT("指纹", 0xFFFF9800),
        BLUETOOTH("蓝牙", 0xFF4CAF50),
        FACE("人脸", 0xFFE91E63);

        public final String label;
        public final int color;

        Type(String label, int color) {
            this.label = label;
            this.color = color;
        }

        public static Type fromLabel(String s) {
            for (Type t : values()) {
                if (t.label.equals(s)) return t;
            }
            return BLUETOOTH;
        }
    }

    public final Type type;
    public final String detail;
    public final String date;
    public final String time;

    public LockRecord(Type type, String detail, String date, String time) {
        this.type = type;
        this.detail = detail;
        this.date = date;
        this.time = time;
    }
}
