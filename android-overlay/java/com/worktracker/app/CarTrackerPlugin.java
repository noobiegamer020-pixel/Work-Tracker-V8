package com.worktracker.app;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** JavaScript side of the Bluetooth detection. */
@CapacitorPlugin(name = "CarTracker")
public class CarTrackerPlugin extends Plugin {

    private boolean has(String perm) {
        return ContextCompat.checkSelfPermission(getContext(), perm) == PackageManager.PERMISSION_GRANTED;
    }

    @PluginMethod
    public void permissionState(PluginCall call) {
        JSObject o = new JSObject();
        o.put("bluetooth", Build.VERSION.SDK_INT < 31 || has(Manifest.permission.BLUETOOTH_CONNECT));
        o.put("notifications", Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS));
        call.resolve(o);
    }

    @PluginMethod
    public void askPermissions(PluginCall call) {
        List<String> l = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31 && !has(Manifest.permission.BLUETOOTH_CONNECT)) {
            l.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (Build.VERSION.SDK_INT >= 33 && !has(Manifest.permission.POST_NOTIFICATIONS)) {
            l.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!l.isEmpty()) {
            ActivityCompat.requestPermissions(getActivity(), l.toArray(new String[0]), 7711);
        }
        call.resolve();
    }

    /** cars = JSON list of Bluetooth names that are cars, morning = JSON map date -> {t, b}. */
    @PluginMethod
    public void setConfig(PluginCall call) {
        SharedPreferences.Editor e = CarStore.sp(getContext()).edit();
        String cars = call.getString("cars");
        if (cars != null) e.putString("cars", cars);
        String morning = call.getString("morning");
        if (morning != null) e.putString("morning", morning);
        e.apply();
        call.resolve();
    }

    @PluginMethod
    public void pairedDevices(PluginCall call) {
        JSArray arr = new JSArray();
        try {
            BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
            if (ad != null) {
                for (BluetoothDevice d : ad.getBondedDevices()) {
                    JSObject o = new JSObject();
                    o.put("name", d.getName());
                    o.put("address", d.getAddress());
                    arr.put(o);
                }
            }
        } catch (SecurityException ex) {
            call.reject("Bluetooth permission is missing");
            return;
        }
        JSObject r = new JSObject();
        r.put("devices", arr);
        call.resolve(r);
    }

    /** Bluetooth devices connected right now (audio / hands-free): proof that the phone sees the car. */
    @PluginMethod
    public void connectedNow(final PluginCall call) {
        final BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        final JSArray arr = new JSArray();
        final Set<String> seen = new HashSet<>();
        final int[] profiles = {BluetoothProfile.A2DP, BluetoothProfile.HEADSET};
        final int[] left = {profiles.length};
        final boolean[] fin = {false};
        final Runnable done = new Runnable() {
            @Override
            public void run() {
                if (fin[0]) return;
                fin[0] = true;
                JSObject r = new JSObject();
                r.put("devices", arr);
                call.resolve(r);
            }
        };
        if (ad == null) {
            done.run();
            return;
        }
        new Handler(Looper.getMainLooper()).postDelayed(done, 3000);
        for (int pr : profiles) {
            boolean ok;
            try {
                ok = ad.getProfileProxy(getContext(), new BluetoothProfile.ServiceListener() {
                    @Override
                    public void onServiceConnected(int profile, BluetoothProfile proxy) {
                        try {
                            for (BluetoothDevice d : proxy.getConnectedDevices()) {
                                String n = d.getName();
                                if (n != null && seen.add(n)) {
                                    JSObject o = new JSObject();
                                    o.put("name", n);
                                    arr.put(o);
                                }
                            }
                        } catch (SecurityException e) {
                            // permission missing: report nothing
                        }
                        ad.closeProfileProxy(profile, proxy);
                        left[0]--;
                        if (left[0] <= 0) done.run();
                    }

                    @Override
                    public void onServiceDisconnected(int profile) {
                    }
                }, pr);
            } catch (SecurityException e) {
                ok = false;
            }
            if (!ok) {
                left[0]--;
                if (left[0] <= 0) done.run();
            }
        }
    }

    /** Returns the devices detected since the last call (time and name) and clears them. */
    @PluginMethod
    public void drain(PluginCall call) {
        SharedPreferences p = CarStore.sp(getContext());
        String s;
        synchronized (CarStore.class) {
            s = p.getString("events", "[]");
            p.edit().putString("events", "[]").apply();
        }
        JSObject r = new JSObject();
        try {
            r.put("events", new JSArray(s));
        } catch (JSONException ex) {
            r.put("events", new JSArray());
        }
        call.resolve(r);
    }

    /** The screen the person tapped in a notification (set by MainActivity), then cleared. */
    @PluginMethod
    public void takeAction(PluginCall call) {
        SharedPreferences p = CarStore.sp(getContext());
        String a = p.getString("tap_act", "");
        if (!a.isEmpty()) p.edit().remove("tap_act").apply();
        JSObject r = new JSObject();
        r.put("act", a);
        call.resolve(r);
    }
}
