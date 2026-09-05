# Guardian Mobile Client

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="100" alt="Guardian Logo" />
</p>

<p align="center">
  <b>Android Native Background Client for the Guardian Parental Control System</b><br />
  Runs stealthily and resiliently in the background, providing real-time device monitoring, instant GPS tracking, notification interception, app usage telemetry, and anti-tamper protections.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%209.0%2B%20(API%2028%2B)-3DDC84?logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white" alt="Compose" />
  <img src="https://img.shields.io/badge/DI-Dagger%20Hilt-orange" alt="Hilt" />
  <img src="https://img.shields.io/badge/Protocol-Socket.IO%20%7C%20REST-010101?logo=socketdotio&logoColor=white" alt="Socket.IO" />
</p>

---

> [!IMPORTANT]
> **Interdependency Requirement**: This client requires a running instance of [Guardian Admin Server](https://github.com/Irnhakim/Guardian-Admin-Server) to receive telemetry data, relay real-time push commands, and authenticate requests.

---

## 📱 About The Project

**Guardian Mobile Client** is the device-side component of the Guardian ecosystem. Designed for concerned parents, it operates silently on the child's device to safeguard their digital activities.

Unlike conventional tracker apps that are easily killed by OEM battery savers (Xiaomi HyperOS, Samsung OneUI, Oppo ColorOS), Guardian utilizes a **multi-layered watchdog architecture** combining `ForegroundService`, `AccessibilityService`, and `NotificationListenerService` to guarantee persistent operation and immediate responsiveness to remote commands.

---

## ✨ Key Features

### 1. 📍 Real-Time & High-Accuracy Location Tracking
* **Fresh GPS Acquisition**: Executes `getCurrentLocation()` with `PRIORITY_HIGH_ACCURACY` upon remote request, guaranteeing current coordinates instead of stale cached data.
* **Smart Movement Filter**: Implements `setMinUpdateDistanceMeters(50f)` — avoids waking up the cellular/GPS radios when the device is completely stationary.
* **Emergency Fallback**: Gracefully falls back to `lastLocation` if fresh satellite fixes are momentarily obscured.

### 2. 🔔 Real-Time Notification Interception & Auto-Clean
* **Inbox Logging**: Intercepts notifications from WhatsApp, Telegram, SMS, Gmail, and other apps via `NotificationListenerService` and pushes them straight to the server.
* **System Overlay Cleanup**: Automatically detects and dismisses intrusive Android system alert banners like *"Guardian is displaying over other apps"*.

### 3. ⏱️ Screen Time & Application Telemetry
* **App Inventory**: Gathers full package lists, app names, icons, and versions.
* **Daily Screen Time**: Tracks precise usage intervals via `UsageStatsManager`.
* **Side-Loaded APK Gatekeeper**: Detects when new APK packages are installed outside Google Play Store (`ACTION_PACKAGE_ADDED`) and queries the parent server for real-time approval.

### 4. 🛡️ Event-Driven App Blocking & Anti-Tamper
* **0% Idle CPU App Blocking**: Replaced high-drain polling with event-driven window state transitions via `AccessibilityService` (`TYPE_WINDOW_STATE_CHANGED`).
* **Anti-Uninstall Shield**: Monitors system settings, Package Installer, and dialog windows; intercepts attempts to clear data, uninstall Guardian, or revoke Device Admin by redirecting back to the Home screen.
* **Stealth Mode**: Ability to hide or show launcher app icons on remote command.
* **Lock Screen & Remote Popup**: Displays full-screen lockouts with parental password unlock or simple alert dialogs.

### 5. 🔄 Multi-Target Instant Force Sync
* Supports granular WebSocket ping commands (`all`, `location`, `battery`, `apps`, `usage`, `permissions`) via Socket.IO.
* Bypasses background scheduler queues and executes immediate I/O coroutines to update the parent dashboard in under 2 seconds.

### 6. 🔋 Battery & Memory Optimization
* **Exponential Backoff**: Socket.IO client reconnects with dynamic jitter (`reconnectionDelayMax = 30s`), saving battery when offline or sleeping.
* **DataStore Singleton**: Shared `appDataStore` preventing process deadlocks and duplicate instance crashes.

---

## 🛠️ Architecture & Tech Stack

```
Guardian-Mobile-Client
├── app/src/main/java/id/irnhakim/guardian/
│   ├── core/
│   │   ├── receivers/      # BootReceiver, DeviceAdmin, AppInstallReceiver
│   │   ├── services/       # LocationForegroundService, NotificationListener, Accessibility
│   │   ├── utils/          # Permission utilities, helpers
│   │   └── workers/        # Periodic sync background WorkManager tasks
│   ├── data/
│   │   ├── local/          # DataStore Preferences (GuardianPreferences)
│   │   └── remote/         # Retrofit API interface, DTOs
│   ├── di/                 # Dagger Hilt Application Module
│   ├── ui/                 # Jetpack Compose UI (Setup, MainActivity, Lock Overlay)
│   └── GuardianApp.kt      # Application entrypoint with Hilt & singleton DataStore
```

* **Language**: Kotlin 2.0+
* **UI**: Jetpack Compose with Material 3
* **Dependency Injection**: Dagger Hilt 2.51+
* **Networking**: Retrofit 2, OkHttp 4, Socket.IO Client Java (v2.1.0)
* **Storage**: Jetpack Preferences DataStore
* **Background Processing**: AndroidX WorkManager, Coroutines & Flow
* **Hardware API**: Google Play Services Location (Fused Location Provider)

---

## 📋 System Requirements & Permissions

### Requirements
* Android 9.0 (API level 28) or higher.
* Google Play Services installed.

### Crucial Permissions Granted During Setup
1. **Location (Allow all the time)**: Required for continuous background GPS tracking.
2. **Notification Access**: Required for `NotificationListenerService` to capture messages.
3. **Accessibility Service**: Required for anti-uninstall protection and zero-battery app blocking.
4. **Usage Access**: Required to calculate daily screen time and monitor app launches.
5. **Display Over Other Apps**: Required for the remote lock screen and app block overlay.
6. **Device Administrator**: Prevents simple uninstallation through settings.

---

## 🚀 Setup & Installation Guide

### Step 1: Clone Repository
```bash
git clone https://github.com/Irnhakim/Guardian-Mobile-Client.git
cd Guardian-Mobile-Client
```

### Step 2: Configure Server URL
Open `app/src/main/java/id/irnhakim/guardian/di/AppModule.kt` (or configure via the app onboarding UI):
```kotlin
// Default fallback URL if not configured via QR / manual input
BuildConfig.API_BASE_URL // e.g. "http://192.168.1.50:3001/api/v1"
```

### Step 3: Build Debug APK
You can compile via Gradle CLI or Android Studio:

```bash
# Using Gradle Wrapper
./gradlew assembleDebug
```
The output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Step 4: Install to Child Device
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Step 5: OEM Aggressive Killer Configuration (HyperOS, MIUI, ColorOS)
For devices with aggressive process killers (e.g. Xiaomi / Redmi / Poco):
1. **Autostart**: `Settings` → `Apps` → `Guardian` → enable **Autostart**.
2. **Battery Saver**: Set `Battery Saver` to **No restrictions**.
3. **Recent Apps**: Open recent apps, long-press Guardian, and tap the **Padlock icon** to lock it in memory.
4. Enable **Accessibility Service** and **Notification Access** for Guardian.

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
