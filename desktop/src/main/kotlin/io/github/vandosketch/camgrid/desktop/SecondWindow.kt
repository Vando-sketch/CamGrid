package io.github.vandosketch.camgrid.desktop

import io.github.vandosketch.camgrid.core.GridPage
import io.github.vandosketch.camgrid.desktop.config.DesktopDevicePreferences

/**
 * The optional second window (Settings, Second monitor): a second camera wall for another
 * monitor, with its own view. Whether it is open and which view it shows are settings of this
 * computer ([DesktopDevicePreferences]), not of the config, so a backup does not carry them.
 *
 * The view is kept by its id and the page within it, not by the page number, so adding or
 * moving another view does not change what the second monitor shows.
 */
internal object SecondWindow {
    const val ENABLED_KEY = "second_window"
    const val VIEW_KEY = "second_window_view"
    const val PAGE_IN_VIEW_KEY = "second_window_page_in_view"

    fun isEnabled(preferences: DesktopDevicePreferences): Boolean = preferences.getInt(ENABLED_KEY) == 1

    fun setEnabled(preferences: DesktopDevicePreferences, enabled: Boolean) =
        preferences.putInt(ENABLED_KEY, if (enabled) 1 else 0)

    /** The page the second window starts on: see [startPage]. */
    fun startPage(preferences: DesktopDevicePreferences, pages: List<GridPage>): Int =
        startPage(pages, preferences.getString(VIEW_KEY), preferences.getInt(PAGE_IN_VIEW_KEY))

    /**
     * The page of [pages] that shows view [viewId] at [pageInView] (its last page when it has
     * fewer now). With no view saved, or a view that is gone, the first page of the second view,
     * so a new second window does not just repeat the main window's first view.
     */
    fun startPage(pages: List<GridPage>, viewId: String?, pageInView: Int?): Int {
        val ofView = pages.indices.filter { pages[it].view.id == viewId }
        if (ofView.isNotEmpty()) return ofView[(pageInView ?: 0).coerceIn(0, ofView.lastIndex)]
        val firstView = pages.firstOrNull()?.view?.id
        return pages.indexOfFirst { it.view.id != firstView }.coerceAtLeast(0)
    }

    /** Remembers [page] as the one the second window shows next time. */
    fun save(preferences: DesktopDevicePreferences, page: GridPage) {
        preferences.putString(VIEW_KEY, page.view.id)
        preferences.putInt(PAGE_IN_VIEW_KEY, page.pageInView)
    }
}
