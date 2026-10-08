package io.github.vandosketch.camgrid.ios

import io.github.vandosketch.camgrid.platform.DevicePreferences
import platform.Foundation.NSNumber
import platform.Foundation.NSUserDefaults

/** This device's own settings in the app's standard NSUserDefaults. */
class IosDevicePreferences(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : DevicePreferences {

    override fun getInt(key: String): Int? = (defaults.objectForKey(key) as? NSNumber)?.intValue

    override fun putInt(key: String, value: Int) {
        defaults.setInteger(value.toLong(), forKey = key)
    }
}
