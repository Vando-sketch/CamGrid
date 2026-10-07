package io.github.vandosketch.camgrid.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * True where the user has a real keyboard and mouse (the desktop app), false on TVs, phones and
 * tablets. Picks keyboard-oriented hints over remote ones and enables mouse-only extras.
 */
val LocalHasKeyboardAndMouse = staticCompositionLocalOf { false }
