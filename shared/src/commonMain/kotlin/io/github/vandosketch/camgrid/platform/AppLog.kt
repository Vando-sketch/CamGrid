package io.github.vandosketch.camgrid.platform

import kotlin.concurrent.Volatile

/**
 * The app's log, routed to the platform's log by [sink] (Logcat on Android). Messages never
 * contain URLs, file contents or passwords: stream URLs may carry credentials.
 */
object AppLog {
    enum class Level { WARN, ERROR }

    fun interface Sink {
        fun log(level: Level, tag: String, message: String)
    }

    /** Where messages go; standard output until the platform sets its own. */
    @Volatile
    var sink: Sink = Sink { level, tag, message -> println("$level $tag: $message") }

    const val TAG = "CamGrid"

    fun w(message: String, tag: String = TAG) = sink.log(Level.WARN, tag, message)

    fun e(message: String, tag: String = TAG) = sink.log(Level.ERROR, tag, message)
}
