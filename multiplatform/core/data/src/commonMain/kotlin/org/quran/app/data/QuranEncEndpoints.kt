package org.quran.app.data

/** QuranEnc API host and paths are isolated here so the content source can be replaced. */
class QuranEncEndpoints(
    baseUrl: String = DEFAULT_BASE_URL,
    private val localization: String = "en",
) {
    private val baseUrl = baseUrl.trimEnd('/')

    init {
        require(this.baseUrl.startsWith("https://")) { "QuranEnc API must use HTTPS" }
        require(localization.matches(Regex("[a-z]{2,3}"))) { "Invalid API localization" }
    }

    fun translations(languageCode: String?): String {
        require(languageCode == null || languageCode.matches(Regex("[a-z]{2,3}(-[A-Z]{2})?")))
        val languagePath = languageCode.orEmpty()
        return "$baseUrl/translations/list/$languagePath?localization=$localization"
    }

    fun surahTranslation(editionId: String, surah: Int): String {
        require(editionId.matches(Regex("[a-z0-9_]+"))) { "Invalid translation identifier" }
        require(surah in 1..114) { "Surah must be in 1..114" }
        return "$baseUrl/translation/sura/$editionId/$surah"
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://quranenc.com/api/v1"
    }
}
