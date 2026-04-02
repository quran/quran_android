package com.quran.mobile.feature.qarilist.presenter

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.SuraAyah
import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.model.download.QariDownloadInfo
import com.quran.mobile.feature.qarilist.fakes.FakeQariDownloadInfoSource
import kotlinx.coroutines.test.runTest
import org.junit.Test

class QariListPresenterTest {

  private val start = SuraAyah(1, 1)
  private val end = SuraAyah(1, 7)

  private fun makeGaplessQari(id: Int): Qari =
    Qari(id = id, nameResource = 0, url = "url/$id", path = "path/$id", hasGaplessAlternative = false, db = "gapless_$id.db")

  private fun makeGappedQari(id: Int, hasGaplessAlternative: Boolean = false): Qari =
    Qari(id = id, nameResource = 0, url = "url/$id", path = "path/$id", hasGaplessAlternative = hasGaplessAlternative)

  private fun qariToItem(qari: Qari, name: String): QariItem =
    QariItem(id = qari.id, name = name, url = qari.url, path = qari.path, hasGaplessAlternative = qari.hasGaplessAlternative, db = qari.db)

  private fun makeGaplessDownloadInfo(qari: Qari, fullyDownloaded: List<Int> = emptyList()): QariDownloadInfo =
    QariDownloadInfo.GaplessQariDownloadInfo(qari, fullyDownloaded, emptyList())

  private fun makeGappedDownloadInfo(qari: Qari, fullyDownloaded: List<Int> = emptyList()): QariDownloadInfo =
    QariDownloadInfo.GappedQariDownloadInfo(qari, fullyDownloaded, emptyList())

  @Test
  fun `initial emission produces empty list when source emits empty`() = runTest {
    val source = FakeQariDownloadInfoSource()
    val presenter = QariListPresenter(source)

    presenter.qariList(start, end) { qari -> qariToItem(qari, "Qari ${qari.id}") }.test {
      val items = awaitItem()
      assertThat(items).isEmpty()
      cancel()
    }
  }

  @Test
  fun `gapless qari without downloads appears in gapless section`() = runTest {
    val source = FakeQariDownloadInfoSource()
    val presenter = QariListPresenter(source)
    val gaplessQari = makeGaplessQari(1)
    val downloadInfo = makeGaplessDownloadInfo(gaplessQari)

    presenter.qariList(start, end) { qari -> qariToItem(qari, "Qari ${qari.id}") }.test {
      awaitItem() // initial empty
      source.emit(listOf(downloadInfo))
      val items = awaitItem()
      assertThat(items).hasSize(1)
      assertThat(items[0].qariItem.id).isEqualTo(1)
      cancel()
    }
  }

  @Test
  fun `gapless qari with all suras downloaded for range appears in ready to play section`() = runTest {
    val source = FakeQariDownloadInfoSource()
    val presenter = QariListPresenter(source)
    val gaplessQari = makeGaplessQari(2)
    val downloadInfo = makeGaplessDownloadInfo(gaplessQari, fullyDownloaded = listOf(1))

    presenter.qariList(start, end) { qari -> qariToItem(qari, "Q${qari.id}") }.test {
      awaitItem() // initial empty
      source.emit(listOf(downloadInfo))
      val items = awaitItem()
      // The qari appears in "ready to play" (downloaded sura 1 covers the range 1:1-1:7)
      // and again in the "gapless" section (gapless section is not deduped from readyToPlay).
      // qarisWithDownloads IS deduped from readyToPlay, but gapless/gapped are not.
      val ids = items.map { it.qariItem.id }
      assertThat(ids).contains(2)
      // Verify the first occurrence has the qari appearing in the ready-to-play position
      assertThat(items.first().qariItem.id).isEqualTo(2)
      cancel()
    }
  }

  @Test
  fun `multiple qaris are all included in the output`() = runTest {
    val source = FakeQariDownloadInfoSource()
    val presenter = QariListPresenter(source)
    val gapless1 = makeGaplessQari(1)
    val gapless2 = makeGaplessQari(2)
    val gapped1 = makeGappedQari(3)

    presenter.qariList(start, end) { qari -> qariToItem(qari, "Q${qari.id}") }.test {
      awaitItem()
      source.emit(listOf(
        makeGaplessDownloadInfo(gapless1),
        makeGaplessDownloadInfo(gapless2),
        makeGappedDownloadInfo(gapped1)
      ))
      val items = awaitItem()
      val ids = items.map { it.qariItem.id }
      assertThat(ids).containsAtLeast(1, 2, 3)
      cancel()
    }
  }

  @Test
  fun `updated source emission is reflected in subsequent flow values`() = runTest {
    val source = FakeQariDownloadInfoSource()
    val presenter = QariListPresenter(source)
    val qari = makeGaplessQari(5)

    presenter.qariList(start, end) { qari -> qariToItem(qari, "Q${qari.id}") }.test {
      val first = awaitItem()
      assertThat(first).isEmpty()

      source.emit(listOf(makeGaplessDownloadInfo(qari)))
      val second = awaitItem()
      assertThat(second).hasSize(1)
      assertThat(second[0].qariItem.id).isEqualTo(5)
      cancel()
    }
  }

  @Test
  fun `qari name ordering is alphabetical within each section`() = runTest {
    val source = FakeQariDownloadInfoSource()
    val presenter = QariListPresenter(source)
    val qariZ = makeGaplessQari(1)
    val qariA = makeGaplessQari(2)

    val nameMap = mapOf(1 to "Zaid", 2 to "Abul")

    presenter.qariList(start, end) { qari -> qariToItem(qari, nameMap[qari.id] ?: "Unknown") }.test {
      awaitItem()
      source.emit(listOf(
        makeGaplessDownloadInfo(qariZ),
        makeGaplessDownloadInfo(qariA)
      ))
      val items = awaitItem()
      val gaplessItems = items.filter { it.qariItem.isGapless }
      assertThat(gaplessItems).hasSize(2)
      assertThat(gaplessItems[0].qariItem.name).isEqualTo("Abul")
      assertThat(gaplessItems[1].qariItem.name).isEqualTo("Zaid")
      cancel()
    }
  }

  @Test
  fun `qari name ordering is alphabetical within gapped section`() = runTest {
    val source = FakeQariDownloadInfoSource()
    val presenter = QariListPresenter(source)
    val qariZ = makeGappedQari(1)
    val qariA = makeGappedQari(2)

    val nameMap = mapOf(1 to "Zaid", 2 to "Abul")

    presenter.qariList(start, end) { qari -> qariToItem(qari, nameMap[qari.id] ?: "Unknown") }.test {
      awaitItem()
      source.emit(listOf(
        makeGappedDownloadInfo(qariZ),
        makeGappedDownloadInfo(qariA)
      ))
      val items = awaitItem()
      val gappedItems = items.filter { !it.qariItem.isGapless }
      assertThat(gappedItems).hasSize(2)
      assertThat(gappedItems[0].qariItem.name).isEqualTo("Abul")
      assertThat(gappedItems[1].qariItem.name).isEqualTo("Zaid")
      cancel()
    }
  }

  @Test
  fun `clearing source emits empty list after non-empty state`() = runTest {
    val source = FakeQariDownloadInfoSource()
    val presenter = QariListPresenter(source)
    val qari = makeGaplessQari(1)

    presenter.qariList(start, end) { q -> qariToItem(q, "Q${q.id}") }.test {
      awaitItem()
      source.emit(listOf(makeGaplessDownloadInfo(qari)))
      val populated = awaitItem()
      assertThat(populated).isNotEmpty()

      source.emit(emptyList())
      val cleared = awaitItem()
      assertThat(cleared).isEmpty()
      cancel()
    }
  }
}
