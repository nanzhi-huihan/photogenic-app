package com.smartlock.app.model;

/**
 * 人脸信息 (K210)
 */
public class FaceInfo {

    private int id;           // 人脸ID
    private String name;      // 备注名
    private long addedTime;

    public FaceInfo() {}

    public FaceInfo(int id, String name, long addedTime) {
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
