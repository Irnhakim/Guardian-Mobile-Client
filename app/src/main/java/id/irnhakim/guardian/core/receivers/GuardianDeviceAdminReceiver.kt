package id.irnhakim.guardian.core.receivers

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class GuardianDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("DeviceAdmin", "Device Admin Enabled")
        Toast.makeText(context, "Guardian Device Admin Aktif", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d("DeviceAdmin", "Device Admin Disabled")
        Toast.makeText(context, "Guardian Device Admin Dinonaktifkan", Toast.LENGTH_SHORT).show()
    }
}
