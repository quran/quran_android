package com.quran.labs.androidquran.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranTextVerifierTest {

  // Real 1:1 text from quran.ar.uthmani.v2.db (bundled database format).
  private val fatiha11 = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"
  private val fatiha11Hash = "8b0fe1680d46237c566f6f46c9f85f55b975d8d34bd4b1243d048c17482892e1"

  @Test
  fun `normalization matches manifest generator (NFC plus strip)`() {
    assertEquals(
      fatiha11Hash,
      QuranTextVerifier.sha256Hex(QuranTextVerifier.normalizeVerseText("  $fatiha11\n"))
    )
  }

  @Test
  fun `matching texts verify clean`() {
    val report = QuranTextVerifier.verify(
      mapOf("1:1" to fatiha11, "1:2" to "x"),
      mapOf("1:1" to fatiha11Hash, "1:2" to QuranTextVerifier.sha256Hex("x"))
    )

    assertTrue(report.isClean)
    assertEquals(2, report.passed)
    assertTrue(report.mismatches.isEmpty())
  }

  @Test
  fun `single diacritic change is detected`() {
    // ۡ (Madani jazm mark) swapped for a plain sukun: renders the same,
    // hashes differently — exactly the tamper class being guarded against.
    val tampered = fatiha11.replace("ۡ", "ْ")
    val report = QuranTextVerifier.verify(
      mapOf("1:1" to tampered),
      mapOf("1:1" to fatiha11Hash)
    )

    assertFalse(report.isClean)
    assertEquals(0, report.passed)
    assertEquals(listOf("1:1"), report.mismatches.map { it.verseKey })
    assertEquals(fatiha11Hash, report.mismatches.single().expectedHash)
  }

  @Test
  fun `missing keys are reported on both sides`() {
    val report = QuranTextVerifier.verify(
      mapOf("1:1" to fatiha11, "9:9" to "extra"),
      mapOf("1:1" to fatiha11Hash, "2:2" to "abc")
    )

    assertEquals(1, report.passed)
    assertEquals(listOf("2:2"), report.missingFromText)
    assertEquals(listOf("9:9"), report.missingFromManifest)
    assertFalse(report.isClean)
  }

  @Test
  fun `hash comparison is case-insensitive`() {
    val report = QuranTextVerifier.verify(
      mapOf("1:1" to fatiha11),
      mapOf("1:1" to fatiha11Hash.uppercase())
    )

    assertTrue(report.isClean)
    assertEquals(1, report.passed)
  }
}
