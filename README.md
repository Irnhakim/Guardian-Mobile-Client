# Guardian Mobile Client

The Android background tracking application for the **Guardian Parental Control System**. Built natively using Kotlin and Jetpack Compose, this app runs silently on the child's device, capturing activities and syncing them to the NestJS parent server.

GitHub Repository: [https://github.com/Irnhakim/Guardian-Mobile-Client](https://github.com/Irnhakim/Guardian-Mobile-Client)

---

## Core Features

* **Real-time Notification Interception (`NotificationListenerService`)**: Intercepts notifications posted by other apps (e.g. WhatsApp, SMS, Telegram), reading titles, text contents, and app labels, and pushing them instantly to the backend.
* **Location Tracking**: Requests location updates from Google Play Services' Fused Location Provider Client, utilizing balanced-power configurations for accuracy.
* **App Synchronization**: Compiles and syncs a list of all installed packages, versions, and system statuses.
* **App Usage Metrics**: Queries Android's `UsageStatsManager` for foreground app usage runtimes to monitor screen time metrics.
* **Battery Monitoring**: Reads charge state, battery percentage, voltage, and temperature.
* **Remote Trigger Gateway**: Connects via WebSockets using Socket.io to receive instant `force_sync` signals sent from the parent dashboard.
* **OkHttp Authentication Interceptor**: Intercepts background API requests to automatically perform synchronous JWT token refresh rotation when a 401 Unauthorized status is hit.

---

## Battery & Network Optimization Strategies

To ensure minimal battery usage and keep the child's device running efficiently:
1. **Periodic Background Synchronization**: Periodic scans (Battery status, App List sync, usage statistics) are scheduled using Android `WorkManager` on a **30-minute interval**.
2. **Work Constraints**: Background workers only run when the device has an active internet connection (`NetworkType.CONNECTED`).
3. **Isolated Notification Pushes**: Incoming notifications are uploaded using a lightweight, dedicated API route. Capturing a notification does **not** trigger full sync tasks (skips locations/apps scans), preserving bandwidth and battery.
4. **Power-Balanced Location Requests**: Location scans run at a **30-minute interval** and are configured with `Priority.PRIORITY_BALANCED_POWER_ACCURACY` to avoid triggering heavy GPS hardware unless necessary.

---

## Required Permissions

To function correctly, the app requires the parent to manually grant the following permissions:
* **Notification Listener Access** (`BIND_NOTIFICATION_LISTENER_SERVICE`): Required to read incoming notifications.
* **Location Access (Always Allow)** (`ACCESS_FINE_LOCATION` & `ACCESS_BACKGROUND_LOCATION`): Required to query device location in the background.
* **Usage Stats Access** (`PACKAGE_USAGE_STATS`): Required to query screen time and app runtimes.

---

## Tech Stack

* **Language**: Kotlin
* **UI Framework**: Jetpack Compose
* **Dependency Injection**: Dagger Hilt
* **Background Tasks**: Android WorkManager
* **Networking**: Retrofit 2, OkHttp 4, Socket.io Client Java
* **Location Service**: Google Play Services (Fused Location)

---

## Build & Installation Guide

### Prerequisites
* Android Studio (Koala or higher)
* Android Device/Emulator running Android 9.0 (API 28) or higher

### Step 1: Clone the Project
```bash
git clone https://github.com/Irnhakim/Guardian-Mobile-Client.git
```

### Step 2: Configure Server IP address
Before compiling, configure the mobile app to point to your NestJS server local IP address.
Open `app/src/main/java/id/irnhakim/guardian/core/di/NetworkModule.kt` and replace the base URL:
```kotlin
private const val BASE_URL = "http://192.168.1.XX:3001/v1/" // Replace with your computer's local IP address
```

### Step 3: Run the Application
1. Connect your Android device via USB/ADB.
2. In Android Studio, select your device and click **Run** (`Shift + F10`) to compile and install the debug APK.
3. Complete the Parent registration on the app, or login if you have registered.
4. **Grant Permissions**:
   - Tap **Grant Notification Listener Access** and toggle **ON** for Guardian.
   - Tap **Grant Location Access** (select "Allow all the time").
   - Tap **Grant Usage Stats Access** and select Guardian in the list.
5. The application is now fully configured and running background tasks every **30 minutes**.
