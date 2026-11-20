package inktransit.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import inktransit.app.ui.screens.STILL_CURSOR
import org.junit.Assert.assertEquals
import org.junit.Test

class StillnessTest {
    // Compose blinks any cursor that has a colour, twice a second. On E Ink
    // that is a screen refresh twice a second for as long as a field has focus.
    @Test fun textFieldsHaveNoBlinkingCursor() = assertEquals(Color.Unspecified, (STILL_CURSOR as SolidColor).value)
}
