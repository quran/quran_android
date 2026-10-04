package org.quran.app.data

import org.quran.app.domain.ProgressRepository
import org.quran.app.domain.SettingsStore
import org.quran.app.model.AppLanguage
import org.quran.app.model.StudyProgress
import org.quran.app.model.VerseId

/** A single atomic value avoids partial updates between bookmarks and preferences. */
class StoredProgressRepository(private val store: SettingsStore) : ProgressRepository {
    override fun read(): StudyProgress {
        val value = store.get(KEY) ?: return StudyProgress()
        return runCatching {
            val fields = value.split('|')
            require(fields.size == 6 && fields[0] == "1")
            StudyProgress(
                lastRead = parseId(fields[1]),
                memorized = parseSet(fields[2]),
                bookmarks = parseSet(fields[3]),
                language = AppLanguage.valueOf(fields[4]),
                childMode = when (fields[5]) {
                    "true" -> true
                    "false" -> false
                    else -> error("Invalid child setting")
                },
            )
        }.getOrElse { StudyProgress() }
    }

    override fun save(progress: StudyProgress) {
        val fields = listOf(
            "1",
            format(progress.lastRead),
            formatSet(progress.memorized),
            formatSet(progress.bookmarks),
            progress.language.name,
            progress.childMode.toString(),
        )
        store.set(KEY, fields.joinToString("|"))
    }

    private fun format(id: VerseId): String = "${id.surah}:${id.ayah}"

    private fun formatSet(ids: Set<VerseId>): String = ids
        .sortedWith(compareBy({ it.surah }, { it.ayah }))
        .joinToString(",", transform = ::format)

    private fun parseId(value: String): VerseId {
        val parts = value.split(':')
        require(parts.size == 2)
        return VerseId(parts[0].toInt(), parts[1].toInt())
    }

    private fun parseSet(value: String): Set<VerseId> = if (value.isEmpty()) {
        emptySet()
    } else {
        value.split(',').map(::parseId).toSet()
    }

    private companion object {
        const val KEY = "progress.v1"
    }
}
