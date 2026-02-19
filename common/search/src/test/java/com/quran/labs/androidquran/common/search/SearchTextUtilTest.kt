package com.quran.labs.androidquran.common.search

import com.google.common.truth.Truth.assertThat
import com.quran.common.search.SearchTextUtil
import org.junit.Test

class SearchTextUtilTest {

  // region asSearchableString - RTL (Arabic) path

  @Test
  fun `arabic input is returned without transliteration changes`() {
    // Arrange
    val arabicText = "\u0628\u0633\u0645" // بسم
    // Act
    val result = SearchTextUtil.asSearchableString(arabicText, isRtl = true)
    // Assert
    assertThat(result).isEqualTo(arabicText)
  }

  @Test
  fun `arabic text with diacritics has tashkeel stripped`() {
    // Arrange: بِسْمِ — baa + kasra, seen + sukoon, meem + kasra
    val withDiacritics = "\u0628\u0650\u0633\u0652\u0645\u0650"
    val withoutDiacritics = "\u0628\u0633\u0645"
    // Act
    val result = SearchTextUtil.asSearchableString(withDiacritics, isRtl = true)
    // Assert
    assertThat(result).isEqualTo(withoutDiacritics)
  }

  @Test
  fun `alef with hamza above is normalized to plain alef in rtl mode`() {
    // Arrange: أ (\u0623) → ا (\u0627)
    val withHamza = "\u0623\u0644\u0644\u0647" // أ ل ل ه
    val normalized = "\u0627\u0644\u0644\u0647" // ا ل ل ه
    // Act
    val result = SearchTextUtil.asSearchableString(withHamza, isRtl = true)
    // Assert
    assertThat(result).isEqualTo(normalized)
  }

  @Test
  fun `alef with hamza below is normalized to plain alef in rtl mode`() {
    // Arrange: إ (\u0625) → ا (\u0627)
    val withHamzaBelow = "\u0625\u0646\u0633\u0627\u0646"
    val normalized = "\u0627\u0646\u0633\u0627\u0646"
    // Act
    val result = SearchTextUtil.asSearchableString(withHamzaBelow, isRtl = true)
    // Assert
    assertThat(result).isEqualTo(normalized)
  }

  @Test
  fun `alef with madda above is normalized to plain alef in rtl mode`() {
    // Arrange: آ (\u0622) → ا (\u0627)
    val withMadda = "\u0622\u062f\u0645"
    val normalized = "\u0627\u062f\u0645"
    // Act
    val result = SearchTextUtil.asSearchableString(withMadda, isRtl = true)
    // Assert
    assertThat(result).isEqualTo(normalized)
  }

  @Test
  fun `waw with hamza is normalized to plain waw in rtl mode`() {
    // Arrange: ؤ (\u0624) → و (\u0648)
    val withWawHamza = "\u0624\u0644\u0648\u062f"
    val normalized = "\u0648\u0644\u0648\u062f"
    // Act
    val result = SearchTextUtil.asSearchableString(withWawHamza, isRtl = true)
    // Assert
    assertThat(result).isEqualTo(normalized)
  }

  @Test
  fun `empty string returns empty string in rtl mode`() {
    // Arrange
    val input = ""
    // Act
    val result = SearchTextUtil.asSearchableString(input, isRtl = true)
    // Assert
    assertThat(result).isEmpty()
  }

  @Test
  fun `empty string returns empty string in ltr mode`() {
    // Arrange
    val input = ""
    // Act
    val result = SearchTextUtil.asSearchableString(input, isRtl = false)
    // Assert
    assertThat(result).isEmpty()
  }

  // region asSearchableString - LTR (transliteration) path

  @Test
  fun `ltr input is lowercased`() {
    // Arrange
    val input = "Rahman"
    // Act
    val result = SearchTextUtil.asSearchableString(input, isRtl = false)
    // Assert
    assertThat(result).isEqualTo("rahman")
  }

  @Test
  fun `ltr input with accented characters has diacritics stripped`() {
    // Arrange: é → e after NFKD + combining char removal
    val accented = "\u00e9" // é
    // Act
    val result = SearchTextUtil.asSearchableString(accented, isRtl = false)
    // Assert
    assertThat(result).isEqualTo("e")
  }

  @Test
  fun `ltr input with apostrophe has it stripped`() {
    // Arrange
    val input = "ra'hman"
    // Act
    val result = SearchTextUtil.asSearchableString(input, isRtl = false)
    // Assert
    assertThat(result).isEqualTo("rahman")
  }

  @Test
  fun `ltr input with backtick has it stripped`() {
    // Arrange
    val input = "ra" + "`" + "hman"
    // Act
    val result = SearchTextUtil.asSearchableString(input, isRtl = false)
    // Assert
    assertThat(result).isEqualTo("rahman")
  }

  @Test
  fun `whitespace only input is preserved in ltr mode`() {
    // Arrange
    val input = "   "
    // Act
    val result = SearchTextUtil.asSearchableString(input, isRtl = false)
    // Assert
    assertThat(result).isEqualTo("   ")
  }

  @Test
  fun `whitespace only input is preserved in rtl mode`() {
    // Arrange
    val input = "   "
    // Act
    val result = SearchTextUtil.asSearchableString(input, isRtl = true)
    // Assert
    assertThat(result).isEqualTo("   ")
  }

  // region isRtl

  @Test
  fun `arabic text is detected as rtl`() {
    // Arrange
    val arabicText = "\u0628\u0633\u0645\u0627\u0644\u0644\u0647"
    // Act
    val result = SearchTextUtil.isRtl(arabicText)
    // Assert
    assertThat(result).isTrue()
  }

  @Test
  fun `latin text is detected as ltr`() {
    // Arrange
    val latinText = "bismillah"
    // Act
    val result = SearchTextUtil.isRtl(latinText)
    // Assert
    assertThat(result).isFalse()
  }

  @Test
  fun `empty string returns false for isRtl`() {
    // Arrange / Act / Assert
    assertThat(SearchTextUtil.isRtl("")).isFalse()
  }

  @Test
  fun `mixed string uses first directional character to decide`() {
    // Arrange: starts with a Latin letter so should be LTR
    val mixedStartingLatin = "a\u0628c"
    // Act
    val result = SearchTextUtil.isRtl(mixedStartingLatin)
    // Assert
    assertThat(result).isFalse()
  }

  @Test
  fun `mixed string starting with arabic is rtl`() {
    // Arrange: starts with Arabic letter
    val mixedStartingArabic = "\u0628abc"
    // Act
    val result = SearchTextUtil.isRtl(mixedStartingArabic)
    // Assert
    assertThat(result).isTrue()
  }
}
