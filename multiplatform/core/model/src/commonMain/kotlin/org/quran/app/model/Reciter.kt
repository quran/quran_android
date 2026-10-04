package org.quran.app.model

/** Provider metadata for one supported Hafs recitation set. */
data class Reciter(
    val id: String,
    val nameEnglish: String,
    val nameArabic: String,
    val folder: String,
    val sourceUrl: String,
)
