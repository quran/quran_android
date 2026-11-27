package com.quran.mobile.mapper.imlaei.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.SuraAyah
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class ImlaeiUthmaniMapperTest {

  private lateinit var driver: JdbcSqliteDriver
  private lateinit var database: ImlaeiUthmaniMappingDatabase
  private lateinit var mapper: ImlaeiUthmaniMapper

  @Before
  fun setup() {
    driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    ImlaeiUthmaniMappingDatabase.Schema.create(driver)
    database = ImlaeiUthmaniMappingDatabase(driver)
    mapper = ImlaeiUthmaniMapper.forTest { database }

    insertRow(1, 6, 1, "10")
    insertRow(1, 7, 1, "11")
    insertRow(1, 7, 2, "12,13")
    insertRow(2, 1, 1, "1")
    insertRow(2, 2, 1, "2")
    insertRow(3, 1, 1, "1")
    insertRow(3, 2, 1, "2")
    insertRow(3, 3, 1, "3")
  }

  @After
  fun tearDown() {
    driver.close()
  }

  @Test
  fun `mapWords returns empty list when database is null`() = runTest {
    val nullMapper = ImlaeiUthmaniMapper.forTest { null }

    val result = nullMapper.mapWords(SuraAyah(1, 1), SuraAyah(1, 7))

    assertThat(result).isEmpty()
  }

  @Test
  fun `mapWords returns alignments for single sura ayah range`() = runTest {
    val result = mapper.mapWords(SuraAyah(3, 1), SuraAyah(3, 2))

    assertThat(result).containsExactly(
      WordAlignment(3, 1, 1, listOf(1)),
      WordAlignment(3, 2, 1, listOf(2))
    ).inOrder()
  }

  @Test
  fun `mapWords returns ordered alignments across multi-sura range`() = runTest {
    val result = mapper.mapWords(SuraAyah(1, 7), SuraAyah(3, 2))

    assertThat(result).containsExactly(
      WordAlignment(1, 7, 1, listOf(11)),
      WordAlignment(1, 7, 2, listOf(12, 13)),
      WordAlignment(2, 1, 1, listOf(1)),
      WordAlignment(2, 2, 1, listOf(2)),
      WordAlignment(3, 1, 1, listOf(1)),
      WordAlignment(3, 2, 1, listOf(2))
    ).inOrder()
  }

  @Test
  fun `mapWords excludes alignments outside multi-sura range bounds`() = runTest {
    val result = mapper.mapWords(SuraAyah(1, 7), SuraAyah(3, 1))

    assertThat(result.map { Triple(it.sura, it.ayah, it.audioWordNumber) }).containsExactly(
      Triple(1, 7, 1),
      Triple(1, 7, 2),
      Triple(2, 1, 1),
      Triple(2, 2, 1),
      Triple(3, 1, 1)
    ).inOrder()
  }

  private fun insertRow(sura: Int, ayah: Int, audioWordNumber: Int, glyphWordNumbers: String) {
    driver.execute(
      identifier = null,
      sql = "INSERT INTO word_alignment(sura, ayah, audio_word_number, glyph_word_numbers) VALUES (?, ?, ?, ?)",
      parameters = 4
    ) {
      bindLong(0, sura.toLong())
      bindLong(1, ayah.toLong())
      bindLong(2, audioWordNumber.toLong())
      bindString(3, glyphWordNumbers)
    }
  }
}
