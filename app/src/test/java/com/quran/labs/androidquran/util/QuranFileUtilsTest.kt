package com.quran.labs.androidquran.util

import android.content.Context
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.os.Environment
import android.view.Display
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.Response
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.fakes.FakePageProvider
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowEnvironment

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class QuranFileUtilsTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Before
  fun setUp() {
    QuranSettings.setInstance(null)
    File(context.filesDir, "quran_android").deleteRecursively()
  }

  @After
  fun tearDown() {
    QuranSettings.setInstance(null)
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

  @Test
  fun `recitation directories fall back to internal storage when base directory is null`() {
    ShadowEnvironment.setExternalStorageState("removed")
    QuranSettings.getInstance(context).appCustomLocation =
      Environment.getExternalStorageDirectory().absolutePath
    val quranFileUtils = quranFileUtils(ayahInfoDirectory = "new_madani/databases")
    assertThat(quranFileUtils.getQuranBaseDirectory(context)).isNull()

    val sessionsDir = quranFileUtils.recitationSessionsDirectory()
    val recordingsDir = quranFileUtils.recitationRecordingsDirectory()

    assertThat(sessionsDir).doesNotContain("nullrecitation")
    assertThat(recordingsDir).doesNotContain("nullrecitation")
    val expectedBase = File(context.filesDir, "quran_android").absolutePath
    assertThat(sessionsDir).startsWith(expectedBase)
    assertThat(recordingsDir).startsWith(expectedBase)
  }

  @Test
  fun `getPageFileName formats three digit zero padded filenames`() {
    assertThat(QuranFileUtils.getPageFileName(1)).isEqualTo("page001.png")
    assertThat(QuranFileUtils.getPageFileName(10)).isEqualTo("page010.png")
    assertThat(QuranFileUtils.getPageFileName(604)).isEqualTo("page604.png")
  }

  @Test
  fun `getImageFromWeb writes raw response bytes and returns error on repeated 404`() {
    val server = MockWebServer()
    server.start()
    try {
      val baseUrl = server.url("/").toString()
      val quranFileUtils = quranFileUtils(
        ayahInfoDirectory = "new_madani/databases",
        imagesBaseUrl = baseUrl,
      )
      val okHttpClient = OkHttpClient.Builder().build()

      // 1) 404 triggers 1 retry then returns ERROR_DOWNLOADING_ERROR
      server.enqueue(MockResponse().setResponseCode(404))
      server.enqueue(MockResponse().setResponseCode(404))
      val failedResponse = quranFileUtils.getImageFromWeb(
        okHttpClient, context, "_1024", "page001.png"
      )
      assertThat(failedResponse.isSuccessful).isFalse()
      assertThat(failedResponse.errorCode).isEqualTo(Response.ERROR_DOWNLOADING_ERROR)
      assertThat(server.requestCount).isEqualTo(2)

      // 2) 200 writes exact raw PNG bytes without recompression
      val sourceBitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
      val rawBytes = ByteArrayOutputStream().use { out ->
        sourceBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
      }
      sourceBitmap.recycle()

      server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(rawBytes)))
      val successResponse = quranFileUtils.getImageFromWeb(
        okHttpClient, context, "_1024", "page001.png"
      )
      assertThat(successResponse.isSuccessful).isTrue()
      assertThat(successResponse.bitmap).isNotNull()
      successResponse.bitmap?.recycle()

      val savedFile = File(quranFileUtils.getQuranImagesDirectory("_1024"), "page001.png")
      assertThat(savedFile.exists()).isTrue()
      assertThat(savedFile.readBytes()).isEqualTo(rawBytes)
    } finally {
      server.shutdown()
    }
  }

  private fun quranFileUtils(
    ayahInfoDirectory: String,
    imagesBaseUrl: String = "",
  ): QuranFileUtils {
    val pageProvider = object : PageProvider by FakePageProvider() {
      override fun getDatabaseDirectoryName(): String = "databases"
      override fun getAyahInfoDirectoryName(): String = ayahInfoDirectory
      override fun getImagesDirectoryName(): String = "images"
      override fun getImagesBaseUrl(): String = imagesBaseUrl
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
