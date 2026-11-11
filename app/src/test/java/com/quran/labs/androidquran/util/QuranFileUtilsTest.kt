package com.quran.labs.androidquran.util

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.fakes.FakePageProvider
import java.io.File
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class QuranFileUtilsTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Before
  fun setUp() {
    File(context.filesDir, "quran_android").deleteRecursively()
  }

  @Test
  fun `arabic database in the shared directory is left where it is`() {
    val quranFileUtils = quranFileUtils(ayahInfoDirectory = "new_madani/databases")
    val shared = quranFileUtils.getQuranDatabaseDirectory()
    shared.mkdirs()
    File(shared, ARABIC_DATABASE).writeText("shared")

    assertThat(quranFileUtils.hasArabicSearchDatabase()).isTrue()
    assertThat(File(shared, ARABIC_DATABASE).readText()).isEqualTo("shared")
  }

  @Test
  fun `arabic database is copied out of a mushaf's own directory into the shared one`() {
    // ex madani_1441_lines, whose files unpack into new_madani rather than into databases
    val quranFileUtils = quranFileUtils(ayahInfoDirectory = "new_madani/databases")
    val mushafOwnDirectory = quranFileUtils.quranAyahDatabaseDirectory
    mushafOwnDirectory.mkdirs()
    File(mushafOwnDirectory, ARABIC_DATABASE).writeText("downloaded with the pages")
    val shared = File(quranFileUtils.getQuranDatabaseDirectory(), ARABIC_DATABASE)
    assertThat(shared.exists()).isFalse()

    assertThat(quranFileUtils.hasArabicSearchDatabase()).isTrue()

    assertThat(shared.readText()).isEqualTo("downloaded with the pages")
  }

  @Test
  fun `no arabic database to copy`() {
    val quranFileUtils = quranFileUtils(ayahInfoDirectory = "new_madani/databases")

    assertThat(quranFileUtils.hasArabicSearchDatabase()).isFalse()
  }

  private fun quranFileUtils(ayahInfoDirectory: String): QuranFileUtils {
    val pageProvider = object : PageProvider by FakePageProvider() {
      override fun getDatabaseDirectoryName(): String = "databases"
      override fun getAyahInfoDirectoryName(): String = ayahInfoDirectory
    }
    val display = context.getSystemService(DisplayManager::class.java)
      .getDisplay(Display.DEFAULT_DISPLAY)!!
    val quranScreenInfo = QuranScreenInfo(
      context, display, pageProvider.getPageSizeCalculator(DisplaySize(0, 0))
    )
    return QuranFileUtils(context, pageProvider, quranScreenInfo)
  }

  private companion object {
    private val ARABIC_DATABASE = QuranDataProvider.QURAN_ARABIC_DATABASE
  }
}
