package io.github.vandosketch.camgrid

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import io.github.vandosketch.camgrid.platform.AutoStart
import io.github.vandosketch.camgrid.platform.AutoStartBlocker

/**
 * Starting after boot on Android: the option is [BootReceiver]'s component enabled state, so no
 * extra storage. It stays on the device (component states are not part of a backup), and while
 * it is off the app is not even woken at boot.
 */
class AndroidAutoStart(private val context: Context) : AutoStart {

    private val receiver = ComponentName(context, BootReceiver::class.java)

    /** Only an explicit "enabled" counts: the manifest's default is off. */
    override fun isEnabled(): Boolean =
        context.packageManager.getComponentEnabledSetting(receiver) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    override fun setEnabled(enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        // DONT_KILL_APP: the user flips this in Settings, the app must keep running.
        context.packageManager.setComponentEnabledSetting(receiver, state, PackageManager.DONT_KILL_APP)
    }

    /**
     * Android 10 stopped apps from opening an activity from the background, and a boot receiver
     * is the background. Holding "Display over other apps" (SYSTEM_ALERT_WINDOW) is one of the
     * exemptions, and the only one a user can grant. Fire OS 7 and older (Android 9) have no
     * such limit.
     */
    override fun blocker(): AutoStartBlocker? = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> null
        Settings.canDrawOverlays(context) -> null
        else -> AutoStartBlocker.OVERLAY_PERMISSION
    }

    /**
     * The "Display over other apps" screen for CamGrid. Fire TV usually has none: then nothing
     * resolves (or the start fails anyway) and Settings explains the manual way.
     */
    override fun openBlockerSettings(): Boolean {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
            // Started from the application context, which has no task of its own.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) return false
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
