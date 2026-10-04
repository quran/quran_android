package org.quran.app.model

/** Presentation preferences independent of scripture content and reading progress. */
data class ReadingPreferences(
    val arabicTextSize: ReadingTextSize = ReadingTextSize.DEFAULT,
    val translationTextSize: ReadingTextSize = ReadingTextSize.DEFAULT,
)
