package org.quran.app.model

/** A validated chapter and verse address using the bundled Hafs/Madani numbering. */
data class VerseId(val surah: Int, val ayah: Int) {
    init {
        require(ayah in 1..QuranCanon.verseCount(surah)) { "Ayah is outside this surah" }
    }
}
