package org.quran.app.domain

import org.quran.app.model.Chapter
import org.quran.app.model.Verse

interface QuranRepository {
    fun chapters(): List<Chapter>
    fun verses(surah: Int): List<Verse>
}
