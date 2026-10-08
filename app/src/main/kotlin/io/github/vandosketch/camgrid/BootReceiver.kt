package io.github.vandosketch.camgrid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Opens the grid once the device has booted, for a wall display nobody wants to start by hand.
 *
 * It only runs when the user turned the option on: the manifest declares it disabled, and
 * [AndroidAutoStart] enables it. Its enabled state is the setting itself.
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
        context.startActivity(
            // NEW_TASK because a receiver has no task to start it in. If CamGrid is already
            // running (the user was faster), this brings its task to the front instead of
            // opening a second grid.
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
