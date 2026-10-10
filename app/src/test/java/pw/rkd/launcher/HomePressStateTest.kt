package pw.rkd.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class HomePressStateTest {
    @Test fun repeatedHomeOpensAppsEvenIfWindowFocusChanges() {
        val state = HomePressState()
        state.onResume()
        assertTrue(state.onHomeIntent())
    }

    @Test fun returnFromAnotherAppStartsAtHome() {
        val state = HomePressState()
        state.onResume()
        state.onStop()
        assertFalse(state.onHomeIntent())
        assertTrue(state.onHomeIntent())
    }

    @Test fun initialHomeIntentDoesNotOpenApps() {
        assertFalse(HomePressState().onHomeIntent())
    }

    @Test fun homeTogglesBetweenHomeAndAppsAndLeavesWidgets() {
        assertEquals(1, homeDestination(true, 0))
        assertEquals(0, homeDestination(true, 1))
        assertEquals(0, homeDestination(true, 2))
        assertEquals(0, homeDestination(false, 1))
        assertEquals(2, homeDestination(true, 0, appsPage = 2))
        assertEquals(0, homeDestination(true, 1, appsPage = 2))
        assertEquals(0, homeDestination(true, 2, appsPage = 2))
    }
}
