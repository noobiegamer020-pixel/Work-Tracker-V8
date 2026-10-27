package com.worktracker.app;

import android.content.Intent;
import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(CarTrackerPlugin.class);
        registerPlugin(ClockAlarmPlugin.class);
        super.onCreate(savedInstanceState);
        CarStore.ensureChannels(this);
        remember(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        remember(intent);
    }

    /** A tapped car notification carries "wt_act"; the web page reads it with CarTracker.takeAction(). */
    private void remember(Intent i) {
        if (i == null) return;
        String a = i.getStringExtra("wt_act");
        if (a != null && !a.isEmpty()) {
            CarStore.sp(this).edit().putString("tap_act", a).apply();
            i.removeExtra("wt_act");
        }
    }
}
