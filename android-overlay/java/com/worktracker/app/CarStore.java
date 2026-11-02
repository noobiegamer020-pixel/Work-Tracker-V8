package com.worktracker.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Storage and logic for the Bluetooth detection (no GPS, no foreground service). */
public class CarStore {
    static final String PREFS = "wt_car";
    static final String CH_MORNING = "car_morning_v2";
    static final int START_HOUR = 5;   // detection window: 05:00 ...
    static final int END_HOUR = 20;    // ... to 20:00

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.createNotificationChannel(chan());
    }

    static NotificationChannel chan() {
        NotificationChannel ch = new NotificationChannel(CH_MORNING, "Morning car question", NotificationManager.IMPORTANCE_HIGH);
        ch.enableVibration(true);
        return ch;
    }

    static void diag(Context c, String name, boolean car, boolean sent, String why) {
        try {
            JSONObject o = new JSONObject();
            o.put("t", System.currentTimeMillis());
            o.put("name", name == null ? "" : name);
            o.put("car", car);
            o.put("sent", sent);
            o.put("why", why);
            sp(c).edit().putString("diag", o.toString()).apply();
        } catch (Exception e) {
            // diagnostics only
        }
    }

    static boolean noCars(Context c) {
        try {
            return new JSONArray(sp(c).getString("cars", "[]")).length() == 0;
        } catch (Exception e) {
            return true;
        }
    }

    static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /** True when the name is one of the cars the app told us about. */
    static boolean isCar(Context c, String name) {
        if (name == null || name.trim().isEmpty()) return false;
        try {
            JSONArray a = new JSONArray(sp(c).getString("cars", "[]"));
            String n = name.trim().toLowerCase(Locale.ROOT);
            for (int i = 0; i < a.length(); i++) {
                String k = a.optString(i, "").trim().toLowerCase(Locale.ROOT);
                if (!k.isEmpty() && (n.equals(k) || n.contains(k) || k.contains(n))) return true;
            }
        } catch (Exception e) {
            // no cars saved yet
        }
        return false;
    }

    /**
     * A Bluetooth device connected. Each device is detected ONCE per day, so turning the car off and on
     * again does not repeat anything. Only the first car of the day sends the morning notification.
     */
    static synchronized void onConnect(Context c, String name, String addr, boolean carClass) {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        SharedPreferences p = sp(c);
        boolean car = isCar(c, name) || (carClass && noCars(c));
        if (hour < START_HOUR || hour >= END_HOUR) {
            diag(c, name, car, false, "outside 05:00-20:00");
            return;
        }
        String day = today();
        String devKey = (addr != null && !addr.isEmpty()) ? addr : String.valueOf(name);
        try {
            JSONObject seen = new JSONObject(p.getString("seen", "{}"));
            if (day.equals(seen.optString(devKey, ""))) {
                // already logged today; still ask once if this is the car and nothing was sent yet
                if (car && !day.equals(p.getString("carDay", ""))) {
                    p.edit().putString("carDay", day).apply();
                    morning(c, day, name, true);
                }
                return;
            }
            // keep only today's entries so the list never grows
            JSONObject next = new JSONObject();
            JSONArray names = seen.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String k = names.getString(i);
                    if (day.equals(seen.optString(k, ""))) next.put(k, day);
                }
            }
            next.put(devKey, day);
            JSONObject ev = new JSONObject();
            ev.put("t", now.getTimeInMillis());
            ev.put("name", name == null ? "" : name);
            ev.put("addr", addr == null ? "" : addr);
            JSONArray arr = new JSONArray(p.getString("events", "[]"));
            arr.put(ev);
            while (arr.length() > 100) arr.remove(0);
            SharedPreferences.Editor e = p.edit().putString("seen", next.toString()).putString("events", arr.toString());
            if (car && !day.equals(p.getString("carDay", ""))) {
                e.putString("carDay", day);
                e.apply();
                morning(c, day, name, true);
                return;
            }
            e.apply();
            diag(c, name, car, false, car ? "already sent today" : "not one of your cars");
        } catch (Exception ex) {
            // never crash a broadcast receiver
        }
    }

    static PendingIntent openApp(Context c, String act) {
        Intent i = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (i == null) i = new Intent();
        if (act != null) i.putExtra("wt_act", act);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getActivity(c, 7100, i, flags);
    }

    static void morning(Context c, String today) {
        morning(c, today, "test", true);
    }

    static void morning(Context c, String today, String devName, boolean isCarDev) {
        String title = "Good morning ☀️ Where are you working today?";
        String body = "Your car is connected. Tap to confirm or choose your region.";
        String act = "region";
        try {
            JSONObject all = new JSONObject(sp(c).getString("morning", "{}"));
            JSONObject o = all.optJSONObject(today);
            if (o != null) {
                title = o.optString("t", title);
                body = o.optString("b", body);
                act = o.optString("a", "plan0");
            }
        } catch (Exception ex) {
            // use the defaults
        }
        try {
            ensureChannels(c);
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (!androidx.core.app.NotificationManagerCompat.from(c).areNotificationsEnabled()) {
                diag(c, devName, isCarDev, false, "notifications are off for the app");
                return;
            }
            NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH_MORNING)
                    .setSmallIcon(R.drawable.ic_stat_icon)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setAutoCancel(true)
                    .setContentIntent(openApp(c, act));
            nm.notify(7100, b.build());
            diag(c, devName, isCarDev, true, "");
        } catch (Exception ex) {
            diag(c, devName, isCarDev, false, "error: " + ex.getClass().getSimpleName());
        }
    }
}
