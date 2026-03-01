package com.quran.labs.feature.autoquran.common

import com.google.common.truth.Truth.assertThat
import com.quran.data.model.audio.Qari
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator
import com.quran.data.source.QuranDataSource
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.util.AudioExtensionDecider
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BrowsableSurahBuilderTest {

  private lateinit var recentQariManager: RecentQariManager
  private lateinit var builder: BrowsableSurahBuilder

  @Before
  fun setUp() {
    val context = RuntimeEnvironment.getApplication()
    recentQariManager = RecentQariManager(context)
    builder = BrowsableSurahBuilder(
      appContext = context,
      pageProvider = object : PageProvider {
        override fun getDataSource(): QuranDataSource = throw NotImplementedError()
        override fun getPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator =
          throw NotImplementedError()
        override fun getImageVersion(): Int = throw NotImplementedError()
        override fun getImagesBaseUrl(): String = throw NotImplementedError()
        override fun getImagesZipBaseUrl(): String = throw NotImplementedError()
        override fun getPatchBaseUrl(): String = throw NotImplementedError()
        override fun getAyahInfoBaseUrl(): String = throw NotImplementedError()
        override fun getDatabasesBaseUrl(): String = throw NotImplementedError()
        override fun getAudioDatabasesBaseUrl(): String = throw NotImplementedError()
        override fun getAudioDirectoryName(): String = throw NotImplementedError()
        override fun getDatabaseDirectoryName(): String = throw NotImplementedError()
        override fun getAyahInfoDirectoryName(): String = throw NotImplementedError()
        override fun getImagesDirectoryName(): String = throw NotImplementedError()
        override fun getPreviewTitle(): Int = throw NotImplementedError()
        override fun getPreviewDescription(): Int = throw NotImplementedError()
        override fun getQaris(): List<Qari> = emptyList()
        override fun getDefaultQariId(): Int = throw NotImplementedError()
      },
      audioExtensionDecider = object : AudioExtensionDecider {
        override fun audioExtensionForQari(qari: Qari): String = throw NotImplementedError()
        override fun audioExtensionForQari(qariItem: QariItem): String = throw NotImplementedError()
        override fun allowedAudioExtensions(qari: Qari): List<String> = throw NotImplementedError()
        override fun allowedAudioExtensions(qariItem: QariItem): List<String> =
          throw NotImplementedError()
      },
      qariArtworkProvider = QariArtworkProvider(context),
      recentQariManager = recentQariManager,
    )
  }

  @Test
  fun `root has 1 child when recents are empty`() = runTest {
    val children = builder.children(BrowsableSurahBuilder.ROOT_ID)
    assertThat(children).hasSize(1)
  }

  @Test
  fun `root has 2 children when recents are non-empty`() = runTest {
    recentQariManager.recordQari(qariId = 1, sura = 36)
    val children = builder.children(BrowsableSurahBuilder.ROOT_ID)
    assertThat(children).hasSize(2)
  }
}
