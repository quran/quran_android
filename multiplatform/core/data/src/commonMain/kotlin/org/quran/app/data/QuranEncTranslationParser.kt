package org.quran.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.quran.app.model.*

/** Maps QuranEnc responses without rewriting the source translation strings. */
internal class QuranEncTranslationParser {
    fun editions(raw: String): List<TranslationEdition> {
        val translations = Json.parseToJsonElement(raw).asObject()["translations"]?.asArray()
            ?: error("Translation catalog is missing")
        return translations.map { item ->
            val value = item.asObject()
            val id = value.string("key")
            require(id.matches(Regex("[a-z0-9_]+"))) { "Invalid translation identifier" }
            val language = value.string("language_iso_code")
            require(language.matches(Regex("[a-z]{2,3}(-[A-Z]{2})?"))) { "Invalid translation language" }
            val direction = when (value.stringOrNull("direction")?.lowercase()) {
                "rtl" -> TextDirection.RTL
                "ltr", null -> if (language in RTL_LANGUAGES) TextDirection.RTL else TextDirection.LTR
                else -> error("Unknown translation text direction")
            }
            TranslationEdition(
                id = id,
                languageCode = language,
                languageName = value.stringOrNull("language_name") ?: language,
                title = value.string("title"),
                translator = value.stringOrNull("description") ?: value.string("title"),
                version = value.string("version"),
                lastUpdatedEpochSeconds = value.long("last_update"),
                direction = direction,
            )
        }.also { editions ->
            require(editions.map { it.id }.distinct().size == editions.size) { "Duplicate translation editions" }
        }
    }

    fun surah(raw: String, edition: TranslationEdition, surah: Int): List<VerseTranslation> {
        val verses = Json.parseToJsonElement(raw).asObject()["result"]?.asArray()
            ?: error("Translation response is missing its verse list")
        require(verses.size == QuranCanon.verseCount(surah)) { "Translation verse count does not match Quran numbering" }
        return verses.mapIndexed { index, element ->
            val row = element.asObject()
            val rowSurah = row.int("sura")
            val ayah = row.int("aya")
            require(rowSurah == surah && ayah == index + 1) { "Translation verse numbering is incomplete or out of order" }
            VerseTranslation(
                editionId = edition.id,
                verseId = VerseId(surah, ayah),
                text = row.string("translation"),
                footnotes = row.stringOrNull("footnotes") ?: "",
            )
        }
    }

    private fun JsonElement.asObject() = this as? JsonObject ?: error("Unexpected translation response")
    private fun JsonElement.asArray() = this as? JsonArray ?: error("Unexpected translation response")
    private fun JsonObject.string(key: String) = this[key]?.primitiveContent() ?: error("Missing translation field: $key")
    private fun JsonObject.stringOrNull(key: String) = this[key]?.primitiveContent()
    private fun JsonObject.int(key: String) = string(key).toIntOrNull() ?: error("Invalid translation verse number")
    private fun JsonObject.long(key: String) = string(key).toLongOrNull() ?: error("Invalid translation version date")
    private fun JsonElement.primitiveContent() = (this as? JsonPrimitive)?.content ?: error("Unexpected translation field")

    private companion object {
        val RTL_LANGUAGES = setOf("ar", "fa", "ur", "ps", "sd", "ug", "yi")
    }
}
