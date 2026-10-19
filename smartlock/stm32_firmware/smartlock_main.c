/**
 ******************************************************************************
 * @file    smartlock_main.c
 * @brief   智能门禁系统主程序 — STM32F103C8T6
 *
 * 外设列表:
 *   USART1  — 调试串口 (115200)
 *   USART2  — BLE 蓝牙模块 (9600)
 *   USART3  — AS608 指纹模块 (57600)
 *   SPI1    — RC522 RFID 模块
 *   I2C1    — OLED 显示屏 (SSD1306)
 *   GPIO    — 矩阵键盘 (4x4)
 *   GPIO    — 干簧管门磁 (PA0)
 *   GPIO    — 步进电机 (PB12~PB15)
 *   GPIO    — 蜂鸣器 (PA8)
 *   GPIO    — 出门按钮 (PA1)
 *   GPIO    — K210 人脸模块 (UART4 或 IO触发)
 *   TIM2    — 系统定时器 (1ms)
 *   TIM3    — 步进电机 PWM
 *   ADC1    — 电池电压采集
 *
 * 作者: 毕设项目
 * 版本: v2.0
 ******************************************************************************
 */

#include "stm32f10x.h"
#include <string.h>
#include <stdio.h>
#include <stdbool.h>

/* ======================== 配置宏 ======================== */

// BLE 蓝牙
#define BLE_UART        USART2
#define BLE_BAUDRATE    9600

// 指纹模块
#define FP_UART         USART3
#define FP_BAUDRATE     57600

// 系统参数
#define MAX_CARDS       20      // 最大卡片数
#define MAX_FINGERS     50      // 最大指纹数
#define MAX_FACES       20      // 最大人脸数
#define MAX_RECORDS     100     // 最大记录数
#define PASSWORD_LEN    6       // 密码长度
#define DOOR_AUTO_LOCK_DEFAULT  5   // 默认自动关门秒数

// 错误码
#define ERR_NONE        0x00
#define ERR_TIMEOUT     0x01
#define ERR_FULL        0x02
#define ERR_NOT_FOUND   0x03
#define ERR_DUPLICATE   0x04
#define ERR_VERIFY_FAIL 0x05
#define ERR_PARAM       0x06
#define ERR_BUSY        0x07
#define ERR_CANCEL      0x08

/* ======================== 数据结构 ======================== */

// 卡片记录
typedef struct {
    uint8_t  id;            // 卡片序号
    uint8_t  uid[10];       // UID (最多10字节)
    uint8_t  uidLen;        // UID长度
    uint8_t  level;         // 权限等级 1=管理员 2=用户 3=临时 4=禁用
    uint32_t addedTime;     // 添加时间
} CardRecord_t;

// 指纹记录
typedef struct {
    uint8_t  id;            // 指纹存储ID
    char     name[16];      // 备注名
    uint32_t addedTime;
} FingerRecord_t;

// 人脸记录
typedef struct {
    uint8_t  id;
    char     name[16];
    uint32_t addedTime;
} FaceRecord_t;

// 开门记录
typedef struct {
    uint16_t id;            // 记录序号
    uint8_t  method;        // 开锁方式
    uint32_t timestamp;     // 时间戳
    char     userId[12];    // 用户标识
} UnlockRecord_t;

// 系统状态
typedef struct {
    uint8_t  isLocked;          // 当前是否锁定
    uint8_t  doorClosed;        // 门是否关闭
    uint8_t  isDoorOpen;        // 门是否被打开
    uint8_t  alarmActive;       // 报警是否激活
    uint8_t  buzzerEnabled;     // 蜂鸣器开关
    uint8_t  autoLockEnabled;   // 自动关门使能
    uint16_t autoLockDelay;     // 自动关门延时(秒)
    uint8_t  failCount;         // 连续失败次数
    uint8_t  temp;              // 温度
    uint8_t  humi;              // 湿度
    uint8_t  battery;           // 电量百分比
    uint8_t  isValidPassword;   // 当前密码有效
} SystemState_t;

// RTC 时间
typedef struct {
    uint16_t year;
    uint8_t  month;
    uint8_t  day;
    uint8_t  hour;
    uint8_t  minute;
    uint8_t  second;
    uint8_t  weekday;
} RtcTime_t;

// 蓝牙帧缓冲
typedef struct {
    uint8_t  buf[128];
    uint16_t len;
    uint8_t  rxFlag;
} BleFrame_t;

/* ======================== 全局变量 ======================== */

volatile SystemState_t g_sys;
volatile RtcTime_t      g_rtc;
volatile BleFrame_t     g_bleFrame;

// 存储数据 (实际项目应存 Flash/EEPROM)
CardRecord_t    g_cards[MAX_CARDS];
FingerRecord_t  g_fingers[MAX_FINGERS];
FaceRecord_t    g_faces[MAX_FACES];
UnlockRecord_t  g_records[MAX_RECORDS];
uint16_t        g_recordCount = 0;
uint8_t         g_password[PASSWORD_LEN + 1] = "123456";

// 系统计数
volatile uint32_t g_sysTick = 0;       // 系统tick (ms)
volatile uint32_t g_doorOpenTick = 0;  // 开门时刻
volatile uint32_t g_alarmTick = 0;     // 报警开始时刻
volatile uint16_t g_autoLockCounter = 0;

/* ======================== 函数声明 ======================== */

// 初始化
void System_Init(void);
void Clock_Init(void);
void GPIO_Init_All(void);
void UART_Init_All(void);
void SPI_Init_RC522(void);
void I2C_Init_OLED(void);
void TIM_Init_All(void);
void NVIC_Init_All(void);
void RTC_Init_Custom(void);
void ADC_Init_Battery(void);

// 外设驱动
void BLE_SendByte(uint8_t data);
void BLE_SendFrame(uint8_t cmd, uint8_t *data, uint8_t len);
void BLE_SendAck(uint8_t cmd, uint8_t errCode, uint8_t *data, uint8_t len);
void BLE_ProcessFrame(uint8_t cmd, uint8_t *data, uint8_t len);

// 协议处理
void Protocol_Handle(uint8_t cmd, uint8_t *data, uint8_t len);

// 门锁控制
void Lock_Open(uint8_t method, char *userId);
void Lock_Close(void);
void Lock_Toggle(void);

// 报警
void Buzzer_On(uint16_t freq);
void Buzzer_Off(void);
void Alarm_Trigger(uint8_t type);
void Alarm_Stop(void);

// 卡片管理
int8_t Card_Add(uint8_t *uid, uint8_t uidLen);
int8_t Card_Delete(uint8_t *uid, uint8_t uidLen);
int8_t Card_SetLevel(uint8_t id, uint8_t level);
void Card_SendList(void);

// 指纹管理
int8_t Finger_Enroll(uint8_t id);
int8_t Finger_Delete(uint8_t id);
void Finger_SendList(void);

// 人脸管理
int8_t Face_Enroll(uint8_t id);
int8_t Face_Delete(uint8_t id);
void Face_SendList(void);

// 记录管理
void Record_Add(uint8_t method, const char *userId);
void Record_Send(uint8_t filterMethod);
void Record_Clear(void);

// 时间
void RTC_SetTime(uint8_t year, uint8_t month, uint8_t day,
                 uint8_t hour, uint8_t minute, uint8_t second, uint8_t weekday);
void RTC_GetTime(RtcTime_t *t);
uint32_t RTC_ToTimestamp(RtcTime_t *t);
void RTC_FromTimestamp(RtcTime_t *t, uint32_t ts);

// DHT11
uint8_t DHT11_Read(uint8_t *temp, uint8_t *humi);

// 矩阵键盘
uint8_t Key_Scan(void);

// OLED
void OLED_ShowMenu(void);
void OLED_ShowStatus(void);
void OLED_ShowTime(void);

// 工具
void Delay_ms(uint32_t ms);
void SendFrame(uint8_t cmd, uint8_t *data, uint8_t len);

/* ======================== 主函数 ======================== */

int main(void)
{
    System_Init();

    // 开机提示
    OLED_ShowStatus();
    Buzzer_On(1000);
    Delay_ms(100);
    Buzzer_Off();

    while (1)
    {
        // 1. 处理蓝牙帧
        if (g_bleFrame.rxFlag)
        {
            g_bleFrame.rxFlag = 0;
            Protocol_Handle(g_bleFrame.buf[1],
                            &g_bleFrame.buf[3],
                            g_bleFrame.buf[2]);
        }

        // 2. 扫描矩阵键盘
        uint8_t key = Key_Scan();
        if (key != 0)
        {
            // 处理键盘输入
        }

        // 3. 门磁检测
        if (GPIO_ReadInputDataBit(GPIOA, GPIO_Pin_0) == Bit_SET)
        {
            if (!g_sys.isDoorOpen && g_sys.isLocked)
            {
                g_sys.isDoorOpen = 1;
                // 门被打开了
            }
        }

        // 4. 自动关门
        if (g_sys.autoLockEnabled && g_sys.isDoorOpen && g_sys.autoLockDelay > 0)
        {
            if ((g_sysTick - g_doorOpenTick) > (g_sys.autoLockDelay * 1000))
            {
                Lock_Close();
                g_sys.isDoorOpen = 0;
                BLE_SendFrame(0xB4, NULL, 0); // 门未关超时
            }
        }

        // 5. 报警超时检查
        if (g_sys.alarmActive && (g_sysTick - g_alarmTick > 30000))
        {
            Alarm_Stop();
        }

        // 6. OLED刷新 (每500ms)
        static uint32_t oledTick = 0;
        if (g_sysTick - oledTick > 500)
        {
            oledTick = g_sysTick;
            OLED_ShowStatus();
        }

        // 7. DHT11 读取 (每2s)
        static uint32_t dhtTick = 0;
        if (g_sysTick - dhtTick > 2000)
        {
            dhtTick = g_sysTick;
            uint8_t t, h;
            if (DHT11_Read(&t, &h) == 0)
            {
                g_sys.temp = t;
                g_sys.humi = h;
                uint8_t thData[4] = {t, 0, h, 0};
                BLE_SendFrame(0xD0, thData, 4);
            }
        }
    }
}

/* ======================== 协议处理 ======================== */

void Protocol_Handle(uint8_t cmd, uint8_t *data, uint8_t len)
{
    switch (cmd)
    {
    // --- 开锁 ---
    case 0x01:  // CMD_UNLOCK
        Lock_Open(0x5, "BT_APP");
        BLE_SendFrame(0xA0, NULL, 0); // ACK OK
        break;

    case 0x02:  // CMD_LOCK
        Lock_Close();
        BLE_SendFrame(0xA0, NULL, 0);
        break;

    case 0x03:  // CMD_PASSWORD_UNLOCK
        if (len >= PASSWORD_LEN)
        {
            if (memcmp(data, g_password, PASSWORD_LEN) == 0)
            {
                Lock_Open(0x01, "PWD");
                BLE_SendFrame(0xA0, NULL, 0);
            }
            else
            {
                g_sys.failCount++;
                uint8_t errData[1] = {ERR_VERIFY_FAIL};
                BLE_SendFrame(0xA1, errData, 1);
                if (g_sys.failCount >= 3) Alarm_Trigger(7);
            }
        }
        break;

    // --- 指纹管理 ---
    case 0x10:  // CMD_FINGER_ENROLL
        Finger_Enroll(0);
        break;
    case 0x11:  // CMD_FINGER_DELETE
        if (len >= 1) Finger_Delete(data[0]);
        break;
    case 0x12:  // CMD_FINGER_GET_LIST
        Finger_SendList();
        break;
    case 0x13:  // CMD_FINGER_CLEAR
        memset(g_fingers, 0, sizeof(g_fingers));
        BLE_SendFrame(0xA0, NULL, 0);
        break;

    // --- 卡片管理 ---
    case 0x20:  // CMD_CARD_ADD
        if (len >= 1) Card_Add(data + 1, data[0]);
        break;
    case 0x21:  // CMD_CARD_DELETE
        if (len == 1 && data[0] == 0xFF)
            memset(g_cards, 0, sizeof(g_cards)); // 清空
        else if (len >= 1)
            Card_Delete(data + 1, data[0]);
        BLE_SendFrame(0xA0, NULL, 0);
        break;
    case 0x22:  // CMD_CARD_GET_LIST
        Card_SendList();
        break;
    case 0x23:  // CMD_CARD_SET_LEVEL
        if (len >= 2) Card_SetLevel(data[0], data[1]);
        BLE_SendFrame(0xA0, NULL, 0);
        break;

    // --- 人脸管理 ---
    case 0x30:  // CMD_FACE_ENROLL
        Face_Enroll(0);
        break;
    case 0x31:  // CMD_FACE_DELETE
        if (len >= 1) Face_Delete(data[0]);
        break;
    case 0x32:  // CMD_FACE_GET_LIST
        Face_SendList();
        break;
    case 0x33:  // CMD_FACE_CLEAR
        memset(g_faces, 0, sizeof(g_faces));
        BLE_SendFrame(0xA0, NULL, 0);
        break;
    case 0x34:  // CMD_FACE_RECOGNIZE
        // 触发K210识别
        break;

    // --- 系统设置 ---
    case 0x40:  // CMD_SET_TIME
        if (len >= 7)
        {
            RTC_SetTime(data[0], data[1], data[2], data[3], data[4], data[5], data[6]);
            BLE_SendFrame(0xA0, NULL, 0);
        }
        break;
    case 0x41:  // CMD_GET_TIME
    {
        RtcTime_t t;
        RTC_GetTime(&t);
        uint8_t timeData[7] = {t.year - 2000, t.month, t.day, t.hour, t.minute, t.second, t.weekday};
        BLE_SendFrame(0xD1, timeData, 7);
        break;
    }
    case 0x42:  // CMD_SET_BUZZER
        if (len >= 1) g_sys.buzzerEnabled = data[0];
        BLE_SendFrame(0xA0, NULL, 0);
        break;
    case 0x43:  // CMD_SET_AUTO_LOCK
        if (len >= 1)
        {
            g_sys.autoLockDelay = data[0];
            g_sys.autoLockEnabled = (data[0] > 0);
        }
        BLE_SendFrame(0xA0, NULL, 0);
        break;
    case 0x4F:  // CMD_FACTORY_RESET
        memset(g_cards, 0, sizeof(g_cards));
        memset(g_fingers, 0, sizeof(g_fingers));
        memset(g_faces, 0, sizeof(g_faces));
        memset(g_records, 0, sizeof(g_records));
        g_recordCount = 0;
        g_sys.failCount = 0;
        memcpy(g_password, "123456", 6);
        BLE_SendFrame(0xA0, NULL, 0);
        break;

    // --- 记录查询 ---
    case 0x50:  // CMD_GET_RECORDS
        Record_Send(len > 0 ? data[0] : 0);
        break;
    case 0x51:  // CMD_CLEAR_RECORDS
        Record_Clear();
        BLE_SendFrame(0xA0, NULL, 0);
        break;
    case 0x52:  // CMD_GET_RECORD_COUNT
    {
        uint8_t cntData[2] = {g_recordCount >> 8, g_recordCount & 0xFF};
        BLE_SendFrame(0xA2, cntData, 2);
        break;
    }

    // --- 状态查询 ---
    case 0x60:  // CMD_GET_STATUS
    {
        uint8_t stData[2] = {g_sys.doorClosed, g_sys.isLocked};
        BLE_SendFrame(0xD1, stData, 2);
        break;
    }
    case 0x61:  // CMD_GET_TEMP_HUMI
    {
        uint8_t thData[4] = {g_sys.temp, 0, g_sys.humi, 0};
        BLE_SendFrame(0xD0, thData, 4);
        break;
    }
    case 0x62:  // CMD_GET_BATTERY
    {
        uint8_t batData[1] = {g_sys.battery};
        BLE_SendFrame(0xA2, batData, 1);
        break;
    }

    // --- 密码管理 ---
    case 0x70:  // CMD_SET_PASSWORD
        if (len >= PASSWORD_LEN)
        {
            memcpy(g_password, data, PASSWORD_LEN);
            g_password[PASSWORD_LEN] = '\0';
            BLE_SendFrame(0xA0, NULL, 0);
        }
        break;
    case 0x71:  // CMD_VERIFY_PASSWORD
        if (len >= PASSWORD_LEN && memcmp(data, g_password, PASSWORD_LEN) == 0)
            BLE_SendFrame(0xA0, NULL, 0);
        else
        {
            uint8_t err = ERR_VERIFY_FAIL;
            BLE_SendFrame(0xA1, &err, 1);
        }
        break;

    default:
    {
        uint8_t err = ERR_PARAM;
        BLE_SendFrame(0xA1, &err, 1);
        break;
    }
    }
}

/* ======================== 门锁控制 ======================== */

void Lock_Open(uint8_t method, char *userId)
{
    // 步进电机开锁 (正转180°)
    GPIO_SetBits(GPIOB, GPIO_Pin_12);
    GPIO_ResetBits(GPIOB, GPIO_Pin_13);
    GPIO_SetBits(GPIOB, GPIO_Pin_14);
    GPIO_ResetBits(GPIOB, GPIO_Pin_15);
    Delay_ms(500);
    GPIO_ResetBits(GPIOB, GPIO_Pin_12 | GPIO_Pin_13 | GPIO_Pin_14 | GPIO_Pin_15);

    g_sys.isLocked = 0;
    g_sys.isDoorOpen = 1;
    g_sys.failCount = 0;
    g_doorOpenTick = g_sysTick;

    // 添加记录
    Record_Add(method, userId);

    // 上报开锁事件
    uint8_t evtData[1] = {method};
    BLE_SendFrame(0xB0, evtData, 1);

    // 蜂鸣提示
    if (g_sys.buzzerEnabled)
    {
        Buzzer_On(1500);
        Delay_ms(200);
        Buzzer_Off();
    }
}

void Lock_Close(void)
{
    // 步进电机关锁 (反转180°)
    GPIO_ResetBits(GPIOB, GPIO_Pin_12);
    GPIO_SetBits(GPIOB, GPIO_Pin_13);
    GPIO_ResetBits(GPIOB, GPIO_Pin_14);
    GPIO_SetBits(GPIOB, GPIO_Pin_15);
    Delay_ms(500);
    GPIO_ResetBits(GPIOB, GPIO_Pin_12 | GPIO_Pin_13 | GPIO_Pin_14 | GPIO_Pin_15);

    g_sys.isLocked = 1;
    g_sys.isDoorOpen = 0;
    g_sys.doorClosed = 1;

    BLE_SendFrame(0xB1, NULL, 0);
}

/* ======================== 报警 ======================== */

void Buzzer_On(uint16_t freq)
{
    // TIM3 输出 PWM 驱动蜂鸣器
    TIM_SetAutoreload(TIM3, 1000000 / freq);
    TIM_SetCompare1(TIM3, 500000 / freq);
    TIM_Cmd(TIM3, ENABLE);
}

void Buzzer_Off(void)
{
    TIM_Cmd(TIM3, DISABLE);
    GPIO_ResetBits(GPIOA, GPIO_Pin_8);
}

void Alarm_Trigger(uint8_t type)
{
    g_sys.alarmActive = 1;
    g_alarmTick = g_sysTick;

    if (g_sys.buzzerEnabled)
    {
        // 间歇鸣叫
        for (int i = 0; i < 10; i++)
        {
            Buzzer_On(2000);
            Delay_ms(200);
            Buzzer_Off();
            Delay_ms(100);
        }
    }

    // 上报
    uint8_t evtData[1] = {type};
    BLE_SendFrame(0xB7, evtData, 1);
}

void Alarm_Stop(void)
{
    g_sys.alarmActive = 0;
    Buzzer_Off();
}

/* ======================== 卡片管理 ======================== */

int8_t Card_Add(uint8_t *uid, uint8_t uidLen)
{
    if (uidLen > 10) uidLen = 10;

    // 查找空位
    for (int i = 0; i < MAX_CARDS; i++)
    {
        if (g_cards[i].id == 0)
        {
            g_cards[i].id = i + 1;
            memcpy(g_cards[i].uid, uid, uidLen);
            g_cards[i].uidLen = uidLen;
            g_cards[i].level = 2; // 默认普通用户
            g_cards[i].addedTime = RTC_ToTimestamp((RtcTime_t *)&g_rtc);
            BLE_SendFrame(0xA0, NULL, 0);
            return i;
        }
    }
    uint8_t err = ERR_FULL;
    BLE_SendFrame(0xA1, &err, 1);
    return -1;
}

int8_t Card_Delete(uint8_t *uid, uint8_t uidLen)
{
    for (int i = 0; i < MAX_CARDS; i++)
    {
        if (g_cards[i].id != 0 &&
            g_cards[i].uidLen == uidLen &&
            memcmp(g_cards[i].uid, uid, uidLen) == 0)
        {
            g_cards[i].id = 0;
            BLE_SendFrame(0xA0, NULL, 0);
            return i;
        }
    }
    uint8_t err = ERR_NOT_FOUND;
    BLE_SendFrame(0xA1, &err, 1);
    return -1;
}

int8_t Card_SetLevel(uint8_t id, uint8_t level)
{
    for (int i = 0; i < MAX_CARDS; i++)
    {
        if (g_cards[i].id == id)
        {
            g_cards[i].level = level;
            BLE_SendFrame(0xA0, NULL, 0);
            return i;
        }
    }
    return -1;
}

void Card_SendList(void)
{
    uint8_t buf[128];
    buf[0] = 0; // count

    uint16_t offset = 1;
    for (int i = 0; i < MAX_CARDS && offset < 120; i++)
    {
        if (g_cards[i].id != 0)
        {
            buf[0]++;
            buf[offset++] = g_cards[i].id;
            buf[offset++] = g_cards[i].uidLen;
            memcpy(&buf[offset], g_cards[i].uid, g_cards[i].uidLen);
            offset += g_cards[i].uidLen;
            buf[offset++] = g_cards[i].level;
        }
    }
    BLE_SendFrame(0x22, buf, offset);
}

/* ======================== 指纹管理 ======================== */

int8_t Finger_Enroll(uint8_t id)
{
    // 实际项目调用 AS608 协议录入
    if (id == 0)
    {
        // 查找空闲ID
        for (int i = 1; i <= MAX_FINGERS; i++)
        {
            int used = 0;
            for (int j = 0; j < MAX_FINGERS; j++)
                if (g_fingers[j].id == i) { used = 1; break; }
            if (!used) { id = i; break; }
        }
    }
    if (id > 0 && id <= MAX_FINGERS)
    {
        for (int i = 0; i < MAX_FINGERS; i++)
        {
            if (g_fingers[i].id == 0)
            {
                g_fingers[i].id = id;
                snprintf(g_fingers[i].name, 16, "指纹#%d", id);
                g_fingers[i].addedTime = RTC_ToTimestamp((RtcTime_t *)&g_rtc);
                BLE_SendFrame(0xA0, NULL, 0);
                return i;
            }
        }
    }
    uint8_t err = ERR_FULL;
    BLE_SendFrame(0xA1, &err, 1);
    return -1;
}

int8_t Finger_Delete(uint8_t id)
{
    for (int i = 0; i < MAX_FINGERS; i++)
    {
        if (g_fingers[i].id == id)
        {
            g_fingers[i].id = 0;
            BLE_SendFrame(0xA0, NULL, 0);
            return i;
        }
    }
    uint8_t err = ERR_NOT_FOUND;
    BLE_SendFrame(0xA1, &err, 1);
    return -1;
}

void Finger_SendList(void)
{
    uint8_t buf[128];
    buf[0] = 0;
    uint16_t offset = 1;
    for (int i = 0; i < MAX_FINGERS && offset < 125; i++)
    {
        if (g_fingers[i].id != 0)
        {
            buf[0]++;
            buf[offset++] = g_fingers[i].id;
        }
    }
    BLE_SendFrame(0x12, buf, offset);
}

/* ======================== 人脸管理 ======================== */

int8_t Face_Enroll(uint8_t id)
{
    if (id == 0)
    {
        for (int i = 1; i <= MAX_FACES; i++)
        {
            int used = 0;
            for (int j = 0; j < MAX_FACES; j++)
                if (g_faces[j].id == i) { used = 1; break; }
            if (!used) { id = i; break; }
        }
    }
    if (id > 0 && id <= MAX_FACES)
    {
        for (int i = 0; i < MAX_FACES; i++)
        {
            if (g_faces[i].id == 0)
            {
                g_faces[i].id = id;
                snprintf(g_faces[i].name, 16, "人脸#%d", id);
                g_faces[i].addedTime = RTC_ToTimestamp((RtcTime_t *)&g_rtc);
                BLE_SendFrame(0xA0, NULL, 0);
                return i;
            }
        }
    }
    uint8_t err = ERR_FULL;
    BLE_SendFrame(0xA1, &err, 1);
    return -1;
}

int8_t Face_Delete(uint8_t id)
{
    for (int i = 0; i < MAX_FACES; i++)
    {
        if (g_faces[i].id == id)
        {
            g_faces[i].id = 0;
            BLE_SendFrame(0xA0, NULL, 0);
            return i;
        }
    }
    return -1;
}

void Face_SendList(void)
{
    uint8_t buf[128];
    buf[0] = 0;
    uint16_t offset = 1;
    for (int i = 0; i < MAX_FACES && offset < 125; i++)
    {
        if (g_faces[i].id != 0)
        {
            buf[0]++;
            buf[offset++] = g_faces[i].id;
        }
    }
    BLE_SendFrame(0x32, buf, offset);
}

/* ======================== 记录管理 ======================== */

void Record_Add(uint8_t method, const char *userId)
{
    if (g_recordCount >= MAX_RECORDS)
    {
        // 覆盖最旧记录 (环形缓冲)
        memmove(&g_records[0], &g_records[1], sizeof(UnlockRecord_t) * (MAX_RECORDS - 1));
        g_recordCount = MAX_RECORDS - 1;
    }

    UnlockRecord_t *r = &g_records[g_recordCount];
    r->id = g_recordCount + 1;
    r->method = method;
    r->timestamp = RTC_ToTimestamp((RtcTime_t *)&g_rtc);
    strncpy(r->userId, userId, 11);
    r->userId[11] = '\0';
    g_recordCount++;
}

void Record_Send(uint8_t filterMethod)
{
    uint8_t buf[128];
    buf[0] = (g_recordCount >> 8) & 0xFF;
    buf[1] = g_recordCount & 0xFF;

    uint16_t count = 0;
    uint16_t offset = 3;

    for (int i = g_recordCount - 1; i >= 0 && count < 20; i--)
    {
        if (filterMethod != 0 && g_records[i].method != filterMethod) continue;

        buf[offset++] = (g_records[i].id >> 8) & 0xFF;
        buf[offset++] = g_records[i].id & 0xFF;
        buf[offset++] = g_records[i].method;
        buf[offset++] = (g_records[i].timestamp >> 24) & 0xFF;
        buf[offset++] = (g_records[i].timestamp >> 16) & 0xFF;
        buf[offset++] = (g_records[i].timestamp >> 8) & 0xFF;
        buf[offset++] = g_records[i].timestamp & 0xFF;
        uint8_t uidLen = strlen(g_records[i].userId);
        buf[offset++] = uidLen;
        memcpy(&buf[offset], g_records[i].userId, uidLen);
        offset += uidLen;
        count++;
    }

    buf[2] = count;
    BLE_SendFrame(0x50, buf, offset);
}

void Record_Clear(void)
{
    memset(g_records, 0, sizeof(g_records));
    g_recordCount = 0;
}

/* ======================== 蓝牙发送 ======================== */

void SendFrame(uint8_t cmd, uint8_t *data, uint8_t len)
{
    uint8_t frame[128];
    if (len > 120) len = 120;

    frame[0] = 0xAA;
    frame[1] = cmd;
    frame[2] = len;

    uint8_t xor = cmd ^ len;
    for (uint8_t i = 0; i < len; i++)
    {
        frame[3 + i] = data[i];
        xor ^= data[i];
    }
    frame[3 + len] = xor;

    for (uint16_t i = 0; i < (4 + len); i++)
    {
        while (USART_GetFlagStatus(BLE_UART, USART_FLAG_TXE) == RESET);
        USART_SendData(BLE_UART, frame[i]);
    }
}

void BLE_SendFrame(uint8_t cmd, uint8_t *data, uint8_t len)
{
    SendFrame(cmd, data, len);
}

/* ======================== 系统初始化 ======================== */

void System_Init(void)
{
    // 使能各外设时钟
    RCC_APB2PeriphClockCmd(RCC_APB2Periph_USART1 | RCC_APB2Periph_SPI1 |
                            RCC_APB2Periph_GPIOA | RCC_APB2Periph_GPIOB |
                            RCC_APB2Periph_GPIOC | RCC_APB2Periph_ADC1 |
                            RCC_APB2Periph_AFIO, ENABLE);
    RCC_APB1PeriphClockCmd(RCC_APB1Periph_USART2 | RCC_APB1Periph_USART3 |
                            RCC_APB1Periph_TIM2 | RCC_APB1Periph_TIM3 |
                            RCC_APB1Periph_I2C1, ENABLE);

    // 默认状态
    memset((void *)&g_sys, 0, sizeof(g_sys));
    g_sys.isLocked = 1;
    g_sys.doorClosed = 1;
    g_sys.buzzerEnabled = 1;
    g_sys.autoLockEnabled = 1;
    g_sys.autoLockDelay = DOOR_AUTO_LOCK_DEFAULT;

    GPIO_Init_All();
    UART_Init_All();
    TIM_Init_All();
    NVIC_Init_All();
}

void GPIO_Init_All(void)
{
    GPIO_InitTypeDef GPIO_InitStruct;

    // PA8 — 蜂鸣器
    GPIO_InitStruct.GPIO_Pin = GPIO_Pin_8;
    GPIO_InitStruct.GPIO_Mode = GPIO_Mode_Out_PP;
    GPIO_InitStruct.GPIO_Speed = GPIO_Speed_2MHz;
    GPIO_Init(GPIOA, &GPIO_InitStruct);
    GPIO_ResetBits(GPIOA, GPIO_Pin_8);

    // PA0 — 门磁 (上拉输入)
    GPIO_InitStruct.GPIO_Pin = GPIO_Pin_0;
    GPIO_InitStruct.GPIO_Mode = GPIO_Mode_IPU;
    GPIO_Init(GPIOA, &GPIO_InitStruct);

    // PA1 — 出门按钮 (上拉输入)
    GPIO_InitStruct.GPIO_Pin = GPIO_Pin_1;
    GPIO_InitStruct.GPIO_Mode = GPIO_Mode_IPU;
    GPIO_Init(GPIOA, &GPIO_InitStruct);

    // PB12~15 — 步进电机
    GPIO_InitStruct.GPIO_Pin = GPIO_Pin_12 | GPIO_Pin_13 | GPIO_Pin_14 | GPIO_Pin_15;
    GPIO_InitStruct.GPIO_Mode = GPIO_Mode_Out_PP;
    GPIO_InitStruct.GPIO_Speed = GPIO_Speed_2MHz;
    GPIO_Init(GPIOB, &GPIO_InitStruct);
    GPIO_ResetBits(GPIOB, GPIO_Pin_12 | GPIO_Pin_13 | GPIO_Pin_14 | GPIO_Pin_15);

    // PC13 — LED 指示灯
    GPIO_InitStruct.GPIO_Pin = GPIO_Pin_13;
    GPIO_InitStruct.GPIO_Mode = GPIO_Mode_Out_PP;
    GPIO_Init(GPIOC, &GPIO_InitStruct);
    GPIO_SetBits(GPIOC, GPIO_Pin_13); // LED 低有效，初始灭
}

void UART_Init_All(void)
{
    GPIO_InitTypeDef GPIO_InitStruct;
    USART_InitTypeDef USART_InitStruct;

    // USART1 TX=PA9 RX=PA10 (调试)
    GPIO_InitStruct.GPIO_Pin = GPIO_Pin_9;
    GPIO_InitStruct.GPIO_Mode = GPIO_Mode_AF_PP;
    GPIO_InitStruct.GPIO_Speed = GPIO_Speed_50MHz;
    GPIO_Init(GPIOA, &GPIO_InitStruct);
    GPIO_InitStruct.GPIO_Pin = GPIO_Pin_10;
    GPIO_InitStruct.GPIO_Mode = GPIO_Mode_IN_FLOATING;
    GPIO_Init(GPIOA, &GPIO_InitStruct);

    USART_InitStruct.USART_BaudRate = 115200;
    USART_InitStruct.USART_WordLength = USART_WordLength_8b;
    USART_InitStruct.USART_StopBits = USART_StopBits_1;
    USART_InitStruct.USART_Parity = USART_Parity_No;
    USART_InitStruct.USART_Mode = USART_Mode_Tx | USART_Mode_Rx;
    USART_InitStruct.USART_HardwareFlowControl = USART_HardwareFlowControl_None;
    USART_Init(USART1, &USART_InitStruct);
    USART_Cmd(USART1, ENABLE);

    // USART2 TX=PA2 RX=PA3 (BLE)
    GPIO_InitStruct.GPIO_Pin = GPIO_Pin_2;
    GPIO_InitStruct.GPIO_Mode = GPIO_Mode_AF_PP;
    GPIO_InitStruct.GPIO_Speed = GPIO_Speed_50MHz;
    GPIO_Init(GPIOA, &GPIO_InitStruct);
    GPIO_InitStruct.GPIO_Pin = GPIO_Pin_3;
    GPIO_InitStruct.GPIO_Mode = GPIO_Mode_IN_FLOATING;
    GPIO_Init(GPIOA, &GPIO_InitStruct);

    USART_InitStruct.USART_BaudRate = BLE_BAUDRATE;
    USART_Init(USART2, &USART_InitStruct);
    USART_Cmd(USART2, ENABLE);
    USART_ITConfig(USART2, USART_IT_RXNE, ENABLE);

    // USART3 TX=PB10 RX=PB11 (指纹)
    GPIO_InitStruct.GPIO_Pin = GPIO_Pin_10;
    GPIO_InitStruct.GPIO_Mode = GPIO_Mode_AF_PP;
    GPIO_InitStruct.GPIO_Speed = GPIO_Speed_50MHz;
    GPIO_Init(GPIOB, &GPIO_InitStruct);
    GPIO_InitStruct.GPIO_Pin = GPIO_Pin_11;
    GPIO_InitStruct.GPIO_Mode = GPIO_Mode_IN_FLOATING;
    GPIO_Init(GPIOB, &GPIO_InitStruct);

    USART_InitStruct.USART_BaudRate = FP_BAUDRATE;
    USART_Init(USART3, &USART_InitStruct);
    USART_Cmd(USART3, ENABLE);
    USART_ITConfig(USART3, USART_IT_RXNE, ENABLE);
}

void TIM_Init_All(void)
{
    TIM_TimeBaseInitTypeDef TIM_InitStruct;

    // TIM2 — 系统定时 1ms
    TIM_InitStruct.TIM_Period = 999;
    TIM_InitStruct.TIM_Prescaler = 71;  // 72MHz / 72 = 1MHz
    TIM_InitStruct.TIM_CounterMode = TIM_CounterMode_Up;
    TIM_InitStruct.TIM_ClockDivision = TIM_CKD_DIV1;
    TIM_TimeBaseInit(TIM2, &TIM_InitStruct);
    TIM_ITConfig(TIM2, TIM_IT_Update, ENABLE);
    TIM_Cmd(TIM2, ENABLE);

    // TIM3 — 蜂鸣器 PWM
    TIM_InitStruct.TIM_Period = 999;
    TIM_InitStruct.TIM_Prescaler = 71;
    TIM_TimeBaseInit(TIM3, &TIM_InitStruct);
    TIM_OCInitTypeDef TIM_OCInitStruct;
    TIM_OCInitStruct.TIM_OCMode = TIM_OCMode_PWM1;
    TIM_OCInitStruct.TIM_OutputState = TIM_OutputState_Enable;
    TIM_OCInitStruct.TIM_Pulse = 500;
    TIM_OCInitStruct.TIM_OCPolarity = TIM_OCPolarity_High;
    TIM_OC1Init(TIM3, &TIM_OCInitStruct);
}

void NVIC_Init_All(void)
{
    NVIC_InitTypeDef NVIC_InitStruct;

    // TIM2 中断
    NVIC_InitStruct.NVIC_IRQChannel = TIM2_IRQn;
    NVIC_InitStruct.NVIC_IRQChannelPreemptionPriority = 1;
    NVIC_InitStruct.NVIC_IRQChannelSubPriority = 0;
    NVIC_InitStruct.NVIC_IRQChannelCmd = ENABLE;
    NVIC_Init(&NVIC_InitStruct);

    // USART2 (BLE) 中断
    NVIC_InitStruct.NVIC_IRQChannel = USART2_IRQn;
    NVIC_InitStruct.NVIC_IRQChannelPreemptionPriority = 2;
    NVIC_Init(&NVIC_InitStruct);

    // USART3 (指纹) 中断
    NVIC_InitStruct.NVIC_IRQChannel = USART3_IRQn;
    NVIC_Init(&NVIC_InitStruct);
}

/* ======================== 中断处理 ======================== */

// TIM2 — 系统 tick (1ms)
void TIM2_IRQHandler(void)
{
    if (TIM_GetITStatus(TIM2, TIM_IT_Update) != RESET)
    {
        TIM_ClearITPendingBit(TIM2, TIM_IT_Update);
        g_sysTick++;
        g_rtc.second++;
        if (g_rtc.second >= 60)
        {
            g_rtc.second = 0;
            g_rtc.minute++;
            if (g_rtc.minute >= 60)
            {
                g_rtc.minute = 0;
                g_rtc.hour++;
                if (g_rtc.hour >= 24)
                {
                    g_rtc.hour = 0;
                    g_rtc.day++;
                    // 简化处理，不做月份天数判断
                    if (g_rtc.day > 31)
                    {
                        g_rtc.day = 1;
                        g_rtc.month++;
                        if (g_rtc.month > 12)
                        {
                            g_rtc.month = 1;
                            g_rtc.year++;
                        }
                    }
                }
            }
        }
    }
}

// USART2 — BLE 蓝牙数据接收
void USART2_IRQHandler(void)
{
    if (USART_GetITStatus(USART2, USART_IT_RXNE) != RESET)
    {
        uint8_t data = USART_ReceiveData(USART2);

        if (g_bleFrame.len < 128)
        {
            g_bleFrame.buf[g_bleFrame.len++] = data;
        }

        // 帧头检测: 收到 0xAA 且已有数据则重新开始
        if (data == 0xAA && g_bleFrame.len > 1)
        {
            g_bleFrame.len = 1;
            g_bleFrame.buf[0] = 0xAA;
        }

        // 帧完整性检查
        if (g_bleFrame.len >= 4)
        {
            uint8_t dataLen = g_bleFrame.buf[2];
            uint8_t frameLen = 4 + dataLen;
            if (g_bleFrame.len >= frameLen)
            {
                // 校验
                uint8_t cmd = g_bleFrame.buf[1];
                uint8_t xor = cmd ^ dataLen;
                for (uint8_t i = 0; i < dataLen; i++)
                    xor ^= g_bleFrame.buf[3 + i];
                if (xor == g_bleFrame.buf[3 + dataLen])
                {
                    g_bleFrame.rxFlag = 1;
                }
                g_bleFrame.len = 0; // 准备接收下一帧
            }
        }
    }
}

// USART3 — 指纹模块 (简化处理，实际项目需要完整 AS608 协议)
void USART3_IRQHandler(void)
{
    if (USART_GetITStatus(USART3, USART_IT_RXNE) != RESET)
    {
        uint8_t data = USART_ReceiveData(USART3);
        // 解析 AS608 确认码...
    }
}

/* ======================== 工具函数 ======================== */

void Delay_ms(uint32_t ms)
{
    uint32_t start = g_sysTick;
    while ((g_sysTick - start) < ms);
}

void RTC_SetTime(uint8_t year, uint8_t month, uint8_t day,
                 uint8_t hour, uint8_t minute, uint8_t second, uint8_t weekday)
{
    g_rtc.year = 2000 + year;
    g_rtc.month = month;
    g_rtc.day = day;
    g_rtc.hour = hour;
    g_rtc.minute = minute;
    g_rtc.second = second;
    g_rtc.weekday = weekday;
}

void RTC_GetTime(RtcTime_t *t)
{
    memcpy(t, (void *)&g_rtc, sizeof(RtcTime_t));
}

// 简化版时间戳 (从2020-01-01 00:00:00开始)
uint32_t RTC_ToTimestamp(RtcTime_t *t)
{
    uint32_t days = 0;
    for (uint16_t y = 2020; y < t->year; y++)
    {
        days += 365;
        if ((y % 4 == 0 && y % 100 != 0) || y % 400 == 0) days++;
    }
    uint16_t monthDays[] = {0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    if ((t->year % 4 == 0 && t->year % 100 != 0) || t->year % 400 == 0)
        monthDays[2] = 29;
    for (uint8_t m = 1; m < t->month; m++)
        days += monthDays[m];
    days += t->day - 1;
    return days * 86400UL + t->hour * 3600UL + t->minute * 60UL + t->second;
}

void RTC_FromTimestamp(RtcTime_t *t, uint32_t ts)
{
    uint32_t days = ts / 86400UL;
    uint32_t secs = ts % 86400UL;
    t->hour = secs / 3600;
    t->minute = (secs % 3600) / 60;
    t->second = secs % 60;

    uint16_t y = 2020;
    while (days >= 365)
    {
        uint16_t ydays = ((y % 4 == 0 && y % 100 != 0) || y % 400 == 0) ? 366 : 365;
        if (days < ydays) break;
        days -= ydays;
        y++;
    }
    t->year = y;

    uint16_t monthDays[] = {0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    if ((y % 4 == 0 && y % 100 != 0) || y % 400 == 0) monthDays[2] = 29;
    uint8_t m = 1;
    while (days >= monthDays[m])
    {
        days -= monthDays[m];
        m++;
    }
    t->month = m;
    t->day = days + 1;
    t->weekday = 1;
}

/* ======================== DHT11 驱动 ======================== */

uint8_t DHT11_Read(uint8_t *temp, uint8_t *humi)
{
    // DHT11 单总线协议
    // 简化实现，实际项目需要精确时序
    GPIO_InitTypeDef GPIO_InitStruct;

    // 配置为输出，发送起始信号
    GPIO_InitStruct.GPIO_Pin = GPIO_Pin_7; // PD7 (根据实际接线)
    GPIO_InitStruct.GPIO_Mode = GPIO_Mode_Out_OD;
    GPIO_InitStruct.GPIO_Speed = GPIO_Speed_2MHz;
    GPIO_Init(GPIOA, &GPIO_InitStruct);

    GPIO_ResetBits(GPIOA, GPIO_Pin_7);
    Delay_ms(20); // 拉低至少18ms
    GPIO_SetBits(GPIOA, GPIO_Pin_7);
    Delay_us(30);

    // 切换为输入
    GPIO_InitStruct.GPIO_Mode = GPIO_Mode_IPU;
    GPIO_Init(GPIOA, &GPIO_InitStruct);

    // 等待 DHT11 应答...
    // 实际项目需要完整实现时序读取40bit数据

    *temp = 25; // 占位
    *humi = 60; // 占位
    return 0;
}

void Delay_us(uint32_t us)
{
    uint32_t start = g_sysTick * 1000; // 粗略实现
    while ((g_sysTick * 1000 - start) < us);
}

/* ======================== 矩阵键盘 ======================== */

// 4x4 键盘
// 行: PB0~PB3 (输出)
// 列: PB4~PB7 (输入上拉)

const char keyMap[4][4] = {
    {'1', '2', '3', 'A'},
    {'4', '5', '6', 'B'},
    {'7', '8', '9', 'C'},
    {'*', '0', '#', 'D'}
};

uint8_t Key_Scan(void)
{
    static uint8_t lastKey = 0;
    // 实际项目需要完整扫描逻辑
    return 0; // 简化
}

/* ======================== OLED 显示 (占位) ======================== */

void OLED_ShowStatus(void)
{
    // SSD1306 I2C 驱动
    // 实际项目需要完整实现
}

void OLED_ShowMenu(void)
{
    // 菜单显示
}

void OLED_ShowTime(void)
{
    // 时间显示
}
