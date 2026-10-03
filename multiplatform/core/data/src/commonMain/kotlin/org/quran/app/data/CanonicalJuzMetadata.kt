package org.quran.app.data

import org.quran.app.model.Juz
import org.quran.app.model.VerseId

/**
 * Generated from unchanged core/data/content/quran-data.xml by tools/generate-juz-metadata.py.
 * Source SHA-256: 423afa57b81ccee79a4f15a2df1b8aa6a6c06498cfe8ce12e627adc41512c4a9
 * Runtime metadata is synchronous and offline on every KMP target.
 */
internal object CanonicalJuzMetadata {
    private val entries = listOf(
        Juz(1, VerseId(1, 1)),
        Juz(2, VerseId(2, 142)),
        Juz(3, VerseId(2, 253)),
        Juz(4, VerseId(3, 93)),
        Juz(5, VerseId(4, 24)),
        Juz(6, VerseId(4, 148)),
        Juz(7, VerseId(5, 82)),
        Juz(8, VerseId(6, 111)),
        Juz(9, VerseId(7, 88)),
        Juz(10, VerseId(8, 41)),
        Juz(11, VerseId(9, 93)),
        Juz(12, VerseId(11, 6)),
        Juz(13, VerseId(12, 53)),
        Juz(14, VerseId(15, 1)),
        Juz(15, VerseId(17, 1)),
        Juz(16, VerseId(18, 75)),
        Juz(17, VerseId(21, 1)),
        Juz(18, VerseId(23, 1)),
        Juz(19, VerseId(25, 21)),
        Juz(20, VerseId(27, 56)),
        Juz(21, VerseId(29, 46)),
        Juz(22, VerseId(33, 31)),
        Juz(23, VerseId(36, 28)),
        Juz(24, VerseId(39, 32)),
        Juz(25, VerseId(41, 47)),
        Juz(26, VerseId(46, 1)),
        Juz(27, VerseId(51, 31)),
        Juz(28, VerseId(58, 1)),
        Juz(29, VerseId(67, 1)),
        Juz(30, VerseId(78, 1)),
    )

    fun load(): List<Juz> = entries.toList()
}
