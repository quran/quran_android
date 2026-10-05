package org.quran.app.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.quran.app.model.Chapter

class LibrarySearchTest {
    private val chapters = listOf(
        Chapter(number = 1, arabicName = "الْفَاتِحَة", englishName = "Al-Fatihah", verseCount = 7),
        Chapter(number = 2, arabicName = "ٱلْبَقَرَةُ", englishName = "Al-Baqarah", verseCount = 286),
        Chapter(number = 3, arabicName = "آل عمران", englishName = "Ali Imran", verseCount = 200),
        Chapter(number = 12, arabicName = "يُوسُف", englishName = "Yusuf", verseCount = 111),
        Chapter(number = 20, arabicName = "طه", englishName = "Ta-Ha", verseCount = 135),
        Chapter(number = 87, arabicName = "الْأَعْلَى", englishName = "Al-A'la", verseCount = 19),
        Chapter(number = 112, arabicName = "الْإِخْلَاص", englishName = "Al-Ikhlas", verseCount = 4),
    )

    @Test
    fun englishSearchIgnoresCaseAndSeparators() {
        assertEquals(listOf(2), filterChapters(chapters, "AL baqarah").map(Chapter::number))
        assertEquals(listOf(1), filterChapters(chapters, "al-fatihah").map(Chapter::number))
        assertEquals(listOf(3), filterChapters(chapters, "ali imran").map(Chapter::number))
    }

    @Test
    fun englishSearchMatchesSubstrings() {
        assertEquals(listOf(1), filterChapters(chapters, "fatih").map(Chapter::number))
        assertEquals(listOf(20), filterChapters(chapters, "taha").map(Chapter::number))
        assertEquals(listOf(1, 20), filterChapters(chapters, "ha").map(Chapter::number))
    }

    @Test
    fun arabicSearchIgnoresDiacriticsAndAlefVariants() {
        assertEquals(listOf(2), filterChapters(chapters, "البقرة").map(Chapter::number))
        assertEquals(listOf(3), filterChapters(chapters, "ال عمران").map(Chapter::number))
        assertEquals(listOf(1), filterChapters(chapters, "الفاتحة").map(Chapter::number))
    }

    @Test
    fun arabicSearchNormalizesTaMarbutaAndAlefForms() {
        assertEquals(listOf(1), filterChapters(chapters, "الفاتحه").map(Chapter::number))
        assertEquals(listOf(2), filterChapters(chapters, "البقره").map(Chapter::number))
    }

    @Test
    fun arabicSearchIgnoresTatweelAndDiacriticsInQuery() {
        assertEquals(listOf(2), filterChapters(chapters, "الـبـقـرة").map(Chapter::number))
        assertEquals(listOf(1), filterChapters(chapters, "الْفَاتِحَةُ").map(Chapter::number))
    }

    @Test
    fun arabicSearchNormalizesAlefMaqsuraAndHamzaVariants() {
        assertEquals(listOf(87), filterChapters(chapters, "الاعلى").map(Chapter::number))
        assertEquals(listOf(87), filterChapters(chapters, "الأعلى").map(Chapter::number))
        assertEquals(listOf(87), filterChapters(chapters, "الاعلي").map(Chapter::number))
        assertEquals(listOf(112), filterChapters(chapters, "الاخلاص").map(Chapter::number))
        assertEquals(listOf(112), filterChapters(chapters, "الإخلاص").map(Chapter::number))
    }

    @Test
    fun arabicSubstringSearchMatchesPartsOfNames() {
        assertEquals(listOf(3), filterChapters(chapters, "عمران").map(Chapter::number))
        assertEquals(listOf(2), filterChapters(chapters, "بقر").map(Chapter::number))
    }

    @Test
    fun numbersMatchWithAsciiAndArabicIndicDigits() {
        assertEquals(listOf(2), filterChapters(chapters, "2").map(Chapter::number))
        assertEquals(listOf(2), filterChapters(chapters, "٢").map(Chapter::number))
        assertEquals(listOf(2), filterChapters(chapters, "۲").map(Chapter::number))
    }

    @Test
    fun numbersWithLeadingZeroMatchExactChapter() {
        assertEquals(listOf(1), filterChapters(chapters, "01").map(Chapter::number))
        assertEquals(listOf(2), filterChapters(chapters, "02").map(Chapter::number))
        assertEquals(listOf(2), filterChapters(chapters, "٠٢").map(Chapter::number))
    }

    @Test
    fun numberSearchWithWhitespaceMatchesCorrectChapter() {
        assertEquals(listOf(1), filterChapters(chapters, " 1 ").map(Chapter::number))
        assertEquals(listOf(2), filterChapters(chapters, " 02 ").map(Chapter::number))
        assertEquals(listOf(87), filterChapters(chapters, " 87 ").map(Chapter::number))
    }

    @Test
    fun exactNumberSearchDoesNotFalselyMatchOtherChapters() {
        assertEquals(listOf(2), filterChapters(chapters, "2").map(Chapter::number))
        assertEquals(listOf(12), filterChapters(chapters, "12").map(Chapter::number))
        assertEquals(listOf(20), filterChapters(chapters, "20").map(Chapter::number))
    }

    @Test
    fun chapterOrderIsPreservedWhenFiltering() {
        val matches = filterChapters(chapters, "al")
        assertEquals(listOf(1, 2, 3, 87, 112), matches.map(Chapter::number))
    }

    @Test
    fun emptySearchRestoresEveryChapterAfterClearing() {
        assertEquals(chapters, filterChapters(chapters, ""))
        assertEquals(chapters, filterChapters(chapters, "   "))
        assertEquals(chapters, filterChapters(chapters, "---"))
    }

    @Test
    fun noMatchReturnsEmptyList() {
        assertTrue(filterChapters(chapters, "NonExistentChapter").isEmpty())
        assertTrue(filterChapters(chapters, "999").isEmpty())
    }

    @Test
    fun normalizeLibrarySearchTransformsCharactersCorrectly() {
        assertEquals("albaqarah", normalizeLibrarySearch("Al-Baqarah"))
        assertEquals("الفاتحه", normalizeLibrarySearch("الْفَاتِحَةُ"))
        assertEquals("العمران", normalizeLibrarySearch("آل عِمْرَان"))
        assertEquals("123", normalizeLibrarySearch("١٢٣"))
        assertEquals("456", normalizeLibrarySearch("۴۵۶"))
        assertEquals("يوسف", normalizeLibrarySearch("يُوسُـف"))
        assertEquals("الاعلي", normalizeLibrarySearch("الْأَعْلَى"))
        assertEquals("الاخلاص", normalizeLibrarySearch("الْإِخْلَاص"))
    }

    @Test
    fun librarySectionEntriesPreserveOrder() {
        assertEquals(
            listOf(LibrarySection.SURAHS, LibrarySection.JUZ, LibrarySection.BOOKMARKS),
            LibrarySection.entries,
        )
    }
}
