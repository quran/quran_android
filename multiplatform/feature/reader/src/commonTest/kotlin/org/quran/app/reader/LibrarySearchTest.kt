package org.quran.app.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import org.quran.app.model.Chapter

class LibrarySearchTest {
    private val chapters = listOf(
        Chapter(number = 1, arabicName = "الْفَاتِحَة", englishName = "Al-Fatihah", verseCount = 7),
        Chapter(number = 2, arabicName = "ٱلْبَقَرَةُ", englishName = "Al-Baqarah", verseCount = 286),
        Chapter(number = 3, arabicName = "آل عمران", englishName = "Ali Imran", verseCount = 200),
    )

    @Test
    fun englishSearchIgnoresCaseAndSeparators() {
        assertEquals(listOf(2), filterChapters(chapters, "AL baqarah").map(Chapter::number))
    }

    @Test
    fun arabicSearchIgnoresDiacriticsAndAlefVariants() {
        assertEquals(listOf(2), filterChapters(chapters, "البقرة").map(Chapter::number))
        assertEquals(listOf(3), filterChapters(chapters, "ال عمران").map(Chapter::number))
    }

    @Test
    fun numbersMatchWithAsciiAndArabicIndicDigits() {
        assertEquals(listOf(2), filterChapters(chapters, "2").map(Chapter::number))
        assertEquals(listOf(2), filterChapters(chapters, "٢").map(Chapter::number))
    }

    @Test
    fun emptySearchRestoresEveryChapterAfterClearing() {
        assertEquals(chapters, filterChapters(chapters, ""))
    }
}
