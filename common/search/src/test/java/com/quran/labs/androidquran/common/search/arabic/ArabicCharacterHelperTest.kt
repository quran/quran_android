package com.quran.labs.androidquran.common.search.arabic

import com.google.common.truth.Truth.assertThat
import com.quran.common.search.arabic.ArabicCharacterHelper
import org.junit.Test

class ArabicCharacterHelperTest {

  // region generateRegex – characters with lookup table entries

  @Test
  fun `plain alef generates character class including alef variants`() {
    // Arrange: ا (\u0627) should map to آأإاﻯ
    val query = "\u0627"
    // Act
    val regex = ArabicCharacterHelper.generateRegex(query)
    // Assert: result must be a character class containing all alef variants
    assertThat(regex).contains("[")
    assertThat(regex).contains("]")
    assertThat(regex).contains("\u0622") // آ
    assertThat(regex).contains("\u0623") // أ
    assertThat(regex).contains("\u0625") // إ
    assertThat(regex).contains("\u0627") // ا
    assertThat(regex).contains("\u0649") // ى
  }

  @Test
  fun `waw generates character class including waw with hamza`() {
    // Arrange: و (\u0648) should map to وؤ
    val query = "\u0648"
    // Act
    val regex = ArabicCharacterHelper.generateRegex(query)
    // Assert
    assertThat(regex).contains("\u0648") // و
    assertThat(regex).contains("\u0624") // ؤ
  }

  @Test
  fun `alef with hamza above generates class including hamza variants`() {
    // Arrange: أ (\u0623) maps to ﺀأؤئ and ا
    val query = "\u0623"
    // Act
    val regex = ArabicCharacterHelper.generateRegex(query)
    // Assert
    assertThat(regex).contains("\u0621") // ء
    assertThat(regex).contains("\u0623") // أ
    assertThat(regex).contains("\u0624") // ؤ
    assertThat(regex).contains("\u0626") // ئ
    assertThat(regex).contains("\u0627") // ا
  }

  @Test
  fun `hamza alone generates class including alef variants`() {
    // Arrange: ء (\u0621) maps to ءأؤ and ا
    val query = "\u0621"
    // Act
    val regex = ArabicCharacterHelper.generateRegex(query)
    // Assert
    assertThat(regex).contains("\u0621") // ء
    assertThat(regex).contains("\u0623") // أ
    assertThat(regex).contains("\u0627") // ا
  }

  @Test
  fun `ta generates character class including taa marbuta`() {
    // Arrange: ت (\u062a) maps to تة
    val query = "\u062a"
    // Act
    val regex = ArabicCharacterHelper.generateRegex(query)
    // Assert
    assertThat(regex).contains("\u062a") // ت
    assertThat(regex).contains("\u0629") // ة
  }

  @Test
  fun `taa marbuta generates class including ta and haa`() {
    // Arrange: ة (\u0629) maps to ةتﻫ
    val query = "\u0629"
    // Act
    val regex = ArabicCharacterHelper.generateRegex(query)
    // Assert
    assertThat(regex).contains("\u0629") // ة
    assertThat(regex).contains("\u062a") // ت
    assertThat(regex).contains("\u0647") // ه
  }

  @Test
  fun `haa generates character class including taa marbuta`() {
    // Arrange: ه (\u0647) maps to ةه
    val query = "\u0647"
    // Act
    val regex = ArabicCharacterHelper.generateRegex(query)
    // Assert
    assertThat(regex).contains("\u0647") // ه
    assertThat(regex).contains("\u0629") // ة
  }

  @Test
  fun `alef maqsura generates class including ya`() {
    // Arrange: ى (\u0649) maps to ىي
    val query = "\u0649"
    // Act
    val regex = ArabicCharacterHelper.generateRegex(query)
    // Assert
    assertThat(regex).contains("\u0649") // ى
    assertThat(regex).contains("\u064a") // ي
  }

  @Test
  fun `ya with hamza generates class including alef maqsura and ya`() {
    // Arrange: ئ (\u0626) maps to ئىي
    val query = "\u0626"
    // Act
    val regex = ArabicCharacterHelper.generateRegex(query)
    // Assert
    assertThat(regex).contains("\u0626") // ئ
    assertThat(regex).contains("\u0649") // ى
    assertThat(regex).contains("\u064a") // ي
  }

  // region generateRegex – characters NOT in lookup table

  @Test
  fun `character not in lookup table is appended as literal`() {
    // Arrange: ب (\u0628) has no entry in the lookup table
    val query = "\u0628"
    // Act
    val regex = ArabicCharacterHelper.generateRegex(query)
    // Assert: should be a bare character, no brackets
    assertThat(regex).isEqualTo("\u0628")
  }

  @Test
  fun `empty query produces empty regex`() {
    // Arrange
    val query = ""
    // Act
    val regex = ArabicCharacterHelper.generateRegex(query)
    // Assert
    assertThat(regex).isEmpty()
  }

  @Test
  fun `banned parenthesis characters are omitted from regex`() {
    // Arrange: parentheses are in bannedChars
    val query = "(\u0628)"
    // Act
    val regex = ArabicCharacterHelper.generateRegex(query)
    // Assert: only ب should be present; no parentheses
    assertThat(regex).isEqualTo("\u0628")
    assertThat(regex).doesNotContain("(")
    assertThat(regex).doesNotContain(")")
  }

  @Test
  fun `multi-character query combining lookup and literal chars`() {
    // Arrange: ب (\u0628, literal) + ا (\u0627, lookup) + ب (\u0628, literal)
    val query = "\u0628\u0627\u0628"
    // Act
    val regex = ArabicCharacterHelper.generateRegex(query)
    // Assert: must start and end with bare ب and have a character class in the middle
    assertThat(regex).startsWith("\u0628")
    assertThat(regex).endsWith("\u0628")
    assertThat(regex).contains("[")
  }

  @Test
  fun `generated regex matches original character`() {
    // Arrange: ا should produce a regex that matches ا itself
    val query = "\u0627"
    // Act
    val regex = ArabicCharacterHelper.generateRegex(query)
    val pattern = Regex(regex)
    // Assert
    assertThat(pattern.containsMatchIn("\u0627")).isTrue()
  }

  @Test
  fun `generated regex for alef matches alef with hamza`() {
    // Arrange: querying ا should also match أ
    val query = "\u0627"
    // Act
    val regex = ArabicCharacterHelper.generateRegex(query)
    val pattern = Regex(regex)
    // Assert
    assertThat(pattern.containsMatchIn("\u0623")).isTrue()
  }
}
