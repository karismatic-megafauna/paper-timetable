package inktransit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import inktransit.app.ui.EInkTheme
import inktransit.app.ui.Ink
import inktransit.app.ui.InkButton
import inktransit.app.ui.InkText
import inktransit.app.ui.Nav
import inktransit.app.ui.PagedList
import inktransit.app.ui.Rule
import inktransit.app.ui.Screen
import inktransit.app.ui.ScreenFrame

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val nav = remember { Nav() }
            EInkTheme {
                BackHandler(enabled = nav.canPop) { nav.pop() }
                when (nav.top) {
                    Screen.MyRoutes -> ScreenFrame("MY ROUTES") {
                        PagedList((1..25).map { "Placeholder route $it" }, pageSize = 5, modifier = Modifier.weight(1f)) {
                            InkText(it, Ink.Row, Modifier.padding(Ink.Gutter, 14.dp))
                        }
                        Rule()
                        Row(Modifier.fillMaxWidth()) {
                            InkButton("Lines", Modifier.weight(1f)) {}
                            InkButton("Near me", Modifier.weight(1f)) {}
                            InkButton("Search", Modifier.weight(1f)) {}
                        }
                    }
                }
            }
        }
    }
}
