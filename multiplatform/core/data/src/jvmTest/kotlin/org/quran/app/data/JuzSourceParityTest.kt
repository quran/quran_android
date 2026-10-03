package org.quran.app.data

import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import org.quran.app.model.Juz
import org.quran.app.model.VerseId

/** Compares every runtime entry against the existing authoritative XML, not a copied expectation. */
class JuzSourceParityTest {
    @Test fun runtimeMetadataMatchesEveryCanonicalXmlStartExactly() {
        val source = listOf(File("content/quran-data.xml"), File("core/data/content/quran-data.xml"))
            .firstOrNull(File::isFile) ?: fail("Canonical core/data/content/quran-data.xml is required for this test")
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
        val document = factory.newDocumentBuilder().parse(source)
        val elements = document.getElementsByTagName("juz")
        val expected = (0 until elements.length).map { index ->
            val attributes = elements.item(index).attributes
            Juz(
                attributes.getNamedItem("index").nodeValue.toInt(),
                VerseId(attributes.getNamedItem("sura").nodeValue.toInt(), attributes.getNamedItem("aya").nodeValue.toInt()),
            )
        }
        assertEquals(30, expected.size)
        assertEquals(expected, BundledQuranRepository().juzs())
    }
}
