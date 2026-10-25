package app.orchard.tv.system

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RecentAppsTest {
    private val app: Context = RuntimeEnvironment.getApplication()
    private val pm = shadowOf(app.packageManager)
    private val task = ComponentName("com.tcl.cyberui", "com.tcl.cyberui.taskmanager.view.TaskManagerActivity")
    private val receiver = ComponentName("com.tcl.cyberui", "com.tcl.global.history.receiver.GlobalHistoryReceiver")
    private val action = "com.tcl.action.OPEN_GLOBAL_HISTORY"

    private fun registerTask() {
        pm.addActivityIfNotPresent(task).apply {
            exported = false
            enabled = true
            applicationInfo.enabled = true
        }
        pm.addIntentFilterForActivity(task, IntentFilter("com.tcl.cyberui.taskmanager.mainpage").apply {
            addCategory(Intent.CATEGORY_DEFAULT)
        })
    }

    private fun registerReceiver() = pm.addReceiverIfNotPresent(receiver).also {
        it.exported = true
        it.enabled = true
        it.applicationInfo.enabled = true
        pm.addIntentFilterForReceiver(receiver, IntentFilter(action))
    }

    @Test fun privateTaskManagerOpensThroughExportedReceiver() {
        registerTask()
        registerReceiver()
        val caller = Caller()
        assertTrue(RecentApps.open(caller) { error("Firmware should handle Recents") })
        assertEquals(receiver, caller.sent!!.component)
        assertEquals(action, caller.sent!!.action)
        assertEquals("com.tcl.cyberui", caller.sent!!.`package`)
    }

    @Test fun absentFirmwareUsesAndroidAndPreservesFailureResult() {
        val caller = Caller()
        var calls = 0
        assertFalse(RecentApps.open(caller) { calls++; false })
        assertEquals(1, calls)
        assertNull(caller.sent)
    }

    @Test fun historyOnlyFirmwareUsesAndroidRecents() {
        registerReceiver()
        val caller = Caller()
        assertTrue(RecentApps.open(caller) { true })
        assertNull(caller.sent)
    }

    @Test fun inaccessibleReceiverUsesAndroid() {
        registerTask()
        val info = registerReceiver()
        val caller = Caller()
        info.exported = false
        assertTrue(RecentApps.open(caller) { true })
        assertNull(caller.sent)
        info.exported = true
        info.permission = "com.tcl.cyberui.PRIVATE_TASK_MANAGER"
        assertTrue(RecentApps.open(caller) { true })
        assertNull(caller.sent)
    }

    @Test fun disabledTaskManagerUsesAndroid() {
        registerTask()
        registerReceiver()
        app.packageManager.setComponentEnabledSetting(task, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0)
        val caller = Caller()
        assertTrue(RecentApps.open(caller) { true })
        assertNull(caller.sent)
    }

    @Test fun broadcastSecurityFailureUsesAndroidOnce() {
        registerTask()
        registerReceiver()
        var calls = 0
        assertTrue(RecentApps.open(Caller(SecurityException("restricted"))) { calls++; true })
        assertEquals(1, calls)
    }

    private inner class Caller(val failure: RuntimeException? = null) : ContextWrapper(app) {
        var sent: Intent? = null
        override fun sendBroadcast(intent: Intent) {
            failure?.let { throw it }
            sent = Intent(intent)
        }
    }
}
