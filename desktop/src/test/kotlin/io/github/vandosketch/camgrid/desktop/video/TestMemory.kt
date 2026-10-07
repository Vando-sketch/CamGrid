package io.github.vandosketch.camgrid.desktop.video

import java.io.File

/** Memory of the test process as the OS sees it, native allocations included (Linux only). */
object TestMemory {
    fun residentBytes(): Long =
        File("/proc/self/status").readLines().first { it.startsWith("VmRSS:") }
            .split(Regex("\\s+"))[1].toLong() * 1024
}
