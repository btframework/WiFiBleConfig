#include <BLEServer.h>
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <WiFi.h>

using namespace std;

/* WiFi AP commands */

#define WIFI_CMD_SET_SSID   0x01
#define WIFI_CMD_SET_PWD    0x02
#define WIFI_CMD_START      0x03
#define WIFI_CMD_STOP       0x04
#define WIFI_CMD_GET_STATUS 0x05

/* WiFi AP status */

#define WIFI_STATUS_STARTED     0x0001
#define WIFI_STATUS_STOPPED     0x0002
#define WIFI_STATUS_NO_CONFIG   0x0004
#define WIFI_STATUS_ERROR       0x0005

/* GATT attributes */

#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

/* Forward declaration */

class BleWiFi;

/* Global variables */

static BleWiFi* g_pBleWiFi = NULL;

/* BleWiFi class */

class BleWiFi : BLESecurityCallbacks,
                BLEServerCallbacks,
                BLECharacteristicCallbacks
{
private:
    /* GATT members */
    BLECharacteristic*  m_pCharacteristic;
    bool                m_DeviceConnected;

    /* WiFi members */

    string              m_Password;
    string              m_Ssid;
    uint16_t            m_WiFiStatus;

    /* GATT initialization */

    void InitSecurity()
    {
        // Uncomment the line below to enabled bonding
        //esp_ble_auth_req_t auth_req = ESP_LE_AUTH_REQ_SC_MITM_BOND;
        esp_ble_auth_req_t auth_req = ESP_LE_AUTH_NO_BOND;
        esp_ble_io_cap_t iocap = ESP_IO_CAP_NONE;
        uint8_t key_size = 16;
        uint8_t init_key = ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK;
        uint8_t rsp_key = ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK;
        uint32_t passkey = 123456;
        uint8_t auth_option = ESP_BLE_ONLY_ACCEPT_SPECIFIED_AUTH_DISABLE;
        uint8_t oob_support = ESP_BLE_OOB_DISABLE;
        
        // Uncommend the line below to enable encryotion
        //BLEDevice::setEncryptionLevel(ESP_BLE_SEC_ENCRYPT);
        BLEDevice::setSecurityCallbacks((BLESecurityCallbacks*)this);
        
        esp_ble_gap_set_security_param(ESP_BLE_SM_SET_STATIC_PASSKEY, &passkey, sizeof(uint32_t));
        esp_ble_gap_set_security_param(ESP_BLE_SM_AUTHEN_REQ_MODE, &auth_req, sizeof(uint8_t));
        esp_ble_gap_set_security_param(ESP_BLE_SM_IOCAP_MODE, &iocap, sizeof(uint8_t));
        esp_ble_gap_set_security_param(ESP_BLE_SM_MAX_KEY_SIZE, &key_size, sizeof(uint8_t));
        esp_ble_gap_set_security_param(ESP_BLE_SM_ONLY_ACCEPT_SPECIFIED_SEC_AUTH, &auth_option, sizeof(uint8_t));
        esp_ble_gap_set_security_param(ESP_BLE_SM_OOB_SUPPORT, &oob_support, sizeof(uint8_t));
        esp_ble_gap_set_security_param(ESP_BLE_SM_SET_INIT_KEY, &init_key, sizeof(uint8_t));
        esp_ble_gap_set_security_param(ESP_BLE_SM_SET_RSP_KEY, &rsp_key, sizeof(uint8_t));
    }

    void StartGattServer()
    {
        BLEServer *pServer = BLEDevice::createServer();
        pServer->setCallbacks((BLEServerCallbacks*)this);
        
        BLEService *pService = pServer->createService(SERVICE_UUID);
        m_pCharacteristic = pService->createCharacteristic(
            CHARACTERISTIC_UUID,
            BLECharacteristic::PROPERTY_READ |
            BLECharacteristic::PROPERTY_WRITE |
            BLECharacteristic::PROPERTY_NOTIFY);
        
        m_pCharacteristic->setValue(m_WiFiStatus);
        
        m_pCharacteristic->addDescriptor(new BLEDescriptor(BLEUUID((uint16_t)0x2902)));
        m_pCharacteristic->setCallbacks((BLECharacteristicCallbacks*)this);
        
        pService->start();
    }

    void StartLeAdvertising()
    {
        BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
        pAdvertising->addServiceUUID(SERVICE_UUID);
        pAdvertising->setScanResponse(true);    
        pAdvertising->setMinPreferred(0x06); // Helps with iPhone connections issue
        pAdvertising->setMinPreferred(0x12);
        
        BLEDevice::startAdvertising();
    }

    bool SetCharacteristicValue()
    {
        if (m_pCharacteristic != NULL)
        {
            uint16_t val = m_WiFiStatus;
            if (m_WiFiStatus == WIFI_STATUS_STARTED)
                val = val + (WiFi.softAPgetStationNum() << 8);
            m_pCharacteristic->setValue(val);
            return true;
        }
        return false;
    }

    void Notify()
    {
        if (SetCharacteristicValue())
        {
            if (m_DeviceConnected)
                m_pCharacteristic->notify(true);
        }
    }

    /* WiFi support */

    void UpdateWiFiStatus()
    {
        if (m_WiFiStatus == WIFI_STATUS_STARTED)
            StopAp();

        if (m_Password == "" || m_Ssid == "")
            m_WiFiStatus = WIFI_STATUS_NO_CONFIG;
        else
            m_WiFiStatus = WIFI_STATUS_STOPPED;
        
        Notify();
    }

    void StopAp()
    {
        WiFi.enableAP(false);
        WiFi.softAPdisconnect(true);
    }

    void SetWiFiEvents()
    {
        WiFi.onEvent(WiFiEvent);
    }

    /* WiFi event handlers */
    
    static void WiFiEvent(WiFiEvent_t event)
    {
        switch (event)
        {
            case ARDUINO_EVENT_WIFI_AP_STACONNECTED:
            case ARDUINO_EVENT_WIFI_AP_STADISCONNECTED:
                if (g_pBleWiFi != NULL)
                    g_pBleWiFi->Notify();
                break;
        }
    }

public:
    /* BLESecurityCallbacks */

    virtual uint32_t onPassKeyRequest() override
    {
        return 123456;
    }

    virtual void onPassKeyNotify(uint32_t pass_key) override
    {
        // Do nothing
    }

    virtual bool onConfirmPIN(uint32_t pass_key) override
    {
        vTaskDelay(5000);
        return true;
    }

    virtual bool onSecurityRequest() override
    {
        return true;
    }

    virtual void onAuthenticationComplete(esp_ble_auth_cmpl_t cmpl) override
    {
        // Do nothing
    }

public:
    /* BLEServerCallbacks */

    virtual void onConnect(BLEServer* pServer) override
    {
        m_DeviceConnected = true;
    }

    virtual void onDisconnect(BLEServer* pServer) override
    {
        m_DeviceConnected = false;
        // We need to restart advertising.
        BLEDevice::startAdvertising();
    }

public:
    /* BLECharacteristicCallbacks */

    virtual void onWrite(BLECharacteristic *pCharacteristic) override
    {
        if (pCharacteristic == m_pCharacteristic)
        {
            size_t Len = pCharacteristic->getLength();
            if (Len > 0)
            {
                uint8_t* Data = pCharacteristic->getData();
                uint8_t Cmd = Data[0];
                switch (Cmd)
                {
                    case WIFI_CMD_SET_SSID:
                        if (Len > 1)
                            m_Ssid = string((char*)&Data[1]);
                        else
                            m_Ssid = "";
                        UpdateWiFiStatus();
                        break;

                    case WIFI_CMD_SET_PWD:
                        if (Len > 1)
                            m_Password = string((char*)&Data[1]);
                        else
                            m_Password = "";
                        UpdateWiFiStatus();
                        break;
                        
                    case WIFI_CMD_START:
                        if (m_WiFiStatus == WIFI_STATUS_STOPPED)
                        {
                            if (WiFi.softAP(m_Ssid.c_str(), m_Password.c_str()))
                            {
                                m_WiFiStatus = WIFI_STATUS_STARTED;
                                Notify();
                            }
                        }
                        break;
                        
                    case WIFI_CMD_STOP:
                        if (m_WiFiStatus == WIFI_STATUS_STARTED)
                        {
                            StopAp();
                            m_WiFiStatus = WIFI_STATUS_STOPPED;
                            Notify();
                        }
                        break;
                    
                    case WIFI_CMD_GET_STATUS:
                        Notify();
                        break;
                }
            }
        }
    }

    virtual void onRead(BLECharacteristic* pCharacteristic) override
    {
        if (pCharacteristic == m_pCharacteristic)
            SetCharacteristicValue();
    }

public:
    BleWiFi()
    {
        m_pCharacteristic = NULL;
        m_DeviceConnected = false;

        m_Password = "";
        m_Ssid = "";
        m_WiFiStatus = WIFI_STATUS_NO_CONFIG;
        
        BLEDevice::init("GATT WiFi AP");
        
        InitSecurity();
        StartGattServer();
        StartLeAdvertising();

        StopAp();
        SetWiFiEvents();
    }
};

/* Main functions */

void setup()
{
    g_pBleWiFi = new BleWiFi();
}

void loop()
{

}