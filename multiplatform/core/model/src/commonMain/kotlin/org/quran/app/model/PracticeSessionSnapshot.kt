package org.quran.app.model

/** Durable learner state for a memorization session. Audio handles are intentionally omitted. */
data class PracticeSessionSnapshot(
    val surah: Int,
    val startAyah: Int,
    val endAyah: Int,
    val currentAyah: Int,
    val repetitionsPerVerse: Int,
    val completedRepetitions: Int,
    val repeatUntilMemorized: Boolean,
    val complete: Boolean,
    val autoplayRequested: Boolean = false,
) {
    init {
        require(surah > 0)
        require(startAyah > 0 && startAyah <= endAyah)
        require(currentAyah in startAyah..endAyah)
        require(repetitionsPerVerse > 0)
        require(completedRepetitions >= 0)
    }
}
