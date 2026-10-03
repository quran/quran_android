package org.quran.app.domain

import org.quran.app.model.Juz
import org.quran.app.model.Chapter
import org.quran.app.model.Verse

interface QuranRepository {
    fun chapters(): List<Chapter>
    fun juzs(): List<Juz>
    fun verses(surah: Int): List<Verse>
}
