package com.smartlock.app.model;

/**
 * 指纹信息
 */
public class FingerprintInfo {

    private int id;           // 指纹存储ID (1~255)
    private String name;      // 备注名
    private long addedTime;

    public FingerprintInfo() {}

    public FingerprintInfo(int id, String name, long addedTime) {
        this.id = id;
        this.name = name;
        this.addedTime = addedTime;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getAddedTime() { return addedTime; }
    public void setAddedTime(long addedTime) { this.addedTime = addedTime; }

    @Override
    public String toString() {
        return "#" + id + " " + name;
    }
}
