package inktransit.app.ui

import androidx.compose.runtime.mutableStateListOf

sealed interface Screen {
    data object MyRoutes : Screen
}

/** A plain back stack. Screens swap instantly; there are no transitions to animate. */
class Nav(start: Screen = Screen.MyRoutes) {
    private val stack = mutableStateListOf(start)
    val top: Screen get() = stack.last()
    val canPop: Boolean get() = stack.size > 1
    fun push(screen: Screen) { stack.add(screen) }
    fun pop(): Boolean = if (canPop) { stack.removeAt(stack.lastIndex); true } else false
    fun home() { while (pop()) Unit }
}
