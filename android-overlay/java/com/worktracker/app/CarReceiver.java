package com.worktracker.app;

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
        try {
            name = d.getName();
            addr = d.getAddress();
        } catch (SecurityException e) {
            // Bluetooth permission not granted yet
        }
        CarStore.onConnect(c, name, addr);
    }
}
