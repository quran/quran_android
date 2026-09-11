package com.quran.labs.androidquran.util

import java.security.MessageDigest
import java.text.Normalizer

/**
 * Verifies bundled/downloaded Quran verse texts against a checksum manifest.
 *
 * Manifest format (see https://github.com/spqrxi/quranchecksum): a map of
 * `"surah:ayah"` to the SHA-256 hex of the verse text in canonical form.
 * Canonical form here mirrors the manifest generator exactly: NFC
 * normalization plus surrounding-whitespace stripping, nothing more — any
 * orthographic mapping belongs in the manifest generation step, not here, so
 * verification stays byte-exact and cannot mask real corruption.
 *
 * Pure JVM/Kotlin, no Android dependencies: safe to unit test on the JVM.
 */
object QuranTextVerifier {

  /** Canonical form used for hashing. Must match the manifest generator. */
  fun normalizeVerseText(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFC).trim()

  fun sha256Hex(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }

  data class Mismatch(val verseKey: String, val expectedHash: String, val actualHash: String)

  data class VerificationReport(
    val passed: Int,
    val mismatches: List<Mismatch>,
    val missingFromText: List<String>,
    val missingFromManifest: List<String>
  ) {
    val totalChecked: Int get() = passed + mismatches.size
    val isClean: Boolean get() = mismatches.isEmpty() && missingFromText.isEmpty()
  }

  /**
   * Verifies [verses] (`"surah:ayah"` to raw text) against [manifestHashes]
   * (`"surah:ayah"` to expected SHA-256 hex).
   */
  fun verify(
    verses: Map<String, String>,
    manifestHashes: Map<String, String>
  ): VerificationReport {
    val mismatches = mutableListOf<Mismatch>()
    var passed = 0
    for ((verseKey, text) in verses) {
      val expected = manifestHashes[verseKey]
      if (expected == null) continue
      val actual = sha256Hex(normalizeVerseText(text))
      if (actual == expected.lowercase()) {
        passed++
      } else {
        mismatches.add(Mismatch(verseKey, expected, actual))
      }
    }
    val missingFromText = (manifestHashes.keys - verses.keys).sorted()
    val missingFromManifest = (verses.keys - manifestHashes.keys).sorted()
    return VerificationReport(passed, mismatches, missingFromText, missingFromManifest)
  }
}
