package io.github.vandosketch.camgrid.core

/**
 * Pure, immutable edits on [CamGridConfig]. Every function returns a new config and leaves
 * the receiver untouched.
 */
object ConfigEditor {

    /** Appends [camera] at the end. Throws [IllegalArgumentException] if its id is already used. */
    fun addCamera(config: CamGridConfig, camera: Camera): CamGridConfig {
        require(config.cameras.none { it.id == camera.id }) { "Camera id already used: ${camera.id}" }
        return config.copy(cameras = config.cameras + camera)
    }

    /** Replaces the camera with the same id, keeping its position. Unknown id: throws [IllegalArgumentException]. */
    fun updateCamera(config: CamGridConfig, camera: Camera): CamGridConfig {
        val index = config.cameras.indexOfFirst { it.id == camera.id }
        require(index >= 0) { "Unknown camera id: ${camera.id}" }
        return config.copy(cameras = config.cameras.toMutableList().apply { set(index, camera) })
    }

    /** Removes the camera with [id]. Unknown id: returns the config unchanged. */
    fun removeCamera(config: CamGridConfig, id: String): CamGridConfig =
        config.copy(cameras = config.cameras.filterNot { it.id == id })

    /**
     * Moves the camera with [id] to [toIndex] in the camera order. [toIndex] is clamped to the
     * valid range. Unknown id: returns the config unchanged.
     */
    fun moveCamera(config: CamGridConfig, id: String, toIndex: Int): CamGridConfig {
        val index = config.cameras.indexOfFirst { it.id == id }
        if (index < 0) return config
        val cameras = config.cameras.toMutableList()
        val camera = cameras.removeAt(index)
        cameras.add(toIndex.coerceIn(0, cameras.size), camera)
        return config.copy(cameras = cameras)
    }

    /** Sets the grid size. Values outside the allowed range throw [IllegalArgumentException]. */
    fun setLayout(config: CamGridConfig, columns: Int, rows: Int): CamGridConfig =
        config.copy(layout = GridLayout(columns, rows))

    /**
     * Adds every camera in [cameras] whose id is not already present, at the end, in order.
     * Existing cameras are left as they are.
     */
    fun importCameras(config: CamGridConfig, cameras: List<Camera>): CamGridConfig {
        val usedIds = config.cameras.mapTo(HashSet()) { it.id }
        return config.copy(cameras = config.cameras + cameras.filter { usedIds.add(it.id) })
    }
}
