package pw.rkd.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
}
