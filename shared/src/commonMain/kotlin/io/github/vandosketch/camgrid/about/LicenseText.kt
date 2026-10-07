package io.github.vandosketch.camgrid.about

/** Turns a plain-text license file into paragraphs for the license screen. */
object LicenseText {

    /**
     * Splits [text] at blank lines and joins each paragraph's lines with a space. License files
     * are hard-wrapped at about 72 columns and indented; kept as they are, they would wrap a
     * second time on a phone and leave ragged lines. Each paragraph becomes one focusable item,
     * so the D-pad scrolls through the text a paragraph at a time.
     */
    fun paragraphs(text: String): List<String> =
        text.replace("\r\n", "\n")
            .split(Regex("\n\\s*\n"))
            .map { paragraph -> paragraph.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ") }
            .filter { it.isNotEmpty() }
}
