package io.github.vandosketch.camgrid.platform

/**
 * Starting CamGrid by itself when the device boots, for a wall display that should come up
 * showing the grid without anyone touching a remote. Only platforms that can do it provide
 * one; Settings hides the option where there is none.
 *
 * The setting belongs to the device, not to the config: it is not part of a backup.
 */
interface AutoStart {
    /** Whether CamGrid starts after the device boots. */
    fun isEnabled(): Boolean

    fun setEnabled(enabled: Boolean)

    /**
     * Why an enabled start may not happen on this device, or null when nothing is known to
     * block it. Asked again whenever Settings shows the option.
     */
    fun blocker(): AutoStartBlocker?

    /**
     * Opens the system screen where the user can lift [blocker]. False when the device has
     * no such screen (Fire TV hides it), so Settings shows the manual way instead.
     */
    fun openBlockerSettings(): Boolean
}

enum class AutoStartBlocker {
    /**
     * Android 10 and newer let an app open its screen from the background only with the
     * "Display over other apps" permission, which the user has to grant.
     */
    OVERLAY_PERMISSION,
}
