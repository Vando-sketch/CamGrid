package io.github.vandosketch.camgrid.platform

/** Device preferences kept in memory, for tests; [values] shows what was written. */
class MemoryDevicePreferences(val values: MutableMap<String, Int> = mutableMapOf()) : DevicePreferences {
    override fun getInt(key: String): Int? = values[key]

    override fun putInt(key: String, value: Int) {
        values[key] = value
    }
}
