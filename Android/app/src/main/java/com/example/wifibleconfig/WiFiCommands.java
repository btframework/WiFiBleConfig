package com.example.wifibleconfig;

public class WiFiCommands {
    public final static byte WIFI_STATUS_STARTED = 0x01;
    public final static byte WIFI_STATUS_STOPPED = 0x02;
    public final static byte WIFI_STATUS_NO_CONFIG = 0x04;
    public final static byte WIFI_STATUS_ERROR = 0x05;

    public final static byte WIFI_CMD_NONE = 0x00;
    public final static byte WIFI_CMD_SET_SSID = 0x01;
    public final static byte WIFI_CMD_SET_PWD = 0x02;
    public final static byte WIFI_CMD_START = 0x03;
    public final static byte WIFI_CMD_STOP = 0x04;
    public final static byte WIFI_CMD_GET_STATUS = 0x05;
}
