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

    /**
     * Removes the camera with [id]. Tiles that showed it become auto tiles. Unknown id: returns
     * the config unchanged.
     */
    fun removeCamera(config: CamGridConfig, id: String): CamGridConfig {
        if (config.cameras.none { it.id == id }) return config
        return config.copy(
            cameras = config.cameras.filterNot { it.id == id },
            views = config.views.map { view ->
                view.copy(tiles = view.tiles.map { if (it.camera == id) it.copy(camera = null) else it })
            },
        )
    }

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

    /** Appends [view]. Throws [IllegalArgumentException] if its id is already used. */
    fun addView(config: CamGridConfig, view: CamView): CamGridConfig {
        require(config.views.none { it.id == view.id }) { "View id already used: ${view.id}" }
        return config.copy(views = config.views + view)
    }

    /** Replaces the view with the same id, keeping its position. Unknown id: throws [IllegalArgumentException]. */
    fun updateView(config: CamGridConfig, view: CamView): CamGridConfig {
        val index = config.views.indexOfFirst { it.id == view.id }
        require(index >= 0) { "Unknown view id: ${view.id}" }
        return config.copy(views = config.views.toMutableList().apply { set(index, view) })
    }

    /** Removes the view with [id]. The last remaining view is kept, as is an unknown id. */
    fun removeView(config: CamGridConfig, id: String): CamGridConfig {
        if (config.views.size <= 1) return config
        return config.copy(views = config.views.filterNot { it.id == id })
    }

    /** Moves the view with [id] to [toIndex] (clamped). Unknown id: unchanged. */
    fun moveView(config: CamGridConfig, id: String, toIndex: Int): CamGridConfig {
        val index = config.views.indexOfFirst { it.id == id }
        if (index < 0) return config
        val views = config.views.toMutableList()
        val view = views.removeAt(index)
        views.add(toIndex.coerceIn(0, views.size), view)
        return config.copy(views = views)
    }

    /** A view id not used yet: "view-2", "view-3", … */
    fun newViewId(config: CamGridConfig): String {
        val used = config.views.mapTo(HashSet()) { it.id }
        return generateSequence(2) { it + 1 }.map { "view-$it" }.first { it !in used }
    }

    /**
     * Adds every camera in [cameras] whose id is not already present, at the end, in order.
     * Existing cameras are left as they are.
     */
    fun importCameras(config: CamGridConfig, cameras: List<Camera>): CamGridConfig {
        val usedIds = config.cameras.mapTo(HashSet()) { it.id }
        return config.copy(cameras = config.cameras + cameras.filter { usedIds.add(it.id) })
    }
}
