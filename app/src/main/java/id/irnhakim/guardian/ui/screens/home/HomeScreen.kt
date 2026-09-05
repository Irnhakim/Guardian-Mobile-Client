package id.irnhakim.guardian.ui.screens.home

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import id.irnhakim.guardian.core.services.LocationForegroundService
import id.irnhakim.guardian.core.utils.PermissionUtils
import id.irnhakim.guardian.core.workers.AppSyncWorker
import id.irnhakim.guardian.core.workers.BatteryWorker

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasLocation by remember { mutableStateOf(PermissionUtils.hasLocationPermission(context)) }
    var hasUsageStats by remember { mutableStateOf(PermissionUtils.hasUsageStatsPermission(context)) }
    var hasNotification by remember { mutableStateOf(PermissionUtils.hasNotificationPermission(context)) }
    var hasNotificationAccess by remember { mutableStateOf(PermissionUtils.isNotificationListenerEnabled(context)) }
    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasDeviceAdmin by remember { mutableStateOf(PermissionUtils.isDeviceAdminActive(context)) }
    var hasAccessibility by remember { mutableStateOf(PermissionUtils.isAccessibilityServiceEnabled(context)) }

    // Helper to refresh all permissions and run workers if they were newly granted
    val checkAndSync = {
        val loc = PermissionUtils.hasLocationPermission(context)
        val usage = PermissionUtils.hasUsageStatsPermission(context)
        val notif = PermissionUtils.hasNotificationPermission(context)
        val notifAccess = PermissionUtils.isNotificationListenerEnabled(context)
        val overlay = Settings.canDrawOverlays(context)
        val admin = PermissionUtils.isDeviceAdminActive(context)
        val a11y = PermissionUtils.isAccessibilityServiceEnabled(context)

        hasLocation = loc
        hasUsageStats = usage
        hasNotification = notif
        hasNotificationAccess = notifAccess
        hasOverlay = overlay
        hasDeviceAdmin = admin
        hasAccessibility = a11y

        // If newly granted or registered, ensure foreground service and workers are scheduled/running
        if (loc) {
            LocationForegroundService.start(context)
        }
        LocationForegroundService.syncPermissionsNow()
        BatteryWorker.schedule(context)
        AppSyncWorker.schedule(context)

        // Queue immediate syncs
        val workManager = WorkManager.getInstance(context)
        workManager.enqueue(OneTimeWorkRequestBuilder<BatteryWorker>().build())
        workManager.enqueue(OneTimeWorkRequestBuilder<AppSyncWorker>().build())
    }

    // Observe lifecycle events to refresh permission status when returning from settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkAndSync()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Permission Launchers
    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        hasLocation = granted
        if (granted) {
            LocationForegroundService.start(context)
        }
        checkAndSync()
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotification = granted
        checkAndSync()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F1117), Color(0xFF161B27)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF5C7CFA), Color(0xFFA78BFA))),
                        RoundedCornerShape(20.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("🛡️", fontSize = 32.sp)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Guardian Active",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5C7CFA),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "This device is being monitored",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
            )
            Spacer(Modifier.height(32.dp))

            // Status indicators
            StatusRow(label = "Location Tracking", active = hasLocation)
            Spacer(Modifier.height(8.dp))
            StatusRow(label = "Battery Monitoring", active = true) // Battery check does not require special runtime permission
            Spacer(Modifier.height(8.dp))
            StatusRow(label = "App & Usage Monitoring", active = hasUsageStats)
            Spacer(Modifier.height(8.dp))
            StatusRow(label = "Notification Access", active = hasNotificationAccess)
            Spacer(Modifier.height(8.dp))
            StatusRow(label = "Display Over Other Apps", active = hasOverlay)
            Spacer(Modifier.height(8.dp))
            StatusRow(label = "Device Protection (Admin)", active = hasDeviceAdmin)
            Spacer(Modifier.height(8.dp))
            StatusRow(label = "Uninstall Prevention (A11y)", active = hasAccessibility)

            // Permissions Guidance Panel
            val needsLocationPermission = !hasLocation
            val needsUsagePermission = !hasUsageStats
            val needsNotificationPermission = !hasNotification && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            val needsNotificationAccess = !hasNotificationAccess
            val needsOverlay = !hasOverlay
            val needsDeviceAdmin = !hasDeviceAdmin
            val needsAccessibility = !hasAccessibility

            if (needsLocationPermission || needsUsagePermission || needsNotificationPermission || needsNotificationAccess || needsOverlay || needsDeviceAdmin || needsAccessibility) {
                Spacer(Modifier.height(32.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x1A5C7CFA),
                    border = ButtonDefaults.outlinedButtonBorder
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Required Permissions",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF1F5F9)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Please grant these permissions to ensure full synchronization with the parent dashboard.",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))

                        if (needsLocationPermission) {
                            Button(
                                onClick = {
                                    locationLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C7CFA))
                            ) {
                                Text("Grant Location Access", fontSize = 13.sp, color = Color.White)
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        if (needsUsagePermission) {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C7CFA))
                            ) {
                                Text("Grant Usage Stats Access", fontSize = 13.sp, color = Color.White)
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        if (needsNotificationPermission) {
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C7CFA))
                            ) {
                                Text("Grant Notification Access", fontSize = 13.sp, color = Color.White)
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        if (needsNotificationAccess) {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C7CFA))
                            ) {
                                Text("Grant Notification Listener Access", fontSize = 13.sp, color = Color.White)
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        if (needsOverlay) {
                            Button(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C7CFA))
                            ) {
                                Text("Grant Display Over Other Apps", fontSize = 13.sp, color = Color.White)
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        if (needsDeviceAdmin) {
                            Button(
                                onClick = {
                                    val comp = android.content.ComponentName(context, id.irnhakim.guardian.core.receivers.GuardianDeviceAdminReceiver::class.java)
                                    val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                        putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
                                        putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Guardian memerlukan proteksi Device Admin agar tidak dapat di-uninstall oleh anak.")
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C7CFA))
                            ) {
                                Text("Activate Device Protection (Admin)", fontSize = 13.sp, color = Color.White)
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        if (needsAccessibility) {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C7CFA))
                            ) {
                                Text("Enable Uninstall Prevention (A11y)", fontSize = 13.sp, color = Color.White)
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(32.dp))
                // Button to force manual sync if everything is set
                OutlinedButton(
                    onClick = { checkAndSync() },
                    modifier = Modifier.width(200.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Force Sync Now", color = Color(0xFF5C7CFA))
                }
            }
        }
    }
}

@Composable
fun StatusRow(label: String, active: Boolean) {
    Surface(
        modifier = Modifier.width(260.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF1C2333),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = Color(0xFFF1F5F9), fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (active) "Active" else "Missing Permission",
                    color = if (active) Color(0xFF10B981) else Color(0xFFF59E0B),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (active) Color(0xFF10B981) else Color(0xFFF59E0B),
                            RoundedCornerShape(50),
                        )
                )
            }
        }
    }
}

// Helper permission checking functions
private fun isNotificationListenerEnabled(context: Context): Boolean =
    PermissionUtils.isNotificationListenerEnabled(context)

private fun hasLocationPermission(context: Context): Boolean =
    PermissionUtils.hasLocationPermission(context)

private fun hasUsageStatsPermission(context: Context): Boolean =
    PermissionUtils.hasUsageStatsPermission(context)

private fun hasNotificationPermission(context: Context): Boolean =
    PermissionUtils.hasNotificationPermission(context)

private fun isDeviceAdminActive(context: Context): Boolean =
    PermissionUtils.isDeviceAdminActive(context)

private fun isAccessibilityServiceEnabled(context: Context): Boolean =
    PermissionUtils.isAccessibilityServiceEnabled(context)
