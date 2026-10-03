package org.quran.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class StoredTranslationSelectionStoreTest {
    @Test
    fun selectionSurvivesStoreRecreationAndCanBeCleared() {
        val settings = MemorySettingsStore()

        StoredTranslationSelectionStore(settings).selectEdition("english_saheeh")
        assertEquals("english_saheeh", StoredTranslationSelectionStore(settings).selectedEditionId())

        StoredTranslationSelectionStore(settings).selectEdition(null)
        assertNull(StoredTranslationSelectionStore(settings).selectedEditionId())
    }

    @Test
    fun rejectsPathContentInEditionIdentifier() {
        assertFailsWith<IllegalArgumentException> {
            StoredTranslationSelectionStore(MemorySettingsStore()).selectEdition("../translation")
        }
    }
}
