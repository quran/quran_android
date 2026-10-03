package org.quran.app.domain

import org.quran.app.model.VerseId

/** Counts completed playbacks; memorization still requires explicit learner confirmation. */
class RepeatSession(
    verses: List<VerseId>,
    val repetitionsPerVerse: Int,
    val repeatUntilMemorized: Boolean = false,
) {
    val verses: List<VerseId> = verses.toList()
    private var index = 0
    private var repetitions = 0
    private var complete = false

    init {
        require(this.verses.isNotEmpty()) { "Select at least one verse" }
        require(repetitionsPerVerse > 0) { "Repetitions must be positive" }
    }

    fun state() = RepeatState(verses[index], repetitions, complete)

    fun onRecitationCompleted(): RepeatState {
        if (complete) return state()
        if (repetitions < Int.MAX_VALUE) repetitions++
        if (!repeatUntilMemorized && repetitions >= repetitionsPerVerse) advance()
        return state()
    }

    fun markMemorized(): RepeatState {
        if (!complete) advance()
        return state()
    }

    fun reset(): RepeatState {
        index = 0
        repetitions = 0
        complete = false
        return state()
    }

    private fun advance() {
        if (index == verses.lastIndex) {
            complete = true
        } else {
            index++
            repetitions = 0
        }
    }
}
