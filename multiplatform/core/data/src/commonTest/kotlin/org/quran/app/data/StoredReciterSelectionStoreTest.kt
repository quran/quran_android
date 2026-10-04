package org.quran.app.data

import kotlin.test.*

class StoredReciterSelectionStoreTest {
    @Test fun selectionSurvivesStoreRecreationAndUnknownValuesFallBackSafely() {
        val settings = MemorySettingsStore()
        val store = StoredReciterSelectionStore(settings)
        assertEquals("alafasy", store.selectedReciterId())
        store.select("husary")
        assertEquals("husary", StoredReciterSelectionStore(settings).selectedReciterId())
        assertFailsWith<IllegalArgumentException> { store.select("../other") }
        assertEquals("husary", store.selectedReciterId())
        settings.set("recitation.selected-reciter.v1", "removed-reciter")
        assertEquals("alafasy", store.selectedReciterId())
    }
}
