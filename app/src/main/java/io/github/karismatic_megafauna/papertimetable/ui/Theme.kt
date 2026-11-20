package io.github.karismatic_megafauna.papertimetable.ui

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Everything on screen is pure black on pure white, heavy type, and still.
 * Nothing here animates: no ripples, transitions or scrolling. Sizes are
 * starting points tuned on the Kompakt's 4.3" 800x480 E Ink screen.
 */
object Ink {
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    val Title = TextStyle(color = Black, fontSize = 24.sp, fontWeight = FontWeight.Black)
    val Row = TextStyle(color = Black, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    val Small = TextStyle(color = Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val Gutter = 12.dp
    val ButtonHeight = 64.dp
}

@Composable
fun EInkTheme(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink.White).safeDrawingPadding()) { content() }
}

/** Clickable with no ripple or other feedback drawing; E Ink shows the result instead. */
fun Modifier.tap(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = null, enabled = enabled, onClick = onClick)

@Composable
fun InkText(text: String, style: TextStyle = Ink.Row, modifier: Modifier = Modifier, inverted: Boolean = false, maxLines: Int = 2) {
    BasicText(text, modifier, style.copy(color = if (inverted) Ink.White else Ink.Black), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/**
 * A button at least 64 dp tall. Buttons sit edge to edge in bars and stacks,
 * so they draw no outline of their own: boxes side by side double every line.
 * The caller rules them off with [Rule] and [VRule]. Only a button standing
 * alone asks for a [border]. A disabled button keeps its place but drops its label.
 */
@Composable
fun InkButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, inverted: Boolean = false, style: TextStyle = Ink.Row, border: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = Ink.ButtonHeight)
            .background(if (inverted) Ink.Black else Ink.White)
            .then(if (border) Modifier.border(2.dp, Ink.Black) else Modifier)
            .tap(enabled, onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        InkText(if (enabled) label else "", style, inverted = inverted, maxLines = 1)
    }
}

@Composable
fun Rule() {
    Box(Modifier.fillMaxWidth().height(2.dp).background(Ink.Black))
}

/** A vertical line between two things side by side; it takes the height of its row. */
@Composable
fun VRule() {
    Box(Modifier.fillMaxHeight().width(2.dp).background(Ink.Black))
}

/** Where this screen sits, its title, a rule, then the screen's content filling the rest. */
@Composable
fun ScreenFrame(title: String, trailing: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LocalBack.current?.let { back ->
            Box(
                Modifier.fillMaxWidth().height(48.dp).tap(onClick = back.go).padding(horizontal = Ink.Gutter),
                contentAlignment = Alignment.CenterStart,
            ) { InkText(back.label, Ink.Row, maxLines = 1) }
            Rule()
        }
        androidx.compose.foundation.layout.Row(
            // Anything trailing runs the full height of the row, so its dividing lines reach from the rule to the top.
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (title.length <= 16) InkText(title, Ink.Title, Modifier.weight(1f).padding(horizontal = Ink.Gutter), maxLines = 1)
            else InkText(title, Ink.Row, Modifier.weight(1f).padding(Ink.Gutter, 4.dp), maxLines = 2)
            trailing()
        }
        Rule()
        content()
    }
}
