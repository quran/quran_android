package org.quran.app.model

/** Canonical Arabic text remains separate from all translation editions. */
data class Verse(
    val id: VerseId,
    val arabic: String,
    val source: String,
)
