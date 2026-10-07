package io.github.vandosketch.camgrid.about

import kotlin.test.Test
import kotlin.test.assertEquals

class LicenseTextTest {

    @Test
    fun hardWrappedLinesBecomeOneParagraph() {
        val text = """
            |                    GNU LESSER GENERAL PUBLIC LICENSE
            |                       Version 3, 29 June 2007
            |
            |  Copyright (C) 2007 Free Software Foundation, Inc.
            |  Everyone is permitted to copy and distribute
            |verbatim copies.
            |
            |
            |  0. Additional Definitions.
        """.trimMargin()

        assertEquals(
            listOf(
                "GNU LESSER GENERAL PUBLIC LICENSE Version 3, 29 June 2007",
                "Copyright (C) 2007 Free Software Foundation, Inc. Everyone is permitted to copy and distribute verbatim copies.",
                "0. Additional Definitions.",
            ),
            LicenseText.paragraphs(text),
        )
    }

    @Test
    fun windowsLineEndsAndBlankInputGiveNoEmptyParagraphs() {
        assertEquals(listOf("a b", "c"), LicenseText.paragraphs("a\r\nb\r\n\r\n  \r\nc\r\n"))
        assertEquals(emptyList(), LicenseText.paragraphs(" \n\n"))
    }

    @Test
    fun runsOfSpacesInsideALineAreKept() {
        // Indentation goes, the text itself stays as written.
        assertEquals(listOf("THE SOFTWARE IS PROVIDED  \"AS IS\""), LicenseText.paragraphs("   THE SOFTWARE IS PROVIDED  \"AS IS\"   "))
    }
}
