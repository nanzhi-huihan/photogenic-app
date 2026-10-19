package com.smartlock.app.protocol;

/**
 * 蓝牙通信协议
 * 帧格式: [帧头0xAA] [命令字] [数据长度] [数据...] [校验和]
 * 校验和 = 命令字 XOR 数据长度 XOR 所有数据字节
 */
public class BtProtocol {

    public static final byte FRAME_HEADER = (byte) 0xAA;

    // === APP → STM32 下行命令 ===
    public static final byte CMD_UNLOCK      = 0x01;  // 开锁
    public static final byte CMD_LOCK        = 0x02;  // 关锁
    public static final byte CMD_PASSWORD    = 0x03;  // 密码开锁
    public static final byte CMD_ADD_CARD    = 0x10;  // 添加卡片
    public static final byte CMD_DEL_CARD    = 0x11;  // 删除卡片
    public static final byte CMD_LIST_CARD   = 0x12;  // 获取卡片列表
    public static final byte CMD_GET_RECORDS = 0x20;  // 获取记录
    public static final byte CMD_SET_MODE    = 0x30;  // 设置模式

    // === STM32 → APP 上行命令 ===
    public static final byte CMD_STATUS_REPORT = 0x40; // 状态上报
    public static final byte CMD_NOTIFICATION  = 0x41; // 通知上报

    // === 通知类型 ===
    public static final byte NOTIFY_STRANGER   = 0x01; // 陌生人检测
    public static final byte NOTIFY_DOOR_OPEN  = 0x02; // 门未关提醒
    public static final byte NOTIFY_HOME       = 0x03; // 到家通知
    public static final byte NOTIFY_LATE       = 0x04; // 超时未归

    /**
     * 构建发送帧
     */
    public static byte[] buildFrame(byte cmd, byte[] data) {
        if (data == null) data = new byte[0];
        byte len = (byte) data.length;
        byte checksum = (byte) (cmd ^ len);
        for (byte b : data) checksum ^= b;

        byte[] frame = new byte[4 + data.length];
        frame[0] = FRAME_HEADER;
        frame[1] = cmd;
        frame[2] = len;
        System.arraycopy(data, 0, frame, 3, data.length);
        frame[3 + data.length] = checksum;
        return frame;
    }

    /**
     * 校验帧是否合法
     */
    public static boolean verifyFrame(byte[] frame, int length) {
        if (length < 4) return false;
        if ((frame[0] & 0xFF) != 0xAA) return false;
        byte cmd = frame[1];
        int dataLen = frame[2] & 0xFF;
        if (length != 4 + dataLen) return false;

        byte expectedCs = (byte) (cmd ^ dataLen);
        for (int i = 0; i < dataLen; i++) expectedCs ^= frame[3 + i];
        byte actualCs = frame[3 + dataLen];
        return expectedCs == actualCs;
    }

    /**
     * 从帧中提取数据
     */
    public static byte[] extractData(byte[] frame, int length) {
        int dataLen = frame[2] & 0xFF;
        byte[] data = new byte[dataLen];
        System.arraycopy(frame, 3, data, 0, dataLen);
        return data;
    }

    /**
     * 从帧中提取命令字
     */
    public static byte extractCmd(byte[] frame) {
        return frame[1];
    }

    /**
     * 字符串转字节数组
     */
    public static byte[] stringToBytes(String s) {
        return s.getBytes();
    }

    /**
     * 字节数组转字符串
     */
    public static String bytesToString(byte[] data) {
        return new String(data);
    }
}
