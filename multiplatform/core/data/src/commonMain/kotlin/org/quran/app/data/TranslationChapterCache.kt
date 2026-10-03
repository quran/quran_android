package org.quran.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import org.quran.app.domain.SettingsStore
import org.quran.app.model.TranslationChapter
import org.quran.app.model.TranslationEdition

/** Stores content and its attribution in one atomic value, independent of the current catalog. */
internal class TranslationChapterCache(
    private val settings: SettingsStore,
    private val parser: QuranEncTranslationParser,
) {
    fun read(editionId: String, surah: Int): TranslationChapter? {
        val snapshot = settings.get(key(editionId, surah)) ?: return null
        return runCatching {
            val envelope = Json.parseToJsonElement(snapshot) as? JsonObject ?: error("Invalid chapter cache")
            require((envelope["schema"] as? JsonPrimitive)?.intOrNull == SCHEMA)
            val metadata = envelope["edition"] as? JsonObject ?: error("Missing cached edition")
            for (field in listOf("key", "language_iso_code", "language_name", "title", "description", "version", "direction")) {
                metadata.string(field)
            }
            val catalog = JsonObject(mapOf("translations" to JsonArray(listOf(metadata))))
            val edition = parser.editions(catalog.toString()).single()
            require(edition.id == editionId) { "Cached edition does not match the selected edition" }
            require(metadata.string("publisher") == edition.publisher)
            require(metadata.string("source_url") == edition.sourceUrl)
            validateEdition(edition)
            val verses = parser.surah(envelope.string("response"), edition, surah)
            TranslationChapter(edition, verses, isCached = true)
        }.getOrNull()
    }

    fun save(edition: TranslationEdition, surah: Int, response: String) {
        validateEdition(edition)
        // Never replace a validated snapshot with partial or misnumbered content.
        parser.surah(response, edition, surah)
        val metadata = buildJsonObject {
            put("key", edition.id)
            put("language_iso_code", edition.languageCode)
            put("language_name", edition.languageName)
            put("title", edition.title)
            put("description", edition.translator)
            put("version", edition.version)
            put("last_update", edition.lastUpdatedEpochSeconds)
            put("direction", edition.direction.name.lowercase())
            put("publisher", edition.publisher)
            put("source_url", edition.sourceUrl)
        }
        val envelope = buildJsonObject {
            put("schema", SCHEMA)
            put("edition", metadata)
            put("response", response)
        }
        settings.set(key(edition.id, surah), envelope.toString())
    }

    private fun validateEdition(edition: TranslationEdition) {
        require(edition.id.matches(Regex("[a-z0-9_]+")))
        require(edition.languageCode.matches(Regex("[a-z]{2,3}(-[A-Z]{2})?")))
        require(edition.title.isNotBlank() && edition.translator.isNotBlank() && edition.languageName.isNotBlank())
        require(edition.version.isNotBlank() && edition.lastUpdatedEpochSeconds >= 0)
        // Cache data must not turn the source action into an arbitrary external URL.
        require(edition.publisher == "QuranEnc.com" && edition.sourceUrl == "https://quranenc.com/en/home/api")
    }

    private fun JsonObject.string(name: String): String {
        val value = this[name] as? JsonPrimitive ?: error("Missing cached field: $name")
        require(value.isString) { "Invalid cached field: $name" }
        return value.content
    }

    private fun key(editionId: String, surah: Int) = "translation.chapter.v2.$editionId.$surah"

    private companion object { const val SCHEMA = 2 }
}
