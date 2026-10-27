# Work Tracker APK

`web/Work_Tracker.html` is your app. Edit it, push to GitHub, and the workflow builds a new APK.

## First time
1. Create a **private** GitHub repository and upload everything in this folder (keep the `.github` folder).
2. Open the **Actions** tab, then **Build APK**, then **Run workflow**. It takes about 5 to 10 minutes.
3. When it finishes, open the run and download:
   - **Work-Tracker-APK**: your APK (unzip it, install `app-release.apk`).
   - **KEEP-THIS-signing-key**: only the first time. Unzip it and upload `work-tracker.jks` into the repo's `signing/` folder (Add file, Upload files). Later builds then use the same key, so each new APK installs over the old one and keeps your data.
4. On the phone, open the app, go to Settings and tap **Allow notifications and check everything** (notifications, alarms, battery and Bluetooth). No location permission is needed any more.

## Moving from your old APK
The old APK was signed with a different key, so Android will not install this one over it.
1. In the OLD app: Settings, Backup, save the backup file.
2. Uninstall the old app, install the new APK.
3. In the new app: restore the backup.

## Every update
Change `web/Work_Tracker.html`, commit, push. The APK is built again (or tap Run workflow).

## Folders
- `web/` your app and `native-bridge.js` (reminders and alarm buttons).
- `android-overlay/` native Android code: Bluetooth detection (`CarReceiver`, `CarStore`, `CarTrackerPlugin`) and `ClockAlarmPlugin`.
- `scripts/` copies the web app, adds permissions and signing to the generated Android project.
- `.github/workflows/build-apk.yml` the build.

If you already have your own `ClockAlarm` plugin, delete `ClockAlarmPlugin.java` and its line in `MainActivity.java`.
