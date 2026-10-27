// Run after "npx cap add android". Adds the native files, permissions and signing to the generated project.
const fs = require('fs');
const path = require('path');

const APP = 'android/app';
const PKG_DIR = path.join(APP, 'src/main/java/com/worktracker/app');
const MANIFEST = path.join(APP, 'src/main/AndroidManifest.xml');
const GRADLE = path.join(APP, 'build.gradle');

function copyDir(src, dst) {
  fs.mkdirSync(dst, { recursive: true });
  for (const f of fs.readdirSync(src)) {
    const s = path.join(src, f), d = path.join(dst, f);
    fs.statSync(s).isDirectory() ? copyDir(s, d) : fs.copyFileSync(s, d);
  }
}

// 1. Java files (overwrites the generated MainActivity) and the notification icon
copyDir('android-overlay/java/com/worktracker/app', PKG_DIR);
copyDir('android-overlay/res', path.join(APP, 'src/main/res'));

// 2. Manifest: permissions, WhatsApp visibility, Bluetooth receiver
let m = fs.readFileSync(MANIFEST, 'utf8');
if (!m.includes('CarReceiver')) {
  const perms = [
    'android.permission.BLUETOOTH_CONNECT',
    'android.permission.POST_NOTIFICATIONS',
    'android.permission.SCHEDULE_EXACT_ALARM',
    'android.permission.WAKE_LOCK',
    'android.permission.VIBRATE',
    'android.permission.USE_BIOMETRIC',
    'android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS',
    'com.android.alarm.permission.SET_ALARM'
  ].map(p => `    <uses-permission android:name="${p}" />`).join('\n') +
    '\n    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />' +
    '\n    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />' +
    '\n    <queries>\n        <package android:name="com.whatsapp" />\n        <package android:name="com.whatsapp.w4b" />\n    </queries>\n';

  const comps =
`        <receiver android:name=".CarReceiver" android:exported="true">
            <intent-filter>
                <action android:name="android.bluetooth.device.action.ACL_CONNECTED" />
            </intent-filter>
        </receiver>
`;
  m = m.replace('</application>', comps + '    </application>');
  m = m.replace('</manifest>', perms + '</manifest>');
  fs.writeFileSync(MANIFEST, m);
}

// 2b. Launcher icon (your goal picture on the app gradient)
m = fs.readFileSync(MANIFEST, 'utf8');
m = m.replace('@mipmap/ic_launcher_round', '@mipmap/wt_launcher_round').replace('@mipmap/ic_launcher"', '@mipmap/wt_launcher"');
fs.writeFileSync(MANIFEST, m);

// 3. Gradle: fingerprint library + signing with signing/work-tracker.jks
let g = fs.readFileSync(GRADLE, 'utf8');
if (!g.includes('androidx.biometric')) {
  g = g.replace(/dependencies\s*\{/, 'dependencies {\n    implementation "androidx.biometric:biometric:1.1.0"');
}
if (!g.includes('WT signing')) {
  g += `

// WT signing: same key every build, so new APKs install over the old one
android {
    signingConfigs {
        release {
            storeFile file("../../signing/work-tracker.jks")
            storePassword "worktracker"
            keyAlias "worktracker"
            keyPassword "worktracker"
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
        }
    }
}
`;
}
fs.writeFileSync(GRADLE, g);
console.log('Android project patched');
