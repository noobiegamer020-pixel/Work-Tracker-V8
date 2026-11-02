package com.worktracker.app;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Gets "a Bluetooth device connected" even when the app is closed. */
public class CarReceiver extends BroadcastReceiver {
    @Override
    @SuppressWarnings("deprecation")
    public void onReceive(Context c, Intent i) {
        if (i == null || !BluetoothDevice.ACTION_ACL_CONNECTED.equals(i.getAction())) return;
        BluetoothDevice d;
        if (Build.VERSION.SDK_INT >= 33) {
            d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        } else {
            d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        }
        if (d == null) return;
        String name = null;
        String addr = null;
        boolean carClass = false;
        try {
            name = d.getName();
            if ((name == null || name.isEmpty()) && Build.VERSION.SDK_INT >= 30) name = d.getAlias();
            addr = d.getAddress();
            BluetoothClass bc = d.getBluetoothClass();
            carClass = bc != null && bc.getDeviceClass() == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO;
        } catch (SecurityException e) {
            // Bluetooth permission not granted yet
        }
        CarStore.onConnect(c, name, addr, carClass);
    }
}
