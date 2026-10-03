package org.quran.app.domain

import org.quran.app.model.*
interface QuranRepository { fun chapters(): List<Chapter>; fun verses(surah: Int, language: AppLanguage): List<Verse> }
interface ProgressRepository { fun read(): StudyProgress; fun save(progress: StudyProgress) }
interface SettingsStore { fun get(key: String): String?; fun set(key: String, value: String) }
data class TutorAnswer(val text: String, val citations: List<String>, val isAiGenerated: Boolean)
interface TutorGateway { suspend fun explain(question: String, verse: Verse): TutorAnswer }
data class RepeatState(val currentVerse: VerseId, val completedRepetitions: Int, val complete: Boolean)
/** Counts completed playbacks, so N means N total recitations, not N additional repeats.
 * This state machine has no platform side effects; call completion only on real player completion.
 * The learner explicitly confirms memorization. Repetition never implies a memorized verse.
 */
class RepeatSession(verses: List<VerseId>, val repetitionsPerVerse: Int, val repeatUntilMemorized: Boolean = false) {
    val verses = verses.toList()
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
        if (index == verses.lastIndex) complete = true
        else { index++; repetitions = 0 }
    }
}

/** Initial great-circle bearing from true north. Magnetic heading needs platform declination.
 * At the Kaaba and antipode the direction is undefined and is deliberately not rendered as 0°.
 */
object QiblaCalculator {
    const val KAABA_LATITUDE = 21.4225
    const val KAABA_LONGITUDE = 39.8262
    fun bearing(latitude: Double, longitude: Double): Double? {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Invalid latitude" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Invalid longitude" }
        val radians = kotlin.math.PI / 180.0
        val lat = latitude * radians
        val targetLat = KAABA_LATITUDE * radians
        val delta = (KAABA_LONGITUDE - longitude) * radians
        val y = kotlin.math.sin(delta) * kotlin.math.cos(targetLat)
        val x = kotlin.math.cos(lat) * kotlin.math.sin(targetLat) -
            kotlin.math.sin(lat) * kotlin.math.cos(targetLat) * kotlin.math.cos(delta)
        if (kotlin.math.abs(x) < 1e-12 && kotlin.math.abs(y) < 1e-12) return null
        return (kotlin.math.atan2(y, x) / radians + 360.0) % 360.0
    }
}

/** Platform playback adapter. Completion is emitted only after an actual media item ends. */
interface AudioPlayer {
    fun loadLocal(uri: String)
    fun play(onCompleted: () -> Unit, onError: (String) -> Unit)
    fun pause()
    fun release()
}
