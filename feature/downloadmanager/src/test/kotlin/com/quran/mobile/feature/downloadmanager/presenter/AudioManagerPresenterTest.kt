package com.quran.mobile.feature.downloadmanager.presenter

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.model.download.QariDownloadInfo
import com.quran.mobile.feature.downloadmanager.fakes.FakeQariDownloadInfoSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AudioManagerPresenterTest {

  private val source = FakeQariDownloadInfoSource()
  private val presenter = AudioManagerPresenter(source)

  private fun makeGaplessQari(id: Int, db: String = "db_$id") =
    Qari(id, 0, "url/$id", path = "path/$id", hasGaplessAlternative = false, db = db)

  private fun makeGappedQari(id: Int) =
    Qari(id, 0, "url/$id", path = "path/$id", hasGaplessAlternative = false)

  private fun qariToItem(qari: Qari, name: String) =
    QariItem(qari.id, name, qari.url, path = qari.path, hasGaplessAlternative = qari.hasGaplessAlternative, db = qari.db)

  private fun makeGaplessInfo(qari: Qari, fullyDownloaded: List<Int>) =
    QariDownloadInfo.GaplessQariDownloadInfo(qari, fullyDownloaded, emptyList())

  private fun makeGappedInfo(qari: Qari, fullyDownloaded: List<Int>) =
    QariDownloadInfo.GappedQariDownloadInfo(qari, fullyDownloaded, emptyList())

  private val defaultLambda: (Qari) -> QariItem = { qari ->
    qariToItem(qari, "Qari ${qari.id}")
  }

  @Test
  fun `returns empty list when no qaris are downloaded`() = runTest {
    source.emitFiltered(emptyList())
    presenter.downloadedShuyookh(defaultLambda).test {
      val result = awaitItem()
      assertThat(result).isEmpty()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `maps qari download info to DownloadedSheikhUiModel`() = runTest {
    val qari = makeGaplessQari(1)
    source.emitFiltered(listOf(makeGaplessInfo(qari, listOf(1, 2, 3))))

    presenter.downloadedShuyookh(defaultLambda).test {
      val result = awaitItem()
      assertThat(result).hasSize(1)
      assertThat(result[0].qariItem.id).isEqualTo(1)
      assertThat(result[0].downloadedSuras).isEqualTo(3)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `sorts results alphabetically by qari name`() = runTest {
    val qari1 = makeGaplessQari(1)
    val qari2 = makeGaplessQari(2)
    source.emitFiltered(listOf(makeGaplessInfo(qari1, emptyList()), makeGaplessInfo(qari2, emptyList())))

    val lambda: (Qari) -> QariItem = { qari ->
      when (qari.id) {
        1 -> qariToItem(qari, "Zaid")
        2 -> qariToItem(qari, "Abul")
        else -> qariToItem(qari, "Unknown")
      }
    }

    presenter.downloadedShuyookh(lambda).test {
      val result = awaitItem()
      assertThat(result[0].qariItem.name).isEqualTo("Abul")
      assertThat(result[1].qariItem.name).isEqualTo("Zaid")
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `applies lambda to transform Qari to QariItem`() = runTest {
    val qari = makeGaplessQari(1)
    source.emitFiltered(listOf(makeGaplessInfo(qari, emptyList())))

    val lambda: (Qari) -> QariItem = { q ->
      qariToItem(q, "Custom Name")
    }

    presenter.downloadedShuyookh(lambda).test {
      val result = awaitItem()
      assertThat(result[0].qariItem.name).isEqualTo("Custom Name")
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `emits updated list when upstream flow changes`() = runTest {
    val qari = makeGaplessQari(1)

    presenter.downloadedShuyookh(defaultLambda).test {
      val first = awaitItem()
      assertThat(first).isEmpty()

      source.emitFiltered(listOf(makeGaplessInfo(qari, listOf(1))))
      val second = awaitItem()
      assertThat(second).hasSize(1)

      source.emitFiltered(emptyList())
      val third = awaitItem()
      assertThat(third).isEmpty()

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `handles multiple qaris with varying download counts`() = runTest {
    val qari1 = makeGaplessQari(1)
    val qari2 = makeGaplessQari(2)
    val qari3 = makeGaplessQari(3)
    source.emitFiltered(
      listOf(
        makeGaplessInfo(qari1, emptyList()),
        makeGaplessInfo(qari2, listOf(1, 2, 3, 4, 5)),
        makeGaplessInfo(qari3, (1..114).toList())
      )
    )

    presenter.downloadedShuyookh(defaultLambda).test {
      val result = awaitItem()
      assertThat(result).hasSize(3)
      val countsByQariId = result.associate { it.qariItem.id to it.downloadedSuras }
      assertThat(countsByQariId[1]).isEqualTo(0)
      assertThat(countsByQariId[2]).isEqualTo(5)
      assertThat(countsByQariId[3]).isEqualTo(114)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `result is an ImmutableList`() = runTest {
    source.emitFiltered(emptyList())
    presenter.downloadedShuyookh(defaultLambda).test {
      val result = awaitItem()
      assertThat(result).isInstanceOf(ImmutableList::class.java)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `counts only fully downloaded suras`() = runTest {
    val qari = makeGaplessQari(1)
    val info = QariDownloadInfo.GaplessQariDownloadInfo(
      qari,
      fullyDownloadedSuras = listOf(1, 2),
      partiallyDownloadedSuras = listOf(3, 4, 5)
    )
    source.emitFiltered(listOf(info))

    presenter.downloadedShuyookh(defaultLambda).test {
      val result = awaitItem()
      assertThat(result[0].downloadedSuras).isEqualTo(2)
      cancelAndIgnoreRemainingEvents()
    }
  }
}
