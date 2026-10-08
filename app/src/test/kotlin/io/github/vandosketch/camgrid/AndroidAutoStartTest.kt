package io.github.vandosketch.camgrid

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import io.github.vandosketch.camgrid.platform.AutoStartBlocker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/**
 * Starting after boot: a preference is the setting, off until the user turns it on. The receiver's
 * component state is never touched (except to undo what older builds did), see [AndroidAutoStart].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidAutoStartTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val autoStart = AndroidAutoStart(app)
    private val receiver = ComponentName(app, BootReceiver::class.java)

    @Before
    fun allowOverlaysByDefault() {
        ShadowSettings.setCanDrawOverlays(true)
    }

    @Test
    fun isOffUntilTheUserTurnsItOn() {
        assertFalse(autoStart.isEnabled())
    }

    @Test
    fun theReceiverIsAlwaysEnabledInTheManifest() {
        // Toggling it would make the Fire TV launcher reload CamGrid and lose its tile.
        val info = app.packageManager.getReceiverInfo(receiver, PackageManager.MATCH_DISABLED_COMPONENTS)
        assertTrue(info.enabled)
    }

    @Test
    fun theReceiverListensForBootCompleted() {
        val receivers = app.packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(app.packageName),
            PackageManager.MATCH_DISABLED_COMPONENTS,
        )
        assertTrue(receivers.any { it.activityInfo.name == BootReceiver::class.java.name })
    }

    @Test
    fun turningItOnAndOffLeavesTheComponentAlone() {
        autoStart.setEnabled(true)
        assertTrue(autoStart.isEnabled())
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, componentState())

        autoStart.setEnabled(false)
        assertFalse(autoStart.isEnabled())
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, componentState())
    }

    @Test
    fun theSettingIsStoredInItsOwnPreferences() {
        autoStart.setEnabled(true)
        assertTrue(prefs().getBoolean("enabled", false))
        // A new instance (the app restarted) reads it back.
        assertTrue(AndroidAutoStart(app).isEnabled())

        autoStart.setEnabled(false)
        assertFalse(prefs().getBoolean("enabled", true))
        assertFalse(AndroidAutoStart(app).isEnabled())
    }

    @Test
    fun whenOffBootCompletedDoesNothing() {
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertNull(shadowOf(app).nextStartedActivity)
        assertTrue(alarms().scheduledAlarms.isEmpty())
    }

    @Test
    fun whenOnBootCompletedOpensTheGridInANewTask() {
        autoStart.setEnabled(true)
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))

        val started = shadowOf(app).nextStartedActivity
        assertEquals(ComponentName(app, MainActivity::class.java), started.component)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun whenOnBootCompletedOpensTheGridAgainAfterTheLauncher() {
        autoStart.setEnabled(true)
        val now = SystemClock.elapsedRealtime()
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))

        val scheduled = alarms().scheduledAlarms
        assertEquals(1, scheduled.size)
        val alarm = scheduled.single()
        assertEquals(AlarmManager.ELAPSED_REALTIME, alarm.type)
        assertEquals(now + 15_000L, alarm.triggerAtMs)
        val pending = shadowOf(alarm.operation)
        assertTrue(pending.isActivityIntent)
        assertTrue(pending.flags and PendingIntent.FLAG_IMMUTABLE != 0)
        assertEquals(ComponentName(app, MainActivity::class.java), pending.savedIntent.component)
        assertTrue(pending.savedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun otherBroadcastsAreIgnored() {
        autoStart.setEnabled(true)
        BootReceiver().onReceive(app, Intent(Intent.ACTION_SCREEN_ON))
        BootReceiver().onReceive(app, Intent())

        assertNull(shadowOf(app).nextStartedActivity)
        assertTrue(alarms().scheduledAlarms.isEmpty())
    }

    @Test
    fun anEnabledReceiverFromAnOlderBuildBecomesTheSetting() {
        setComponentState(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)

        // Even before the app opens once after the update, the boot start still works.
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(ComponentName(app, MainActivity::class.java), shadowOf(app).nextStartedActivity.component)

        assertTrue(AndroidAutoStart(app).isEnabled())
        assertTrue(prefs().getBoolean("enabled", false))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, componentState())
    }

    @Test
    fun aDisabledReceiverFromAnOlderBuildBecomesTheSetting() {
        prefs().edit().putBoolean("enabled", true).commit()
        setComponentState(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)

        assertFalse(AndroidAutoStart(app).isEnabled())
        assertFalse(prefs().getBoolean("enabled", true))
        // Otherwise the receiver would stay off for good, whatever the switch says.
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, componentState())
    }

    private fun prefs() = app.getSharedPreferences("autostart", Context.MODE_PRIVATE)

    private fun alarms() = shadowOf(app.getSystemService(AlarmManager::class.java))

    private fun componentState() = app.packageManager.getComponentEnabledSetting(receiver)

    private fun setComponentState(state: Int) =
        app.packageManager.setComponentEnabledSetting(receiver, state, PackageManager.DONT_KILL_APP)

    @Test
    @Config(sdk = [28])
    fun nothingBlocksItBeforeAndroid10() {
        ShadowSettings.setCanDrawOverlays(false)
        assertNull(autoStart.blocker())
    }

    @Test
    fun nothingBlocksItWhenOverlaysAreAllowed() {
        ShadowSettings.setCanDrawOverlays(true)
        assertNull(autoStart.blocker())
    }

    @Test
    fun withoutTheOverlayPermissionAndroid10BlocksIt() {
        ShadowSettings.setCanDrawOverlays(false)
        assertEquals(AutoStartBlocker.OVERLAY_PERMISSION, autoStart.blocker())
    }

    @Test
    fun withoutAnOverlaySettingsScreenNothingOpens() {
        assertFalse(autoStart.openBlockerSettings())
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun opensTheOverlaySettingsForCamGrid() {
        val settings = ComponentName("com.android.settings", "com.android.settings.OverlaySettings")
        shadowOf(app.packageManager).apply {
            addActivityIfNotPresent(settings)
            addIntentFilterForActivity(
                settings,
                IntentFilter(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    // As the real Settings app declares it; startActivity needs a default activity.
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addDataScheme("package")
                },
            )
        }

        assertTrue(autoStart.openBlockerSettings())

        val started = shadowOf(app).nextStartedActivity
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, started.action)
        assertEquals(Uri.parse("package:${app.packageName}"), started.data)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
