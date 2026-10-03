package org.quran.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import org.quran.app.model.TextDirection

class QuranEncEditionParserTest {
    private val parser = QuranEncTranslationParser()

    @Test
    fun retainsEditionKeyTranslatorVersionAndDirection() {
        val edition = parser.editions(TranslationTestFixtures.CATALOG_JSON).single()

        assertEquals("english_saheeh", edition.id)
        assertEquals("en", edition.languageCode)
        assertEquals("Saheeh International", edition.translator)
        assertEquals("1.1.2", edition.version)
        assertEquals(TextDirection.LTR, edition.direction)
    }

    @Test
    fun rejectsUnsafeOrDuplicateEditionIdentifiers() {
        val unsafeId = TranslationTestFixtures.CATALOG_JSON.replace("english_saheeh", "../unsafe")
        assertFails { parser.editions(unsafeId) }
        val duplicate = TranslationTestFixtures.CATALOG_JSON.replace(
            "}]}",
            "},{\"key\":\"english_saheeh\",\"language_iso_code\":\"en\",\"version\":\"1\",\"last_update\":1,\"title\":\"Duplicate\",\"description\":\"Duplicate\"}]}",
        )
        assertFails { parser.editions(duplicate) }
    }
}
