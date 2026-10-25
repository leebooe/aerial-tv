package app.orchard.tv.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat

/** Opens the firmware's task manager, with Android's accessibility action as a fallback. */
internal object RecentApps {
    fun open(context: Context, fallback: () -> Boolean): Boolean {
        // TCL MT5879 / Android 11 has mRecentsComponent=null and a private task manager activity.
        // Its held-Home path broadcasts OPEN_GLOBAL_HISTORY; CyberUI's exported receiver opens
        // TaskManagerActivity internally (verified on the 75Q9K). Check both capabilities:
        // older CyberUI versions may use the same broadcast for viewing history instead.
        val task = Intent("com.tcl.cyberui.taskmanager.mainpage").setPackage("com.tcl.cyberui")
        val request = Intent("com.tcl.action.OPEN_GLOBAL_HISTORY").setPackage("com.tcl.cyberui")
        try {
            val pm = context.packageManager
            val activity = task.resolveActivityInfo(pm, PackageManager.MATCH_DEFAULT_ONLY)
            val receiver = pm.queryBroadcastReceivers(request, 0).firstOrNull { result ->
                val info = result.activityInfo
                info.exported && info.enabled && info.applicationInfo.enabled &&
                    (info.permission == null || ContextCompat.checkSelfPermission(context, info.permission) == PackageManager.PERMISSION_GRANTED)
            }?.activityInfo
            if (activity != null && activity.enabled && activity.applicationInfo.enabled && receiver != null) {
                request.component = ComponentName(receiver.packageName, receiver.name)
                context.sendBroadcast(request)
                Log.i("AerialGuard", "Requested firmware task manager: ${request.component}")
                return true
            }
        } catch (e: RuntimeException) {
            // Firmware updates may remove the receiver or restrict it between lookup and delivery.
            Log.w("AerialGuard", "Firmware task manager unavailable; trying Android Recents", e)
        }
        return fallback().also { Log.i("AerialGuard", "Android Recents requested: $it") }
    }
}
