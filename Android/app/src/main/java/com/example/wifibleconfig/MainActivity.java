package com.example.wifibleconfig;

import android.Manifest;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    //region Constants
    private final static String TAG = "BLE_WIFI";

    private final static int ENABLE_BLUETOOTH_REQUEST_CODE = 1;
    private final static int RUNTIME_PERMISSION_REQUEST_CODE = 2;

    private final static String SERVICE_UUID = "4fafc201-1fb5-459e-8fcc-c5c9c331914b";
    private final static String CHARACTERISTIC_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8";
    private final static String CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb";

    private final static byte WIFI_STATUS_STARTED = 0x01;
    private final static byte WIFI_STATUS_STOPPED = 0x02;
    private final static byte WIFI_STATUS_NO_CONFIG = 0x04;
    private final static byte WIFI_STATUS_ERROR = 0x05;

    private final static byte WIFI_CMD_NONE = 0x00;
    private final static byte WIFI_CMD_SET_SSID = 0x01;
    private final static byte WIFI_CMD_SET_PWD = 0x02;
    private final static byte WIFI_CMD_START = 0x03;
    private final static byte WIFI_CMD_STOP = 0x04;
    private final static byte WIFI_CMD_GET_STATUS = 0x05;
    //endregion

    private Context _context;

    //region Bluetooth objects
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
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

    //region Permission check methods
    @SuppressLint("MissingPermission")
    private void promptEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE);
        }
    }

    private boolean hasPermission(String permissionType) {
        return (checkSelfPermission(permissionType) == PackageManager.PERMISSION_GRANTED);
    }

    private boolean hasRequiredRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return (hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
                    hasPermission(Manifest.permission.BLUETOOTH_CONNECT));
        }

        return hasPermission(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    private void requestRelevantRuntimePermissions() {
        if (!hasRequiredRuntimePermissions()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
                requestLocationPermission();
            else
                requestBluetoothPermissions();
        }
    }

    private void requestLocationPermission() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getResources().getString(R.string.location_dlg_title));
        builder.setMessage(getResources().getString(R.string.location_dlg_text));
        builder.setPositiveButton(android.R.string.ok, null);
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                requestPermissions(
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        RUNTIME_PERMISSION_REQUEST_CODE);
            }
        });
        builder.show();
    }

    private void requestBluetoothPermissions() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getResources().getString(R.string.bluetooth_dlg_title));
        builder.setMessage(getResources().getString(R.string.bluetooth_dlg_text));
        builder.setPositiveButton(android.R.string.ok, null);
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                String[] permissions = new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                };
                requestPermissions(permissions, RUNTIME_PERMISSION_REQUEST_CODE);
            }
        });
        builder.show();
    }
    //endregion

    private void showErrorMessage(String message) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getResources().getString(R.string.error_dlg_title));
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }

    //region Callbacks
    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        private void enableWiFiButtons(boolean enabled)
        {
            startWiFiButton.setEnabled(enabled);
            stopWiFiButton.setEnabled(enabled);
        }
        private void enableWiFiSettings(boolean enabled) {
            currentCommand = WIFI_CMD_NONE;

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
                case WIFI_STATUS_STARTED:
                    status = String.format(getResources().getString(R.string.wifi_status_started),
                            value[1]);
                    break;

                case WIFI_STATUS_STOPPED:
                    status = getResources().getString(R.string.wifi_status_stopped);
                    break;

                case WIFI_STATUS_NO_CONFIG:
                    status = getResources().getString(R.string.wifi_status_not_configured);
                    break;

                case WIFI_STATUS_ERROR:
                    status = getResources().getString(R.string.wifi_status_error);
                    break;

                default:
                    status = getResources().getString(R.string.wifi_status_unknown);
                    break;
            }

            MainActivity.this.runOnUiThread(new Runnable() {
                public void run() {
                    wifiStatusLabel.setText(status);
                    stopWiFiButton.setEnabled(value[0] == WIFI_STATUS_STARTED);
                }
            });
        }

        @SuppressLint("MissingPermission")
        private boolean enableNotifications() {
            UUID cccdUuid = UUID.fromString(CCC_DESCRIPTOR_UUID);

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
                case WIFI_CMD_SET_SSID:
                    String password = passwordEdit.getText().toString();
                    value = new byte[password.length() + 1];
                    byte[] str = password.getBytes(StandardCharsets.US_ASCII);
                    value[0] = WIFI_CMD_SET_PWD;
                    System.arraycopy(str, 0, value, 1, str.length);
                    setCharacteristicValue(value, WIFI_CMD_SET_PWD);
                    break;

                case WIFI_CMD_SET_PWD:
                    value = new byte[]{WIFI_CMD_START};
                    setCharacteristicValue(value, WIFI_CMD_START);
                    break;

                case WIFI_CMD_START:
                case WIFI_CMD_STOP:
                    currentCommand = WIFI_CMD_NONE;
                    MainActivity.this.runOnUiThread(new Runnable() {
                        public void run() {
                            enableWiFiButtons(true);
                        }
                    });
                    break;
            }
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
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            UUID serviceUuid = UUID.fromString(SERVICE_UUID);
            UUID characteristicUuid = UUID.fromString(CHARACTERISTIC_UUID);

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
                    //.setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                    //.setReportDelay(0L)
                    .build();
            ScanFilter filter = new ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid.fromString(SERVICE_UUID))
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
                showErrorMessage(getResources().getString(R.string.error_dlg_ssid_empty));
                return;
            }
            if (ssid.length() > 16) {
                showErrorMessage(getResources().getString(R.string.error_dlg_ssid_long));
                return;
            }

            if (password.length() == 0) {
                showErrorMessage(getResources().getString(R.string.error_dlg_password_empty));
                return;
            }
            if (password.length() > 16) {
                showErrorMessage(getResources().getString(R.string.error_dlg_password_long));
                return;
            }

            byte[] value = new byte[ssid.length() + 1];
            byte[] str = ssid.getBytes(StandardCharsets.US_ASCII);
            value[0] = WIFI_CMD_SET_SSID;
            System.arraycopy(str, 0, value, 1, str.length);

            boolean res = wifiCharacteristic.setValue(value);
            if (res)
                res = gattClient.writeCharacteristic(wifiCharacteristic);

            if (!res)
                gattClient.disconnect();
            else {
                startWiFiButton.setEnabled(false);
                stopWiFiButton.setEnabled(false);

                currentCommand = WIFI_CMD_SET_SSID;
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void stopWiFiButtonClicked() {
        if (wifiCharacteristic != null) {
            byte[] value = new byte[]{WIFI_CMD_STOP};
            boolean res = wifiCharacteristic.setValue(value);
            if (res)
                res = gattClient.writeCharacteristic(wifiCharacteristic);

            if (!res)
                gattClient.disconnect();
            else {
                startWiFiButton.setEnabled(false);
                stopWiFiButton.setEnabled(false);

                currentCommand = WIFI_CMD_STOP;
            }
        }
    }
    //endregion

    //region Activity overloads (events)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = null;
        gattClient = null;
        wifiCharacteristic = null;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!bluetoothAdapter.isEnabled()) {
            promptEnableBluetooth();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ENABLE_BLUETOOTH_REQUEST_CODE) {
            if (resultCode != Activity.RESULT_OK)
                promptEnableBluetooth();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NotNull String[] permissions,
                                           @NotNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case RUNTIME_PERMISSION_REQUEST_CODE:
                boolean containsDenial = Arrays.stream(grantResults).anyMatch(i -> i == PackageManager.PERMISSION_DENIED);
                if (containsDenial)
                    requestRelevantRuntimePermissions();
                break;
        }
    }
    //endregion
}