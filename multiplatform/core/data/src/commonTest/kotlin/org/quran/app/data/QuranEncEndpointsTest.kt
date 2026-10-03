package org.quran.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QuranEncEndpointsTest {
    @Test
    fun combinesConfiguredBaseUrlAndDocumentedPaths() {
        val endpoints = QuranEncEndpoints("https://example.test/api/v1", localization = "en")

        assertEquals(
            "https://example.test/api/v1/translations/list/ur?localization=en",
            endpoints.translations("ur"),
        )
        assertEquals(
            "https://example.test/api/v1/translation/sura/english_saheeh/2",
            endpoints.surahTranslation("english_saheeh", 2),
        )
    }

    @Test
    fun rejectsInsecureHostsAndUntrustedPathSegments() {
        assertFailsWith<IllegalArgumentException> { QuranEncEndpoints("http://example.test") }
        val endpoints = QuranEncEndpoints()
        assertFailsWith<IllegalArgumentException> { endpoints.surahTranslation("../other", 1) }
        assertFailsWith<IllegalArgumentException> { endpoints.translations("en/other") }
        assertFailsWith<IllegalArgumentException> { endpoints.surahTranslation("english_saheeh", 115) }
    }
}
