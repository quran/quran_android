package org.quran.app.data

import org.quran.app.domain.PracticeSessionStore
import org.quran.app.domain.SettingsStore
import org.quran.app.model.PracticeSessionSnapshot

/** Versioned, isolated storage so corrupt practice data cannot invalidate reading progress. */
class StoredPracticeSessionStore(private val store: SettingsStore) : PracticeSessionStore {
    override fun read(): PracticeSessionSnapshot? = store.get(KEY)?.let { value ->
        runCatching {
            val fields = value.split('|')
            require(fields.size == 10 && fields[0] == VERSION)
            PracticeSessionSnapshot(
                surah = fields[1].toInt(), startAyah = fields[2].toInt(), endAyah = fields[3].toInt(),
                currentAyah = fields[4].toInt(), repetitionsPerVerse = fields[5].toInt(),
                completedRepetitions = fields[6].toInt(), repeatUntilMemorized = fields[7].toBooleanStrict(),
                complete = fields[8].toBooleanStrict(), autoplayRequested = fields[9].toBooleanStrict(),
            )
        }.getOrNull()
    }

    override fun save(snapshot: PracticeSessionSnapshot) {
        store.set(KEY, listOf(VERSION, snapshot.surah, snapshot.startAyah, snapshot.endAyah,
            snapshot.currentAyah, snapshot.repetitionsPerVerse, snapshot.completedRepetitions,
            snapshot.repeatUntilMemorized, snapshot.complete, snapshot.autoplayRequested).joinToString("|"))
    }

    override fun clear() { store.set(KEY, "") }

    private companion object { const val KEY = "practice_session.v1"; const val VERSION = "1" }
}
