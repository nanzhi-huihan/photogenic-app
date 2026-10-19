package com.smartlock.app.model;

/**
 * 门禁卡片信息
 */
public class CardInfo {

    public enum Level {
        ADMIN(0x01, "管理员"),
        USER(0x02, "普通用户"),
        TEMP(0x03, "临时用户"),
        DISABLED(0x04, "已禁用");

        public final byte code;
        public final String label;

        Level(int code, String label) {
            this.code = (byte) code;
            this.label = label;
        }

        public static Level fromCode(byte code) {
            for (Level l : values()) if (l.code == code) return l;
            return USER;
        }
    }

    private int id;
    private String uid;
    private String name;
    private Level level;
    private long addedTime;

    public CardInfo() {}

    public CardInfo(int id, String uid, String name, Level level, long addedTime) {
        this.id = id;
        this.uid = uid;
        this.name = name;
        this.level = level;
        this.addedTime = addedTime;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Level getLevel() { return level; }
    public void setLevel(Level level) { this.level = level; }

    public long getAddedTime() { return addedTime; }
    public void setAddedTime(long addedTime) { this.addedTime = addedTime; }

    public boolean isEnabled() {
        return level != Level.DISABLED;
    }

    @Override
    public String toString() {
        return name + " [" + uid + "] (" + level.label + ")";
    }
}
