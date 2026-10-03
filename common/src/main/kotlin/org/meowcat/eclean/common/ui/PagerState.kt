package org.meowcat.eclean.common.ui

/**
 * Pure paging state shared by all loaders.
 *
 * @param pageSize number of items per page
 * @param itemCount provider for the total number of items (may change over time)
 * @param initialPage initial page index
 */
class PagerState(
    val pageSize: Int,
    private val itemCount: () -> Int,
    initialPage: Int = 0,
) {
    var page: Int = initialPage.coerceAtLeast(0)
        private set

    val hasPrev: Boolean get() = page > 0
    val hasNext: Boolean get() = (page + 1) * pageSize < itemCount()

    fun nextPage() {
        if (hasNext) page++
    }

    fun prevPage() {
        if (hasPrev) page--
    }

    fun clampPage() {
        if (page <= 0) return
        val maxPage = maxOf(0, (itemCount() - 1) / pageSize)
        if (page > maxPage) page = maxPage
    }

    /** Index of the first item on the current page. */
    fun firstIndex(): Int = page * pageSize

    /** Index of the last item on the current page (exclusive). */
    fun lastIndex(): Int = minOf(firstIndex() + pageSize, itemCount())
}