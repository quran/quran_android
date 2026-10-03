package org.quran.app.model

/** A canonical Quran part opens at its exact starting ayah, including mid-surah starts. */
data class Juz(val number: Int, val start: VerseId) {
    init {
        require(number in 1..30) { "Juz must be 1..30" }
    }
}
