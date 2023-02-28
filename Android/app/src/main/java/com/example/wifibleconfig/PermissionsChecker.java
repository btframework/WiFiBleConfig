package com.example.wifibleconfig;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class PermissionsChecker extends BluetoothObjects {
    private final static int ENABLE_BLUETOOTH_REQUEST_CODE = 1;
    private final static int RUNTIME_PERMISSION_REQUEST_CODE = 2;

    private boolean hasPermission(String permissionType) {
        return (checkSelfPermission(permissionType) == PackageManager.PERMISSION_GRANTED);
    }

    @SuppressLint("MissingPermission")
    private void promptEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE);
        }
    }

    private void requestBluetoothPermissions() {
        showRequestPermissionDialog(R.string.bluetooth_dlg_title, R.string.bluetooth_dlg_text,
                new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                });
    }

    private void requestLocationPermission() {
        showRequestPermissionDialog(R.string.location_dlg_title, R.string.location_dlg_text,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION});
    }

    private void showRequestPermissionDialog(int titleId, int textId, String[] permissions) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getResources().getString(titleId));
        builder.setMessage(getResources().getString(textId));
        builder.setPositiveButton(android.R.string.ok, null);
        builder.setOnDismissListener(dialog -> requestPermissions(permissions, RUNTIME_PERMISSION_REQUEST_CODE));
        builder.show();
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!bluetoothAdapter.isEnabled()) {
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

    public boolean hasRequiredRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return (hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
                    hasPermission(Manifest.permission.BLUETOOTH_CONNECT));
        }

        return hasPermission(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    public void requestRelevantRuntimePermissions() {
        if (!hasRequiredRuntimePermissions()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
                requestLocationPermission();
            else
                requestBluetoothPermissions();
        }
    }
}
