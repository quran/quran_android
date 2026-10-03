package org.quran.app.domain

import org.quran.app.model.Verse

data class TutorAnswer(
    val text: String,
    val citations: List<String>,
    val isAiGenerated: Boolean,
)

interface TutorGateway {
    suspend fun explain(question: String, verse: Verse): TutorAnswer
}
