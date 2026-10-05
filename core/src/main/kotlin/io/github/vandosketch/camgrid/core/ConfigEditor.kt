package io.github.vandosketch.camgrid.core

/**
 * Pure, immutable edits on [CamGridConfig]. Every function returns a new config and leaves
 * the receiver untouched.
 */
object ConfigEditor {

    /** Appends [camera] at the end. Throws [IllegalArgumentException] if its id is already used. */
    fun addCamera(config: CamGridConfig, camera: Camera): CamGridConfig = TODO()

    /** Replaces the camera with the same id, keeping its position. Unknown id: throws [IllegalArgumentException]. */
    fun updateCamera(config: CamGridConfig, camera: Camera): CamGridConfig = TODO()

    /** Removes the camera with [id]. Unknown id: returns the config unchanged. */
    fun removeCamera(config: CamGridConfig, id: String): CamGridConfig = TODO()

    /**
     * Moves the camera with [id] to [toIndex] in the camera order. [toIndex] is clamped to the
     * valid range. Unknown id: returns the config unchanged.
     */
    fun moveCamera(config: CamGridConfig, id: String, toIndex: Int): CamGridConfig = TODO()

    /** Sets the grid size. Values outside the allowed range throw [IllegalArgumentException]. */
    fun setLayout(config: CamGridConfig, columns: Int, rows: Int): CamGridConfig = TODO()

    /**
     * Adds every camera in [cameras] whose id is not already present, at the end, in order.
     * Existing cameras are left as they are.
     */
    fun importCameras(config: CamGridConfig, cameras: List<Camera>): CamGridConfig = TODO()
}
