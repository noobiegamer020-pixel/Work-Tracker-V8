package com.worktracker.app;

import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.view.WindowManager;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * Rebuilt from the way the app calls it: alarms in the Clock app, battery exemption,
 * fingerprint lock, screenshot lock, saving backups and sharing PDFs / WhatsApp.
 * If you already have your own ClockAlarm plugin, keep yours and delete this file.
 */
@CapacitorPlugin(name = "ClockAlarm")
public class ClockAlarmPlugin extends Plugin {

    private static final int AUTH = BiometricManager.Authenticators.BIOMETRIC_STRONG
            | BiometricManager.Authenticators.DEVICE_CREDENTIAL;

    private boolean installed(String pkg) {
        try {
            getContext().getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @PluginMethod
    public void whatsappInstalled(PluginCall call) {
        JSObject o = new JSObject();
        o.put("messenger", installed("com.whatsapp"));
        o.put("business", installed("com.whatsapp.w4b"));
        call.resolve(o);
    }

    @PluginMethod
    public void batteryStatus(PluginCall call) {
        PowerManager pm = (PowerManager) getContext().getSystemService(android.content.Context.POWER_SERVICE);
        boolean ok = pm != null && pm.isIgnoringBatteryOptimizations(getContext().getPackageName());
        JSObject o = new JSObject();
        o.put("ignoring", ok);
        call.resolve(o);
    }

    @PluginMethod
    public void requestBatteryExemption(PluginCall call) {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getContext().getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
        } catch (Exception e) {
            openSettingsInternal();
        }
        call.resolve();
    }

    private void openSettingsInternal() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getContext().getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
    }

    @PluginMethod
    public void openAppSettings(PluginCall call) {
        openSettingsInternal();
        call.resolve();
    }

    /** Alarm in the phone's Clock app. days: Calendar day numbers (1 = Sunday ... 7 = Saturday). */
    @PluginMethod
    public void setAlarm(PluginCall call) {
        try {
            Intent i = new Intent(AlarmClock.ACTION_SET_ALARM);
            i.putExtra(AlarmClock.EXTRA_HOUR, call.getInt("hour", 6));
            i.putExtra(AlarmClock.EXTRA_MINUTES, call.getInt("minute", 0));
            i.putExtra(AlarmClock.EXTRA_MESSAGE, call.getString("message", "Work Tracker"));
            i.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
            JSArray days = call.getArray("days");
            if (days != null && days.length() > 0) {
                ArrayList<Integer> d = new ArrayList<>();
                for (int k = 0; k < days.length(); k++) d.add(days.getInt(k));
                i.putExtra(AlarmClock.EXTRA_DAYS, d);
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("Could not create the alarm: " + e.getMessage());
        }
    }

    @PluginMethod
    public void biometricStatus(PluginCall call) {
        JSObject o = new JSObject();
        o.put("available", BiometricManager.from(getContext()).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS);
        call.resolve(o);
    }

    @PluginMethod
    public void authenticate(final PluginCall call) {
        final String title = call.getString("title", "Work Tracker");
        final String sub = call.getString("subtitle", "Confirm it is you");
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    BiometricPrompt.AuthenticationCallback cb = new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                            call.resolve();
                        }

                        @Override
                        public void onAuthenticationError(int code, CharSequence msg) {
                            call.reject(String.valueOf(msg));
                        }
                    };
                    BiometricPrompt bp = new BiometricPrompt(getActivity(),
                            ContextCompat.getMainExecutor(getContext()), cb);
                    BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                            .setTitle(title).setSubtitle(sub).setAllowedAuthenticators(AUTH).build();
                    bp.authenticate(info);
                } catch (Exception e) {
                    call.reject("Authentication is not available");
                }
            }
        });
    }

    @PluginMethod
    public void setSecure(PluginCall call) {
        final boolean on = Boolean.TRUE.equals(call.getBoolean("on", false));
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (on) {
                    getActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE);
                } else {
                    getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                }
            }
        });
        call.resolve();
    }

    /** Saves a file (backup) in the Downloads folder. */
    @PluginMethod
    public void saveFile(PluginCall call) {
        String b64 = call.getString("base64");
        String name = call.getString("fileName", "work-tracker-backup.json");
        String mime = call.getString("mime", "application/octet-stream");
        if (b64 == null) {
            call.reject("No data");
            return;
        }
        try {
            byte[] data = Base64.decode(b64, Base64.DEFAULT);
            OutputStream os;
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, mime);
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri u = getContext().getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (u == null) throw new Exception("Cannot create the file");
                os = getContext().getContentResolver().openOutputStream(u);
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                dir.mkdirs();
                os = new FileOutputStream(new File(dir, name));
            }
            os.write(data);
            os.close();
            JSObject o = new JSObject();
            o.put("path", "Downloads/" + name);
            call.resolve(o);
        } catch (Exception e) {
            call.reject("Could not save: " + e.getMessage());
        }
    }

    /** Opens the phone's own calculator app. */
    @PluginMethod
    public void openCalculator(PluginCall call) {
        try {
            Intent i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("No calculator app found");
        }
    }

    /** The text last copied (used to bring the result of the phone calculator back). */
    @PluginMethod
    public void readClipboard(PluginCall call) {
        String t = "";
        try {
            ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence c = cm.getPrimaryClip().getItemAt(0).coerceToText(getContext());
                if (c != null) t = c.toString();
            }
        } catch (Exception e) {
            // clipboard not readable
        }
        JSObject o = new JSObject();
        o.put("text", t);
        call.resolve(o);
    }

    /** Opens a PDF in the phone's PDF viewer (it has Print and Share). */
    @PluginMethod
    public void openFile(PluginCall call) {
        String b64 = call.getString("base64");
        String name = call.getString("fileName", "document.pdf");
        String mime = call.getString("mime", "application/pdf");
        if (b64 == null) {
            call.reject("No data");
            return;
        }
        try {
            File dir = new File(getContext().getCacheDir(), "pdf");
            dir.mkdirs();
            File f = new File(dir, name);
            FileOutputStream fo = new FileOutputStream(f);
            fo.write(Base64.decode(b64, Base64.DEFAULT));
            fo.close();
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", f);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("No PDF viewer: " + e.getMessage());
        }
    }

    /** Shares a PDF (optionally straight to a WhatsApp package and number). */
    @PluginMethod
    public void sendPdf(PluginCall call) {
        String b64 = call.getString("base64");
        String name = call.getString("fileName", "document.pdf");
        String pkg = call.getString("pkg", "");
        String number = call.getString("number", "");
        String text = call.getString("text", "");
        if (b64 == null) {
            call.reject("No data");
            return;
        }
        try {
            File dir = new File(getContext().getCacheDir(), "pdf");
            dir.mkdirs();
            File f = new File(dir, name);
            FileOutputStream fo = new FileOutputStream(f);
            fo.write(Base64.decode(b64, Base64.DEFAULT));
            fo.close();
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", f);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("application/pdf");
            i.putExtra(Intent.EXTRA_STREAM, uri);
            if (text != null && !text.isEmpty()) i.putExtra(Intent.EXTRA_TEXT, text);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (pkg != null && !pkg.isEmpty() && installed(pkg)) {
                i.setPackage(pkg);
                if (number != null && !number.isEmpty()) i.putExtra("jid", number + "@s.whatsapp.net");
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(i);
            } else {
                Intent c = Intent.createChooser(i, "Share PDF");
                c.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(c);
            }
            call.resolve();
        } catch (Exception e) {
            call.reject("Could not share: " + e.getMessage());
        }
    }

    @PluginMethod
    public void openWhatsApp(PluginCall call) {
        String pkg = call.getString("pkg", "com.whatsapp");
        String number = call.getString("number", "");
        String text = call.getString("text", "");
        try {
            Uri u = Uri.parse("https://wa.me/" + number + "?text=" + Uri.encode(text));
            Intent i = new Intent(Intent.ACTION_VIEW, u);
            if (installed(pkg)) i.setPackage(pkg);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("WhatsApp could not open");
        }
    }
}
