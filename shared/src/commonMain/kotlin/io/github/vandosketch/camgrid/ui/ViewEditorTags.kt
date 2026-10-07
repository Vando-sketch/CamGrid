package io.github.vandosketch.camgrid.ui

/** Test tags of the view editor, for the UI tests. */
internal const val VE_TAG_PREVIEW = "ve_preview"
internal const val VE_TAG_TILE_DIALOG = "ve_tile_dialog"
internal const val VE_TAG_CHANGE_CAMERA = "ve_change_camera"

/** Test tag of the preview rectangle of tile [index]. */
internal fun veTileTag(index: Int): String = "ve_tile_$index"
