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
  private lateinit var qaris: List<Qari>

  @Before
  fun setUp() {
    val context = RuntimeEnvironment.getApplication()
    recentQariManager = RecentQariManager(context)
    qaris = listOf(
      Qari(
        id = 1,
        nameResource = android.R.string.copy,
        url = "https://example.com/1/",
        path = "qari_1",
        hasGaplessAlternative = false,
        db = "gapless.sqlite",
      )
    )
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
        override fun getQaris(): List<Qari> = qaris
        override fun getDefaultQariId(): Int = throw NotImplementedError()
      },
      audioExtensionDecider = object : AudioExtensionDecider {
        override fun audioExtensionForQari(qari: Qari): String = "mp3"
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

  @Test
  fun `root hides recents when stored entries are stale`() = runTest {
    recentQariManager.recordQari(qariId = 99, sura = 36)

    val children = builder.children(BrowsableSurahBuilder.ROOT_ID)
    val recentChildren = builder.children(BrowsableSurahBuilder.RECENT_ID)

    assertThat(children).hasSize(1)
    assertThat(recentChildren).isEmpty()
  }

  @Test
  fun `child returns browsable parents`() = runTest {
    val recentItem = builder.child(BrowsableSurahBuilder.RECENT_ID)
    val qariRootItem = builder.child(BrowsableSurahBuilder.QARI_ID)
    val qariItem = builder.child("quran_1")

    assertThat(recentItem?.mediaMetadata?.isBrowsable).isTrue()
    assertThat(qariRootItem?.mediaMetadata?.isBrowsable).isTrue()
    assertThat(qariItem?.mediaMetadata?.isBrowsable).isTrue()
  }
}
