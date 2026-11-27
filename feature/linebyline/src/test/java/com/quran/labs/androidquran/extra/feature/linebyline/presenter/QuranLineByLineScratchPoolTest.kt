package com.quran.labs.androidquran.extra.feature.linebyline.presenter

import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.common.ui.core.BitmapPoolTrimRegistry
import org.junit.Test
import kotlin.concurrent.thread

class QuranLineByLineScratchPoolTest {

  @Test
  fun `decodeTempStorage provides reusable 16KB ThreadLocal buffer per thread`() {
    val first = QuranLineByLinePresenter.decodeTempStorageForTest()
    val second = QuranLineByLinePresenter.decodeTempStorageForTest()
    assertThat(first.size).isEqualTo(16 * 1024)
    assertThat(second).isSameInstanceAs(first)

    var workerBuffer: ByteArray? = null
    val t = thread {
      workerBuffer = QuranLineByLinePresenter.decodeTempStorageForTest()
    }
    t.join()
    assertThat(workerBuffer).isNotNull()
    assertThat(workerBuffer!!.size).isEqualTo(16 * 1024)
    assertThat(workerBuffer).isNotSameInstanceAs(first)
  }

  @Test
  fun `scratch pool clears cleanly via BitmapPoolTrimRegistry without background auto-release timer`() {
    QuranLineByLinePresenter.clearScratchBitmapPool()
    assertThat(QuranLineByLinePresenter.hasPooledScratchBitmapForTest()).isFalse()

    BitmapPoolTrimRegistry.clearAll()
    assertThat(QuranLineByLinePresenter.hasPooledScratchBitmapForTest()).isFalse()
  }
}
