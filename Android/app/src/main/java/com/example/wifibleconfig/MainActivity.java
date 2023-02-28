package com.example.wifibleconfig;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;

public class MainActivity extends PermissionsChecker {

    private Context _context;

    //region Bluetooth objects
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt gattClient;
    private BluetoothGattCharacteristic wifiCharacteristic;
    //endregion

    //region UI objects
    private Button scanButton;
    private TextView stateLabel;
    private EditText ssidEdit;
    private EditText passwordEdit;
    private Button startWiFiButton;
    private Button stopWiFiButton;
    private TextView wifiStatusLabel;
    //endregion

    private byte currentCommand;

    //region Bluetooth callbacks
    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        private boolean enableNotifications() {
            UUID cccdUuid = UUID.fromString(WiFiGattServices.CCC_DESCRIPTOR_UUID);

            BluetoothGattDescriptor descriptor = wifiCharacteristic.getDescriptor(cccdUuid);
            if (descriptor == null)
                return false;

            if (!gattClient.setCharacteristicNotification(wifiCharacteristic, true))
                return false;

            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if (!gattClient.writeDescriptor(descriptor))
                return false;

            return true;
        }

        private void enableWiFiButtons(boolean enabled)
        {
            startWiFiButton.setEnabled(enabled);
            stopWiFiButton.setEnabled(enabled);
        }

        private void enableWiFiSettings(boolean enabled) {
            currentCommand = WiFiCommands.WIFI_CMD_NONE;

            ssidEdit.setEnabled(enabled);
            passwordEdit.setEnabled(enabled);
            enableWiFiButtons(enabled);

            wifiStatusLabel.setText(R.string.wifi_status_unknown);
            if (enabled)
                wifiStatusLabel.setVisibility(View.VISIBLE);
            else
                wifiStatusLabel.setVisibility(View.INVISIBLE);
        }

        private void updateWiFiStatus(byte[] value) {
            String status;
            switch (value[0]) {
                case WiFiCommands.WIFI_STATUS_STARTED:
                    status = String.format(getResources().getString(R.string.wifi_status_started),
                            value[1]);
                    break;

                case WiFiCommands.WIFI_STATUS_STOPPED:
                    status = getResources().getString(R.string.wifi_status_stopped);
                    break;

                case WiFiCommands.WIFI_STATUS_NO_CONFIG:
                    status = getResources().getString(R.string.wifi_status_not_configured);
                    break;

                case WiFiCommands.WIFI_STATUS_ERROR:
                    status = getResources().getString(R.string.wifi_status_error);
                    break;

                default:
                    status = getResources().getString(R.string.wifi_status_unknown);
                    break;
            }

            MainActivity.this.runOnUiThread(new Runnable() {
                public void run() {
                    wifiStatusLabel.setText(status);
                    stopWiFiButton.setEnabled(value[0] == WiFiCommands.WIFI_STATUS_STARTED);
                }
            });
        }

        @SuppressLint("MissingPermission")
        private void setCharacteristicValue(byte[] value, byte command) {
            boolean res = wifiCharacteristic.setValue(value);
            if (res)
                res = gattClient.writeCharacteristic(wifiCharacteristic);

            if (!res)
                gattClient.disconnect();
            else
                currentCommand = command;
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            byte[] value = characteristic.getValue();
            if (value != null && value.length == 2)
                updateWiFiStatus(value);
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status != BluetoothGatt.GATT_SUCCESS)
                gattClient.disconnect();
            else {
                if (!enableNotifications())
                    gattClient.disconnect();
                else {
                    byte[] value = characteristic.getValue();
                    if (value != null && value.length == 2)
                        updateWiFiStatus(value);
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            byte[] value;
            boolean res;
            switch (currentCommand) {
                case WiFiCommands.WIFI_CMD_SET_SSID:
                    String password = passwordEdit.getText().toString();
                    value = new byte[password.length() + 1];
                    byte[] str = password.getBytes(StandardCharsets.US_ASCII);
                    value[0] = WiFiCommands.WIFI_CMD_SET_PWD;
                    System.arraycopy(str, 0, value, 1, str.length);
                    setCharacteristicValue(value, WiFiCommands.WIFI_CMD_SET_PWD);
                    break;

                case WiFiCommands.WIFI_CMD_SET_PWD:
                    value = new byte[]{WiFiCommands.WIFI_CMD_START};
                    setCharacteristicValue(value, WiFiCommands.WIFI_CMD_START);
                    break;

                case WiFiCommands.WIFI_CMD_START:
                case WiFiCommands.WIFI_CMD_STOP:
                    currentCommand = WiFiCommands.WIFI_CMD_NONE;
                    MainActivity.this.runOnUiThread(new Runnable() {
                        public void run() {
                            enableWiFiButtons(true);
                        }
                    });
                    break;
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            MainActivity.this.runOnUiThread(new Runnable() {
                public void run() {
                    scanButton.setEnabled(true);
                }
            });

            if (status == BluetoothGatt.GATT_SUCCESS) {
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        MainActivity.this.runOnUiThread(new Runnable() {
                            public void run() {
                                stateLabel.setText(R.string.connected);
                                scanButton.setText(R.string.disconnect);
                                enableWiFiSettings(true);
                            }
                        });
                        gatt.discoverServices();
                        break;

                    case BluetoothProfile.STATE_DISCONNECTED:
                        wifiCharacteristic = null;
                        gattClient = null;
                        MainActivity.this.runOnUiThread(new Runnable() {
                            public void run() {
                                stateLabel.setText(R.string.stopped);
                                scanButton.setText(R.string.start_scan);
                                enableWiFiSettings(false);
                            }
                        });
                        break;
                }
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            UUID serviceUuid = UUID.fromString(WiFiGattServices.SERVICE_UUID);
            UUID characteristicUuid = UUID.fromString(WiFiGattServices.CHARACTERISTIC_UUID);

            BluetoothGattService service = gatt.getService(serviceUuid);
            if (service != null) {
                wifiCharacteristic = service.getCharacteristic(characteristicUuid);
                if (wifiCharacteristic != null) {
                    if (!gattClient.readCharacteristic(wifiCharacteristic))
                        wifiCharacteristic = null;
                }
            }

            if (wifiCharacteristic == null)
                gattClient.disconnect();
        }
    };

    private ScanCallback scanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice bluetoothDevice = result.getDevice();

            stateLabel.setText(String.format(getResources().getString(R.string.device_found),
                    result.getDevice().getAddress()));
            stopScanning(true);
            scanButton.setEnabled(false);

            gattClient = bluetoothDevice.connectGatt(_context, false, gattCallback,
                    BluetoothDevice.TRANSPORT_LE);
        }
    };
    //endregion

    //region Bluetooth LE scanning control
    @SuppressLint("MissingPermission")
    private void startScanning() {
        if (bluetoothLeScanner == null) {
            if (!hasRequiredRuntimePermissions())
                requestRelevantRuntimePermissions();

            if (hasRequiredRuntimePermissions())
                bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }

        if (bluetoothLeScanner == null)
            stateLabel.setText(R.string.no_permissions);
        else {
            ScanSettings scanSettings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    .build();
            ScanFilter filter = new ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid.fromString(WiFiGattServices.SERVICE_UUID))
                    .build();
            bluetoothLeScanner.startScan(Collections.singletonList(filter), scanSettings,
                    scanCallback);

            scanButton.setText(R.string.stop_scan);
            stateLabel.setText(R.string.started);
        }
    }

    private void stopScanning() {
        stopScanning(false);
    }

    @SuppressLint("MissingPermission")
    private void stopScanning(boolean deviceFound) {
        if (bluetoothLeScanner != null) {
            bluetoothLeScanner.stopScan(scanCallback);
            bluetoothLeScanner = null;

            scanButton.setText(R.string.start_scan);
            if (!deviceFound)
                stateLabel.setText(R.string.stopped);
        }
    }
    //endregion

    //region UI events
    @SuppressLint("MissingPermission")
    private void scanButtonClicked() {
        if (gattClient == null) {
            if (bluetoothLeScanner == null)
                startScanning();
            else
                stopScanning();
        } else {
            gattClient.disconnect();
        }
    }

    @SuppressLint("MissingPermission")
    private void startWiFiButtonClicked() {
        if (wifiCharacteristic != null) {
            String ssid = ssidEdit.getText().toString();
            String password = passwordEdit.getText().toString();

            if (ssid.length() == 0) {
                ErrorDialog.showErrorMessage(this, R.string.error_dlg_ssid_empty);
                return;
            }
            if (ssid.length() > 16) {
                ErrorDialog.showErrorMessage(this, R.string.error_dlg_ssid_long);
                return;
            }

            if (password.length() == 0) {
                ErrorDialog.showErrorMessage(this, R.string.error_dlg_password_empty);
                return;
            }
            if (password.length() > 16) {
                ErrorDialog.showErrorMessage(this, R.string.error_dlg_password_long);
                return;
            }

            byte[] value = new byte[ssid.length() + 1];
            byte[] str = ssid.getBytes(StandardCharsets.US_ASCII);
            value[0] = WiFiCommands.WIFI_CMD_SET_SSID;
            System.arraycopy(str, 0, value, 1, str.length);

            boolean res = wifiCharacteristic.setValue(value);
            if (res)
                res = gattClient.writeCharacteristic(wifiCharacteristic);

            if (!res)
                gattClient.disconnect();
            else {
                startWiFiButton.setEnabled(false);
                stopWiFiButton.setEnabled(false);

                currentCommand = WiFiCommands.WIFI_CMD_SET_SSID;
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void stopWiFiButtonClicked() {
        if (wifiCharacteristic != null) {
            byte[] value = new byte[]{WiFiCommands.WIFI_CMD_STOP};
            boolean res = wifiCharacteristic.setValue(value);
            if (res)
                res = gattClient.writeCharacteristic(wifiCharacteristic);

            if (!res)
                gattClient.disconnect();
            else {
                startWiFiButton.setEnabled(false);
                stopWiFiButton.setEnabled(false);

                currentCommand = WiFiCommands.WIFI_CMD_STOP;
            }
        }
    }
    //endregion

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        scanButton = (Button)findViewById(R.id.StartStopScanButton);
        scanButton.setText(R.string.start_scan);
        scanButton.setOnClickListener(v -> scanButtonClicked());

        stateLabel = (TextView)findViewById(R.id.StateLabel);
        stateLabel.setText(R.string.stopped);

        ssidEdit = (EditText)findViewById(R.id.EditSsdi);
        ssidEdit.setEnabled(false);

        passwordEdit = (EditText)findViewById(R.id.EditPassword);
        passwordEdit.setEnabled(false);

        startWiFiButton = (Button)findViewById(R.id.StartWiFiButton);
        startWiFiButton.setEnabled(false);
        startWiFiButton.setOnClickListener(v -> startWiFiButtonClicked());

        stopWiFiButton = (Button)findViewById(R.id.StopWiFiButton);
        stopWiFiButton.setEnabled(false);
        stopWiFiButton.setOnClickListener(v -> stopWiFiButtonClicked());

        wifiStatusLabel = (TextView)findViewById(R.id.WiFiStatusLabel);
        wifiStatusLabel.setVisibility(View.INVISIBLE);

        bluetoothLeScanner = null;
        gattClient = null;
        wifiCharacteristic = null;
    }
}