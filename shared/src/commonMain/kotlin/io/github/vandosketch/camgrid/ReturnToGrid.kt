package io.github.vandosketch.camgrid

/**
 * After how long without input a camera opened fullscreen goes back to the grid, for a wall
 * display nobody closes by hand. Kept per device ([io.github.vandosketch.camgrid.platform.DevicePreferences]),
 * as [minutes]; off by default.
 */
enum class ReturnToGrid(val minutes: Int) {
    OFF(0),
    MINUTES_1(1),
    MINUTES_2(2),
    MINUTES_5(5),
    MINUTES_10(10),
    ;

    /** How long fullscreen waits for input, or null when it never goes back by itself. */
    val timeoutMillis: Long? get() = if (this == OFF) null else minutes * 60_000L

    companion object {
        /** Its key in [io.github.vandosketch.camgrid.platform.DevicePreferences]. */
        const val PREFERENCE_KEY = "return_to_grid_minutes"

        /** The choice stored as [minutes]; nothing stored or a value no choice has is [OFF]. */
        fun fromMinutes(minutes: Int?): ReturnToGrid = entries.firstOrNull { it.minutes == minutes } ?: OFF
    }
}
