package org.quran.app.reader

import org.quran.app.model.Chapter

internal fun filterChapters(chapters: List<Chapter>, query: String): List<Chapter> {
    val normalizedQuery = normalizeLibrarySearch(query)
    if (normalizedQuery.isEmpty()) return chapters

    return chapters.filter { chapter ->
        normalizedQuery == chapter.number.toString() ||
            normalizeLibrarySearch(chapter.englishName).contains(normalizedQuery) ||
            normalizeLibrarySearch(chapter.arabicName).contains(normalizedQuery)
    }
}

private fun normalizeLibrarySearch(value: String): String = buildString {
    for (character in value.lowercase()) {
        when {
            character in '\u064B'..'\u065F' || character == '\u0670' ||
                character in '\u06D6'..'\u06ED' || character == '\u0640' -> Unit
            character in '\u0660'..'\u0669' -> append(('0'.code + character.code - '\u0660'.code).toChar())
            character in '\u06F0'..'\u06F9' -> append(('0'.code + character.code - '\u06F0'.code).toChar())
            character.isLetterOrDigit() -> append(
                when (character) {
                    'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                    'ى', 'ئ' -> 'ي'
                    'ة' -> 'ه'
                    'ؤ' -> 'و'
                    else -> character
                },
            )
        }
    }
}
