package io.github.vandosketch.camgrid

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
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

/** Starting after boot: the receiver's enabled state is the setting, off until the user turns it on. */
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
        // The manifest's default, not only "nobody changed it yet".
        val info = app.packageManager.getReceiverInfo(receiver, PackageManager.MATCH_DISABLED_COMPONENTS)
        assertFalse(info.enabled)
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
    fun turningItOnAndOffSwitchesTheReceiver() {
        autoStart.setEnabled(true)
        assertTrue(autoStart.isEnabled())
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            app.packageManager.getComponentEnabledSetting(receiver),
        )

        autoStart.setEnabled(false)
        assertFalse(autoStart.isEnabled())
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            app.packageManager.getComponentEnabledSetting(receiver),
        )
    }

    @Test
    fun bootCompletedOpensTheGridInANewTask() {
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))

        val started = shadowOf(app).nextStartedActivity
        assertEquals(ComponentName(app, MainActivity::class.java), started.component)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun otherBroadcastsAreIgnored() {
        BootReceiver().onReceive(app, Intent(Intent.ACTION_SCREEN_ON))
        BootReceiver().onReceive(app, Intent())

        assertNull(shadowOf(app).nextStartedActivity)
    }

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
