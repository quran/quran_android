package org.quran.app.data

import org.quran.app.domain.SettingsStore
import org.quran.app.domain.TranslationRepository
import org.quran.app.model.TranslationEdition
import org.quran.app.model.VerseTranslation
import kotlinx.coroutines.CancellationException

/** QuranEnc content remains verbatim. Validated responses are cached for offline reading. */
class QuranEncTranslationRepository(
    private val http: TranslationHttpClient,
    private val cache: SettingsStore,
    private val endpoints: QuranEncEndpoints = QuranEncEndpoints(),
) : TranslationRepository {
    private val parser = QuranEncTranslationParser()
    override suspend fun editions(languageCode: String?): List<TranslationEdition> {
        val key = "translation.catalog.v1.${languageCode ?: "all"}"
        val response = freshOrCached(endpoints.translations(languageCode), key, parser::editions)
        return parser.editions(response)
    }

    override suspend fun verses(edition: TranslationEdition, surah: Int): List<VerseTranslation> {
        val safeVersion = edition.version.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val cacheKey = "translation.cache.v1.${edition.id}.$safeVersion.$surah"
        val response = freshOrCached(endpoints.surahTranslation(edition.id, surah), cacheKey) { body ->
            parser.surah(body, edition, surah)
        }
        return parser.surah(response, edition, surah)
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
