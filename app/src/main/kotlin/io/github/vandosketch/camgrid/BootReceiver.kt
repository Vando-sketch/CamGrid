package io.github.vandosketch.camgrid

import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

/**
 * Opens the grid once the device has booted, for a wall display nobody wants to start by hand.
 *
 * The manifest keeps it enabled at all times, and it returns at once unless the user turned the
 * option on ([AndroidAutoStart.isOn]). Switching the component on and off instead blanked
 * CamGrid's tile in the Fire TV launcher (see [AndroidAutoStart]).
 *
 * From Android 10 on, starting an activity from here needs the "Display over other apps"
 * permission (see [AndroidAutoStart.blocker]); without it the system drops the start silently.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // The receiver is exported (the system sends the boot broadcast from outside the app),
        // so anything else that reaches it is ignored. BOOT_COMPLETED itself is protected:
        // only the system can send it.
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!AndroidAutoStart.isOn(context)) return
        context.startActivity(gridIntent(context))
        startAgainLater(context)
    }

    /**
     * On Fire TV the Amazon home screen can come up after BOOT_COMPLETED and land in front of
     * the grid started above, so one more start follows a little later. If the first one worked,
     * it only brings the running grid to the front again. An inexact alarm is enough here and
     * needs no exact-alarm permission.
     */
    private fun startAgainLater(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getActivity(
            context,
            0,
            gridIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            creatorMayStartFromBackground(),
        )
        alarms.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + RESTART_DELAY_MS, pending)
    }

    /**
     * When the alarm fires, CamGrid is in the background. Since Android 15 (for apps targeting
     * it), a PendingIntent no longer lends its creator's exemption (the overlay permission) to
     * that start unless the creator opts in here.
     */
    private fun creatorMayStartFromBackground() = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA -> ActivityOptions.makeBasic()
            .setPendingIntentCreatorBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS,
            ).toBundle()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> @Suppress("DEPRECATION")
        ActivityOptions.makeBasic()
            .setPendingIntentCreatorBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
            ).toBundle()
        else -> null
    }

    // NEW_TASK because a receiver has no task to start it in. If CamGrid is already running
    // (the user was faster, or the first start worked), this brings its task to the front
    // instead of opening a second grid.
    private fun gridIntent(context: Context) =
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private companion object {
        /**
         * A guess, not a measured value: long enough for the Fire TV home screen to have come
         * up after boot, short enough that nobody waits long for the grid.
         */
        const val RESTART_DELAY_MS = 15_000L
    }
}
