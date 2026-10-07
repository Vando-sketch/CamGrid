package io.github.vandosketch.camgrid.ui

import androidx.compose.ui.input.key.KeyEvent

/** Whether this KeyDown is an automatic repeat of a held key, where the platform tells. */
internal expect val KeyEvent.isRepeat: Boolean
