package org.quran.app.data

import org.quran.app.domain.SettingsStore
import org.quran.app.model.TextDirection
import org.quran.app.model.TranslationEdition

internal object TranslationTestFixtures {
    const val CATALOG_JSON = """{"translations":[{"key":"english_saheeh","language_iso_code":"en","direction":"ltr","version":"1.1.2","last_update":1773348325,"title":"English Translation - Saheeh International","description":"Saheeh International"}]}"""

    fun edition() = TranslationEdition(
        id = "english_saheeh",
        languageCode = "en",
        languageName = "en",
        title = "English Translation - Saheeh International",
        translator = "Saheeh International",
        version = "1.1.2",
        lastUpdatedEpochSeconds = 1773348325,
        direction = TextDirection.LTR,
    )

    fun surahJson(count: Int = 7, firstTranslation: String = "meaning 1") = buildString {
        append("{\"result\":[")
        repeat(count) { index ->
            if (index > 0) append(',')
            val ayah = index + 1
            val text = if (ayah == 1) firstTranslation else "meaning $ayah"
            append("{\"sura\":\"1\",\"aya\":\"$ayah\",\"arabic_text\":\"source Arabic\",\"translation\":${jsonString(text)},\"footnotes\":\"${if (ayah == 1) "[2] note" else ""}\"}")
        }
        append("]}")
    }

    private fun jsonString(text: String): String =
        "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}

internal class MemorySettingsStore : SettingsStore {
    private val values = mutableMapOf<String, String>()
    override fun get(key: String) = values[key]
    override fun set(key: String, value: String) { values[key] = value }
}

internal class FakeTranslationHttpClient(
    private val response: suspend (String) -> String,
) : TranslationHttpClient {
    val requestedUrls = mutableListOf<String>()
    override suspend fun get(url: String): String {
        requestedUrls += url
        return response(url)
    }
}
