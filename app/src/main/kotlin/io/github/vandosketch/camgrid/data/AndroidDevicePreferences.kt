package io.github.vandosketch.camgrid.data

import android.content.Context
import androidx.core.content.edit
import io.github.vandosketch.camgrid.platform.DevicePreferences

/**
 * This device's own settings in SharedPreferences. Like everything else of the app they are
 * excluded from Android's cloud backup and device transfer (data_extraction_rules.xml), so they
 * stay on the TV they were made on.
 */
class AndroidDevicePreferences(context: Context) : DevicePreferences {

    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun getInt(key: String): Int? = try {
        if (prefs.contains(key)) prefs.getInt(key, 0) else null
    } catch (_: ClassCastException) {
        // Stored as another type by some older build; treat as not set.
        null
    }

    override fun putInt(key: String, value: Int) {
        prefs.edit { putInt(key, value) }
    }

    private companion object {
        const val FILE_NAME = "device"
    }
}
