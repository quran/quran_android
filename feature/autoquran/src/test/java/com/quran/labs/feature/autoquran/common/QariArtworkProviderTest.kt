package com.quran.labs.feature.autoquran.common

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.audio.Qari
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class QariArtworkProviderTest {

  private lateinit var context: Context
  private lateinit var provider: QariArtworkProvider

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    provider = QariArtworkProvider(context)
  }

  @Test
  fun `artworkUriFor builds stable qari content uri`() {
    val qari = Qari(
      id = 7,
      nameResource = android.R.string.copy,
      url = "https://example.com/7/",
      path = "qari_7",
      hasGaplessAlternative = false,
      db = null,
    )

    val uri = provider.artworkUriFor(qari)

    assertThat(uri.scheme).isEqualTo("content")
    assertThat(uri.authority).isEqualTo(QariArtworkProvider.authority(context))
    assertThat(uri.pathSegments).containsExactly("artwork", "qari", "7").inOrder()
  }

  @Test
  fun `suraArtworkUriFor builds stable sura content uri`() {
    val qari = Qari(
      id = 7,
      nameResource = android.R.string.copy,
      url = "https://example.com/7/",
      path = "qari_7",
      hasGaplessAlternative = false,
      db = null,
    )

    val uri = provider.suraArtworkUriFor(qari, 36)

    assertThat(uri.scheme).isEqualTo("content")
    assertThat(uri.authority).isEqualTo(QariArtworkProvider.authority(context))
    assertThat(uri.pathSegments).containsExactly("artwork", "qari", "7", "sura", "36").inOrder()
  }

  @Test
  fun `generateQariPng produces non-empty output bytes`() {
    val bytes = provider.generateQariPng(qariId = 1, displayName = "Mishary Alafasy")

    assertThat(bytes).isNotNull()
    assertThat(bytes!!.isNotEmpty()).isTrue()
  }

  @Test
  fun `generateQariPng handles blank and symbol-only names without error`() {
    val blankBytes = provider.generateQariPng(qariId = 2, displayName = "   ")
    val symbolBytes = provider.generateQariPng(qariId = 3, displayName = "---")

    assertThat(blankBytes).isNotNull()
    assertThat(blankBytes!!.isNotEmpty()).isTrue()
    assertThat(symbolBytes).isNotNull()
    assertThat(symbolBytes!!.isNotEmpty()).isTrue()
  }

  @Test
  fun `generateSuraPng renders non-empty output bytes for sura`() {
    val bytes = provider.generateSuraPng(qariId = 4, sura = 1)

    assertThat(bytes).isNotNull()
    assertThat(bytes!!.isNotEmpty()).isTrue()
  }

  @Test
  fun `repeated generateQariPng calls succeed deterministically`() {
    val first = provider.generateQariPng(qariId = 5, displayName = "Abdul Basit")
    assertThat(first).isNotNull()

    repeat(20) { index ->
      val result = provider.generateQariPng(qariId = index, displayName = "Qari $index")
      assertThat(result).isNotNull()
      assertThat(result!!.isNotEmpty()).isTrue()
    }

    val again = provider.generateQariPng(qariId = 5, displayName = "Abdul Basit")
    assertThat(again).isEqualTo(first)
  }
}
