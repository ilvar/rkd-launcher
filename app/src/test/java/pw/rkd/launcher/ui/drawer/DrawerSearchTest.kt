package pw.rkd.launcher.ui.drawer

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerSearchTest {
    @Test fun `exact matches rank before one and two edits`() {
        assertEquals(0, searchRank("telegram", "telegram"))
        assertEquals(1, searchRank("maps", "google maps"))
        assertEquals(4, searchRank("telegrm", "telegram"))
        assertEquals(5, searchRank("telegarm", "telegram"))
    }

    @Test fun `one and two edits match app names`() {
        assertEquals(1, fuzzyDistance("telegrm", "telegram", listOf("telegram"))) // deletion
        assertEquals(1, fuzzyDistance("telegrax", "telegram", listOf("telegram"))) // substitution
        assertEquals(2, fuzzyDistance("telegarm", "telegram", listOf("telegram"))) // transposition
        assertEquals(3, fuzzyDistance("xyz123", "telegram", listOf("telegram")))
    }

    @Test fun `partial and multiword searches tolerate typos`() {
        assertEquals(1, fuzzyDistance("telgr", "telegram", listOf("telegram")))
        assertEquals(1, fuzzyDistance("google mps", "google maps", listOf("google", "maps")))
        assertEquals(3, fuzzyDistance("go", "google maps", listOf("google", "maps")))
    }
}
