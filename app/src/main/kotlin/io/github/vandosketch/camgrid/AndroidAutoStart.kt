package io.github.vandosketch.camgrid

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import androidx.core.net.toUri
import io.github.vandosketch.camgrid.platform.AutoStart
import io.github.vandosketch.camgrid.platform.AutoStartBlocker

/**
 * Starting after boot on Android: the option is one flag in its own small preferences file, and
 * [BootReceiver] (always enabled in the manifest) checks it at boot. It stays on the device, as
 * the app has backups turned off.
 *
 * Toggling the receiver's component instead, as 0.3.0 previews did, broadcasts PACKAGE_CHANGED,
 * and the Fire TV launcher reloads the package on it and loses CamGrid's tile banner. So the
 * component is never toggled; this only undoes once what those builds left behind.
 */
class AndroidAutoStart(private val context: Context) : AutoStart {

    private val prefs = prefs(context)

    init {
        migrateComponentState()
    }

    override fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    override fun setEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
    }

    /**
     * Carries an explicit component state from an older build over into the preference and
     * resets the component to the manifest's default. Needed for DISABLED in particular: left
     * alone it would keep the receiver off for good, whatever the switch says. Runs as soon as
     * the app opens (MainActivity creates this), and is a no-op from then on.
     */
    private fun migrateComponentState() {
        val receiver = receiver(context)
        val state = context.packageManager.getComponentEnabledSetting(receiver)
        if (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) return
        // commit, not apply: the preference must be written before the component state that
        // still holds the setting is gone.
        prefs.edit(commit = true) { putBoolean(KEY_ENABLED, state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) }
        // DONT_KILL_APP: this runs while the app starts, it must keep running.
        context.packageManager.setComponentEnabledSetting(
            receiver,
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            PackageManager.DONT_KILL_APP,
        )
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

    internal companion object {
        private const val PREFS_NAME = "autostart"
        private const val KEY_ENABLED = "enabled"

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private fun receiver(context: Context) = ComponentName(context, BootReceiver::class.java)

        /**
         * Whether [BootReceiver] should open the grid. Reads the preference without migrating,
         * so a boot never changes the component state. A component still explicitly ENABLED by
         * an older build counts as on: that build's switch was on, and the app has not been
         * opened since the update to carry it over.
         */
        fun isOn(context: Context): Boolean =
            prefs(context).getBoolean(KEY_ENABLED, false) ||
                context.packageManager.getComponentEnabledSetting(receiver(context)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }
}
