package org.quran.app.model

data class StudyProgress(
    val lastRead: VerseId = VerseId(1, 1),
    val memorized: Set<VerseId> = emptySet(),
    val bookmarks: Set<VerseId> = emptySet(),
    val language: AppLanguage = AppLanguage.ENGLISH,
    val childMode: Boolean = false,
)
