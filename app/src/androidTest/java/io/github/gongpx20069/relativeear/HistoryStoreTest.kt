package io.github.gongpx20069.relativeear

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class HistoryStoreTest {
    @Test fun preservesNullableTimeoutAndSettingsSnapshot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = HistoryStore(context)
        try {
            store.clear()
            store.save("test", Attempt("sing", 60, 64, 4, false, timeout = true), Settings())
            store.save("test", Attempt("interval", 60, 67, 7, true), Settings())
            val history = store.history()
            assertEquals(2, history.total)
            assertEquals(1, history.correct)
            assertTrue(history.attempts.last().timeout)
            assertNull(history.attempts.last().cents)
            store.clear()
            assertEquals(0, store.history().total)
        } finally {
            store.close()
        }
    }
}
