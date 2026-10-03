package org.quran.app.data

import org.quran.app.domain.SettingsStore
import org.quran.app.domain.TranslationRepository
import org.quran.app.model.TranslationEdition
import org.quran.app.model.TranslationChapter
import org.quran.app.model.QuranCanon
import kotlinx.coroutines.CancellationException

/** QuranEnc content remains verbatim. Validated responses are cached for offline reading. */
class QuranEncTranslationRepository(
    private val http: TranslationHttpClient,
    private val cache: SettingsStore,
    private val endpoints: QuranEncEndpoints = QuranEncEndpoints(),
) : TranslationRepository {
    private val parser = QuranEncTranslationParser()
    private val chapters = TranslationChapterCache(cache, parser)
    override suspend fun editions(languageCode: String?): List<TranslationEdition> {
        val key = "translation.catalog.v1.${languageCode ?: "all"}"
        val response = freshOrCached(endpoints.translations(languageCode), key, parser::editions)
        return parser.editions(response)
    }

    override suspend fun chapter(edition: TranslationEdition, surah: Int): TranslationChapter {
        QuranCanon.verseCount(surah)
        val url = endpoints.surahTranslation(edition.id, surah)
        val previous = chapters.read(edition.id, surah)
        val response = try {
            http.get(url)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val verses = response?.let { runCatching { parser.surah(it, edition, surah) }.getOrNull() }
        if (response != null && verses != null) {
            chapters.save(edition, surah, response)
            return TranslationChapter(edition, verses)
        }
        if (previous != null) {
            return previous.copy(
                isOlder = previous.edition.lastUpdatedEpochSeconds < edition.lastUpdatedEpochSeconds ||
                    (previous.edition.lastUpdatedEpochSeconds == edition.lastUpdatedEpochSeconds &&
                        previous.edition.version != edition.version),
            )
        }
        if (response != null) parser.surah(response, edition, surah)
        error("Translation content is unavailable. Check your connection and try again.")
    }

    private suspend fun <T> freshOrCached(url: String, cacheKey: String, validate: (String) -> T): String {
        val previous = cache.get(cacheKey)
        val fresh = try {
            http.get(url)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val freshIsValid = fresh?.let { response ->
            try {
                validate(response)
                true
            } catch (_: Exception) {
                false
            }
        } ?: false
        if (fresh != null && freshIsValid) {
            cache.set(cacheKey, fresh)
            return fresh
        }
        if (previous != null) {
            validate(previous)
            return previous
        }
        if (fresh != null) validate(fresh)
        error("Translation content is unavailable. Check your connection and try again.")
    }

}
