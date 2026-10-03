package org.quran.app.model

data class VerseId(val surah: Int, val ayah: Int) { init { validateVerse(surah, ayah) } }
/** Hafs/Madani verse numbering; counts verified against the bundled Tanzil metadata. */
object QuranCanon {
    private val counts = intArrayOf(7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135, 112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85, 54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6)
    fun verseCount(surah: Int): Int {
        require(surah in 1..114) { "Surah must be in 1..114" }
        return counts[surah - 1]
    }
}
private fun validateVerse(surah: Int, ayah: Int) {
    require(ayah in 1..QuranCanon.verseCount(surah)) { "Ayah is outside this surah" }
}
data class Chapter(val number: Int, val arabicName: String, val englishName: String, val verseCount: Int)
data class Verse(val id: VerseId, val arabic: String, val translation: String, val source: String)
enum class AppLanguage(val code: String, val isRtl: Boolean) { ENGLISH("en", false), ARABIC("ar", true) }
data class StudyProgress(val lastRead: VerseId = VerseId(1, 1), val memorized: Set<VerseId> = emptySet(), val bookmarks: Set<VerseId> = emptySet(), val language: AppLanguage = AppLanguage.ENGLISH, val childMode: Boolean = false)
