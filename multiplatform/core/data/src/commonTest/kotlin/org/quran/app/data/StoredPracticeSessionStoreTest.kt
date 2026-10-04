package org.quran.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.quran.app.domain.SettingsStore
import org.quran.app.model.PracticeSessionSnapshot

class StoredPracticeSessionStoreTest {
    @Test
    fun roundTripPreservesPausedLearningState() {
        val values = mutableMapOf<String, String>()
        val store = StoredPracticeSessionStore(object : SettingsStore {
            override fun get(key: String) = values[key]
            override fun set(key: String, value: String) { values[key] = value }
        })
        val expected = PracticeSessionSnapshot(2, 3, 7, 5, 3, 2, true, false)

        store.save(expected)

        assertEquals(expected, store.read())
    }

    @Test
    fun malformedSnapshotIsIgnoredAndDoesNotTouchProgressStorage() {
        val values = mutableMapOf("progress.v1" to "1|1:1|||ENGLISH|false", "practice_session.v1" to "1|bad")
        val store = StoredPracticeSessionStore(object : SettingsStore {
            override fun get(key: String) = values[key]
            override fun set(key: String, value: String) { values[key] = value }
        })

        assertNull(store.read())
        assertEquals("1|1:1|||ENGLISH|false", values["progress.v1"])
    }
}
