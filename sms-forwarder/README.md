# SMS Forwarder — Android APK

Forwards incoming SMS from both SIM slots to a configurable webhook. Runs as a persistent foreground service that auto-restarts on crash and starts automatically on device boot.

## Features

- **Dual SIM support** — detects which SIM slot received the message; maps each slot to a configurable phone number (`sent_to` field)
- **Persistent background service** — runs as an Android ForegroundService with `START_STICKY`; restarts automatically if killed
- **Auto-start on boot** — `BootReceiver` triggers the service after every device reboot, including fast-boot
- **Allowed senders filter** — optionally restrict forwarding to a specific list of phone numbers or sender names
- **Webhook payload** matches your exact format:

```json
{
  "secret": "STC50",
  "from": "+9665XXXXXXXX",
  "message": "Your OTP is 1234",
  "sent_timestamp": 1714900000000,
  "sent_to": "050093478",
  "message_id": "+9665XXXXXXXX",
  "device_id": "6"
}
```

---

## How to Build the APK

### Option A — Android Studio (recommended)

1. Install [Android Studio](https://developer.android.com/studio)
2. Open Android Studio → **File → Open** → select the `sms-forwarder/` folder
3. Let Gradle sync (may take a few minutes on first run)
4. To build a **debug APK** (install directly on your phone):
   - **Build → Build Bundle(s) / APK(s) → Build APK(s)**
   - APK will be at `app/build/outputs/apk/debug/app-debug.apk`
5. To build a **release APK**:
   - **Build → Generate Signed Bundle / APK → APK**
   - Create or use a keystore, fill in the details, choose `release` build variant
   - APK will be at `app/build/outputs/apk/release/app-release.apk`

### Option B — Command line (requires Android SDK + JDK 8+)

```bash
cd sms-forwarder

# Make gradlew executable (Mac/Linux)
chmod +x gradlew

# Debug APK
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Release APK (unsigned — for signing see below)
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release-unsigned.apk
```

> **Windows:** use `gradlew.bat` instead of `./gradlew`

### Gradle wrapper jar (required for CLI builds)

The `gradle-wrapper.jar` binary is not included (it's a binary file). Get it by running:

```bash
# Inside the sms-forwarder/ folder, if you have any Gradle version installed:
gradle wrapper --gradle-version 8.4

# OR copy it from an existing Android Studio project:
# .gradle/wrapper/dists/.../gradle-wrapper.jar  →  sms-forwarder/gradle/wrapper/gradle-wrapper.jar
```

---

## Installing the APK on Your Phone

1. Enable **Unknown Sources** on your Android phone:
   - Settings → Security (or Apps) → Install unknown apps → enable for your file manager / ADB
2. Transfer `app-debug.apk` to the phone (USB, email, Google Drive, etc.)
3. Tap the APK file and install it

Or via ADB:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## First-time App Setup

1. Open **SMS Forwarder**
2. Grant all requested permissions (SMS, Phone State, Notifications)
3. Fill in **Webhook URL**, **Secret Key**, and **Device ID**
4. Set your **SIM 1** and **SIM 2** phone numbers (these appear as `sent_to`)
5. Optionally enable **Allowed Senders** and add sender numbers/names
6. Tap **Disable Battery Optimisation** — **this is critical** for the service to survive on most Android phones (Xiaomi, Samsung, Huawei, etc. kill background apps aggressively)
7. Toggle the service **ON**

The persistent notification ("SMS Forwarder — Running") confirms the service is active.

---

## Permissions Required

| Permission | Why |
|---|---|
| `RECEIVE_SMS` | Intercept incoming SMS |
| `READ_SMS` | Read SMS content |
| `INTERNET` | Send webhook HTTP requests |
| `RECEIVE_BOOT_COMPLETED` | Start service after reboot |
| `FOREGROUND_SERVICE` | Keep service running persistently |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent Android from killing the service |
| `READ_PHONE_STATE` | Detect which SIM slot received each SMS |
| `POST_NOTIFICATIONS` | Show persistent status notification (Android 13+) |

---

## Architecture

```
SmsReceiver (BroadcastReceiver)
    │  receives SMS_RECEIVED broadcast
    │  checks forwarding enabled + allowed sender filter
    │  detects SIM slot via SubscriptionManager
    └─► ForwarderService (ForegroundService)
            │  builds JSON payload
            └─► WebhookManager (OkHttp async call)

BootReceiver        → starts ForwarderService on device boot
ServiceRestartReceiver → restarts ForwarderService when it's destroyed
```

---

## Troubleshooting

| Problem | Solution |
|---|---|
| Service keeps stopping | Tap "Disable Battery Optimisation" in the app; also check manufacturer-specific battery settings (e.g., Xiaomi MIUI → Security → Battery → No restrictions) |
| SMS not forwarded | Make sure RECEIVE_SMS permission is granted; check the Recent Activity log in the app |
| Wrong SIM number in sent_to | Set the correct phone numbers in the SIM Settings card |
| Webhook returns error | Check the URL, ensure the server is reachable from the phone's network |
