package org.quran.app.model

/** Presentation preferences independent of scripture content and reading progress. */
data class ReadingPreferences(
    val arabicTextSize: ReadingTextSize = ReadingTextSize.DEFAULT,
    val translationTextSize: ReadingTextSize = ReadingTextSize.DEFAULT,
)

/** Children mode uses the largest reading text for both scripture and translation. */
fun ReadingPreferences.forChildrenMode(enabled: Boolean): ReadingPreferences =
    if (enabled) copy(
        arabicTextSize = ReadingTextSize.LARGE,
        translationTextSize = ReadingTextSize.LARGE,
    ) else this
