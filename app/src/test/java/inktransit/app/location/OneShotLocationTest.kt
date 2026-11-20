package inktransit.app.location

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Review Focus 3: underground, indoors or with permission off, Near me must answer, not hang.
@OptIn(ExperimentalCoroutinesApi::class)
class OneShotLocationTest {
    private class Fake(
        val permission: Boolean = true,
        val providers: List<String> = listOf("gps", "network"),
        val last: Position? = null,
        val fix: suspend (String) -> Position? = { awaitCancellation() },
    ) : LocationSource {
        override fun hasPermission() = permission
        override fun enabledProviders() = providers
        override suspend fun current(provider: String) = fix(provider)
        override fun lastKnown() = last
    }

    @Test fun deniedReturnsImmediately() = runTest {
        assertEquals(Fix.Denied, OneShotLocation(Fake(permission = false)).get())
        assertEquals(0, currentTime)
    }

    @Test fun providersOffIsOff() = runTest { assertEquals(Fix.Off, OneShotLocation(Fake(providers = emptyList())).get()) }

    @Test fun firstProviderToAnswerWins() = runTest {
        val source = Fake(fix = { p -> if (p == "network") { delay(2_000); Position(34.0, -118.0, 0) } else awaitCancellation() })
        assertEquals(Fix.Fresh(34.0, -118.0), OneShotLocation(source).get())
        assertEquals(2_000, currentTime)
    }

    @Test fun timeoutFallsBackToRecentLastKnown() = runTest {
        val fix = OneShotLocation(Fake(last = Position(34.1, -118.1, ageMinutes = 5))).get()
        assertEquals(Fix.Stale(34.1, -118.1, 5), fix)
        assertEquals(30_000, currentTime)
    }

    @Test fun timeoutWithOldLastKnownIsNone() = runTest {
        assertEquals(Fix.None, OneShotLocation(Fake(last = Position(34.1, -118.1, ageMinutes = 180))).get())
    }

    @Test fun timeoutWithNothingKnownIsNone() = runTest {
        assertEquals(Fix.None, OneShotLocation(Fake()).get())
        assertTrue(currentTime <= 30_000)
    }

    @Test fun aProviderThatGivesUpDoesNotEndTheWaitEarly() = runTest {
        val source = Fake(fix = { p -> if (p == "gps") null else { delay(5_000); Position(34.0, -118.0, 0) } })
        assertEquals(Fix.Fresh(34.0, -118.0), OneShotLocation(source).get())
    }
}
