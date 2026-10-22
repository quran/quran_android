package com.quran.mobile.feature.memorize.prompt

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.memorize.tracker.WordId
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@Config(sdk = [33])
@RunWith(RobolectricTestRunner::class)
class WordTimingsTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private lateinit var timings: WordTimings

  @Before
  fun setUp() {
    val file = File(context.noBackupFilesDir, "memorize/test_timings.db").apply {
      parentFile?.mkdirs()
      delete()
    }
    SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
      db.execSQL("CREATE TABLE timings(sura INTEGER, ayah INTEGER, time INTEGER, words TEXT)")
      db.execSQL("CREATE TABLE audio_files(sura INTEGER PRIMARY KEY, size INTEGER, md5 TEXT)")
      db.execSQL("INSERT INTO timings VALUES (112, 1, 5000, '1:5100:5600,2:5600:6000,3:6000:6900,4:6900:7800')")
      db.execSQL("INSERT INTO timings VALUES (112, 2, 9000, '1:9100:9900,2:9900:11000')")
      db.execSQL("INSERT INTO audio_files VALUES (112, 123456, '')")
    }
    timings = WordTimings.fromFile(file)
  }

  @Test
  fun `a single word plays from its start to its end`() {
    assertThat(timings.span(listOf(WordId(112, 1, 3)))).isEqualTo(WordTimings.Span(6000, 6900))
  }

  @Test
  fun `consecutive words play as one clip, also across ayat`() {
    val span = timings.span(listOf(WordId(112, 1, 4), WordId(112, 2, 1)))

    assertThat(span).isEqualTo(WordTimings.Span(6900, 9900))
  }

  @Test
  fun `unknown words have no span`() {
    assertThat(timings.span(listOf(WordId(112, 3, 1)))).isNull()
    assertThat(timings.span(listOf(WordId(112, 1, 9)))).isNull()
  }

  @Test
  fun `words from different suras can't be played as one clip`() {
    assertThat(timings.span(listOf(WordId(112, 2, 2), WordId(113, 1, 1)))).isNull()
  }

  @Test
  fun `knows the size of the file the timings were made for`() {
    assertThat(timings.expectedFileSize(112)).isEqualTo(123456)
    assertThat(timings.expectedFileSize(113)).isNull()
  }
}
