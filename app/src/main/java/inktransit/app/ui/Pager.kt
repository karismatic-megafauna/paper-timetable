package inktransit.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

fun pageCount(n: Int, pageSize: Int): Int = if (n <= 0) 1 else (n + pageSize - 1) / pageSize

/**
 * How many rows of [rowDp] fit in [availableDp]. When they do not all fit, room
 * is kept for the Prev/Next bar. Never less than one.
 */
fun rowsThatFit(availableDp: Float, rowDp: Float, total: Int, barDp: Float = 66f): Int {
    val all = (availableDp / rowDp).toInt()
    if (total <= all) return maxOf(1, all)
    return maxOf(1, ((availableDp - barDp) / rowDp).toInt())
}

fun <T> pageOf(items: List<T>, page: Int, pageSize: Int): List<T> {
    val from = page * pageSize
    if (page < 0 || from >= items.size) return emptyList()
    return items.subList(from, minOf(from + pageSize, items.size))
}

/**
 * A list that moves a whole page at a time. E Ink cannot scroll smoothly, so
 * there is no scrolling anywhere in the app. [resetKey] returns to page one
 * when the content changes; [startPage] opens somewhere other than the top.
 */
@Composable
fun <T> PagedList(
    items: List<T>,
    pageSize: Int,
    modifier: Modifier = Modifier,
    resetKey: Any? = null,
    startPage: Int = 0,
    empty: @Composable () -> Unit = {},
    row: @Composable (T) -> Unit,
) {
    val pages = pageCount(items.size, pageSize)
    var page by remember(resetKey, startPage) { mutableIntStateOf(startPage.coerceIn(0, pages - 1)) }
    val shown = page.coerceIn(0, pages - 1)
    Column(modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth()) {
            if (items.isEmpty()) empty() else pageOf(items, shown, pageSize).forEach { row(it) }
        }
        if (pages > 1) {
            Rule()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                InkButton("Prev", Modifier.weight(1f), enabled = shown > 0) { page = shown - 1 }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { InkText("${shown + 1} of $pages", Ink.Small) }
                InkButton("Next", Modifier.weight(1f), enabled = shown < pages - 1) { page = shown + 1 }
            }
        }
    }
}
