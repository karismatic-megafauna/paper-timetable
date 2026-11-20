package inktransit.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

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

data class PageLayout(val size: Int, val slotDp: Float)

/**
 * How many rows go on a page and how tall each is drawn. Rows share all the
 * room there is, so a full page ends flush against the Prev/Next bar: a gap
 * under the rows only ever means the list has ended.
 */
fun pageLayout(availableDp: Float, rowDp: Float, total: Int, barDp: Float = 66f): PageLayout {
    val size = rowsThatFit(availableDp, rowDp, total, barDp)
    val room = if (total > size) availableDp - barDp else availableDp
    return PageLayout(size, maxOf(room, 0f) / size)
}

fun <T> pageOf(items: List<T>, page: Int, pageSize: Int): List<T> {
    val from = page * pageSize
    if (page < 0 || from >= items.size) return emptyList()
    return items.subList(from, minOf(from + pageSize, items.size))
}

/**
 * A list that moves a whole page at a time. E Ink cannot scroll smoothly, so
 * there is no scrolling anywhere in the app. The page size follows the room
 * on screen: [rowDp] is the least height a row needs, rule included.
 * [resetKey] returns to page one when the content changes; [startIndex] opens
 * on the page holding that item. Each row is handed its whole slot to fill.
 */
@Composable
fun <T> PagedList(
    items: List<T>,
    rowDp: Float,
    modifier: Modifier = Modifier,
    resetKey: Any? = null,
    startIndex: Int = 0,
    divided: Boolean = true,
    empty: @Composable () -> Unit = {},
    row: @Composable (T) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
    val layout = pageLayout(maxHeight.value, rowDp * LocalDensity.current.fontScale, items.size)
    val pageSize = layout.size
    val pages = pageCount(items.size, pageSize)
    var page by remember(resetKey, startIndex, pageSize) { mutableIntStateOf((startIndex / pageSize).coerceIn(0, pages - 1)) }
    val shown = page.coerceIn(0, pages - 1)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth()) {
            if (items.isEmpty()) empty() else pageOf(items, shown, pageSize).forEach {
                Column(Modifier.height(layout.slotDp.dp).fillMaxWidth()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), propagateMinConstraints = true) { row(it) }
                    if (divided) Rule()
                }
            }
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
}
