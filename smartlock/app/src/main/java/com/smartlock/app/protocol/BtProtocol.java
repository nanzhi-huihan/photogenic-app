package com.smartlock.app.protocol;

/**
 * 智能门禁系统 — 蓝牙通信协议 v2.0
 *
 * 帧格式: [帧头0xAA] [命令字] [数据长度N] [数据0..N-1] [校验XOR]
 * 校验和 = 命令字 XOR 数据长度 XOR 所有数据字节
 *
 * 上行: APP → STM32
 * 下行: STM32 → APP
 */
public class BtProtocol {

    public static final byte FRAME_HEADER = (byte) 0xAA;

    // ==================== 下行命令 (APP → STM32) ====================

    // --- 开锁相关 ---
    public static final byte CMD_UNLOCK          = 0x01;  // 远程开锁
    public static final byte CMD_LOCK            = 0x02;  // 远程关锁
    public static final byte CMD_PASSWORD_UNLOCK = 0x03;  // 密码开锁(携带密码数据)

    // --- 指纹管理 ---
    public static final byte CMD_FINGER_ENROLL   = 0x10;  // 录入指纹
    public static final byte CMD_FINGER_DELETE   = 0x11;  // 删除指纹
    public static final byte CMD_FINGER_GET_LIST = 0x12;  // 获取指纹列表
    public static final byte CMD_FINGER_CLEAR    = 0x13;  // 清空所有指纹

    // --- 卡片管理 ---
    public static final byte CMD_CARD_ADD        = 0x20;  // 添加卡片
    public static final byte CMD_CARD_DELETE     = 0x21;  // 删除卡片
    public static final byte CMD_CARD_GET_LIST   = 0x22;  // 获取卡片列表
    public static final byte CMD_CARD_SET_LEVEL  = 0x23;  // 修改卡片权限等级

    // --- 人脸管理 ---
    public static final byte CMD_FACE_ENROLL     = 0x30;  // 录入人脸
    public static final byte CMD_FACE_DELETE     = 0x31;  // 删除人脸
    public static final byte CMD_FACE_GET_LIST   = 0x32;  // 获取人脸列表
    public static final byte CMD_FACE_CLEAR      = 0x33;  // 清空所有人脸
    public static final byte CMD_FACE_RECOGNIZE  = 0x34;  // 触发人脸识别

    // --- 系统设置 ---
    public static final byte CMD_SET_TIME        = 0x40;  // 校准RTC时间
    public static final byte CMD_GET_TIME        = 0x41;  // 读取RTC时间
    public static final byte CMD_SET_BUZZER      = 0x42;  // 蜂鸣器开关
    public static final byte CMD_SET_AUTO_LOCK   = 0x43;  // 自动关门延时设置
    public static final byte CMD_FACTORY_RESET   = 0x4F;  // 恢复出厂

    // --- 记录查询 ---
    public static final byte CMD_GET_RECORDS     = 0x50;  // 获取开门记录
    public static final byte CMD_CLEAR_RECORDS   = 0x51;  // 清除记录
    public static final byte CMD_GET_RECORD_COUNT= 0x52;  // 获取记录数量

    // --- 状态查询 ---
    public static final byte CMD_GET_STATUS      = 0x60;  // 获取门状态(门磁/锁状态)
    public static final byte CMD_GET_TEMP_HUMI   = 0x61;  // 读取温湿度
    public static final byte CMD_GET_BATTERY     = 0x62;  // 读取电池电量

    // --- 密码管理 ---
    public static final byte CMD_SET_PASSWORD    = 0x70;  // 修改系统密码
    public static final byte CMD_VERIFY_PASSWORD = 0x71;  // 验证密码

    // ==================== 上行命令 (STM32 → APP) ====================

    // --- 应答 ---
    public static final byte CMD_ACK_OK          = (byte) 0xA0;  // 操作成功
    public static final byte CMD_ACK_ERROR       = (byte) 0xA1;  // 操作失败(携带错误码)
    public static final byte CMD_ACK_DATA        = (byte) 0xA2;  // 数据应答(携带请求的数据)

    // --- 事件上报 ---
    public static final byte CMD_EVT_UNLOCKED    = (byte) 0xB0;  // 门已开(携带开锁方式+ID)
    public static final byte CMD_EVT_LOCKED      = (byte) 0xB1;  // 门已关
    public static final byte CMD_EVT_DOOR_OPEN   = (byte) 0xB2;  // 门被打开(门磁)
    public static final byte CMD_EVT_DOOR_CLOSED = (byte) 0xB3;  // 门已关闭
    public static final byte CMD_EVT_DOOR_TIMEOUT= (byte) 0xB4;  // 门未关超时
    public static final byte CMD_EVT_TAMPER      = (byte) 0xB5;  // 撬门报警
    public static final byte CMD_EVT_LOW_BATTERY = (byte) 0xB6;  // 低电量
    public static final byte CMD_EVT_FAIL_ALARM  = (byte) 0xB7;  // 连续失败报警

    // --- 识别结果 ---
    public static final byte CMD_EVT_FINGER_OK   = (byte) 0xC0;  // 指纹验证通过
    public static final byte CMD_EVT_FINGER_FAIL = (byte) 0xC1;  // 指纹验证失败
    public static final byte CMD_EVT_CARD_OK     = (byte) 0xC2;  // 刷卡通过
    public static final byte CMD_EVT_CARD_FAIL   = (byte) 0xC3;  // 刷卡失败
    public static final byte CMD_EVT_FACE_OK     = (byte) 0xC4;  // 人脸识别通过
    public static final byte CMD_EVT_FACE_FAIL   = (byte) 0xC5;  // 人脸识别失败

    // --- 传感器数据 ---
    public static final byte CMD_EVT_TEMP_HUMI   = (byte) 0xD0;  // 温湿度上报
    public static final byte CMD_EVT_STATUS      = (byte) 0xD1;  // 门状态上报

    // ==================== 错误码 ====================
    public static final byte ERR_NONE            = 0x00;
    public static final byte ERR_TIMEOUT         = 0x01;
    public static final byte ERR_FULL            = 0x02;  // 存储已满
    public static final byte ERR_NOT_FOUND       = 0x03;  // 未找到
    public static final byte ERR_DUPLICATE       = 0x04;  // 重复
    public static final byte ERR_VERIFY_FAIL     = 0x05;  // 验证失败
    public static final byte ERR_PARAM           = 0x06;  // 参数错误
    public static final byte ERR_BUSY            = 0x07;  // 忙(正在录入等)
    public static final byte ERR_CANCEL          = 0x08;  // 取消

    // ==================== 开锁方式 ====================
    public static final byte METHOD_PASSWORD     = 0x01;
    public static final byte METHOD_CARD         = 0x02;
    public static final byte METHOD_FINGERPRINT  = 0x03;
    public static final byte METHOD_FACE         = 0x04;
    public static final byte METHOD_BLUETOOTH    = 0x05;
    public static final byte METHOD_BUTTON       = 0x06;  // 出门按钮

    // ==================== 权限等级 ====================
    public static final byte LEVEL_ADMIN         = 0x01;  // 管理员
    public static final byte LEVEL_USER          = 0x02;  // 普通用户
    public static final byte LEVEL_TEMP          = 0x03;  // 临时用户
    public static final byte LEVEL_DISABLED      = 0x04;  // 已禁用

    // ==================== 帧构建/解析 ====================

    /**
     * 构建发送帧
     * @param cmd  命令字
     * @param data 数据(可为null)
     * @return 完整帧字节数组
     */
    public static byte[] buildFrame(byte cmd, byte[] data) {
        if (data == null) data = new byte[0];
        if (data.length > 255) data = java.util.Arrays.copyOf(data, 255);
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
     * 校验帧完整性
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
     * 提取命令字
     */
    public static byte extractCmd(byte[] frame) {
        return frame[1];
    }

    /**
     * 提取数据域
     */
    public static byte[] extractData(byte[] frame, int length) {
        int dataLen = frame[2] & 0xFF;
        byte[] data = new byte[dataLen];
        System.arraycopy(frame, 3, data, 0, dataLen);
        return data;
    }

    /**
     * 字符串→字节
     */
    public static byte[] stringToBytes(String s) {
        return s.getBytes();
    }

    /**
     * 字节→字符串
     */
    public static String bytesToString(byte[] data) {
        return new String(data);
    }

    /**
     * 单字节→无符号int
     */
    public static int unsigned(byte b) {
        return b & 0xFF;
    }

    /**
     * int → 单字节(取低8位)
     */
    public static byte toByte(int v) {
        return (byte) (v & 0xFF);
    }

    /**
     * 大端 short → 2字节
     */
    public static byte[] shortToBytes(int v) {
        return new byte[]{(byte) ((v >> 8) & 0xFF), (byte) (v & 0xFF)};
    }

    /**
     * 大端 2字节 → short
     */
    public static int bytesToShort(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    /**
     * 大端 4字节 → int
     */
    public static int bytesToInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    /**
     * int → 大端 4字节
     */
    public static byte[] intToBytes(int v) {
        return new byte[]{
                (byte) ((v >> 24) & 0xFF),
                (byte) ((v >> 16) & 0xFF),
                (byte) ((v >> 8) & 0xFF),
                (byte) (v & 0xFF)
        };
    }
}
