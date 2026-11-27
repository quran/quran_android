package com.quran.mobile.feature.qarilist.ui

import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.mobile.feature.qarilist.R
import com.quran.mobile.feature.qarilist.filterQaris
import com.quran.mobile.feature.qarilist.model.QariUiModel
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class QariListFilterAndKeyTest {

  private val sampleQaris = persistentListOf(
    QariUiModel(
      qariItem = QariItem(id = 1, name = "Mishary Alafasy", url = "u1", path = "p1", hasGaplessAlternative = false, db = "db1"),
      sectionHeader = R.string.qarilist_ready_to_play
    ),
    QariUiModel(
      qariItem = QariItem(id = 1, name = "Mishary Alafasy", url = "u1", path = "p1", hasGaplessAlternative = false, db = "db1"),
      sectionHeader = R.string.qarilist_gapless
    ),
    QariUiModel(
      qariItem = QariItem(id = 2, name = "AbdulBaset AbdusSamad", url = "u2", path = "p2", hasGaplessAlternative = false, db = "db2"),
      sectionHeader = R.string.qarilist_gapless
    )
  )

  @Test
  fun `qariItemKey produces distinct composite keys when same qari appears in multiple sections`() {
    val readyKey = qariItemKey(R.string.qarilist_ready_to_play, 1)
    val gaplessKey = qariItemKey(R.string.qarilist_gapless, 1)
    val otherGaplessKey = qariItemKey(R.string.qarilist_gapless, 2)

    assertThat(readyKey).isNotEqualTo(gaplessKey)
    assertThat(gaplessKey).isNotEqualTo(otherGaplessKey)
  }

  @Test
  fun `filterQaris returns all items on blank query`() {
    assertThat(filterQaris(sampleQaris, "")).hasSize(3)
    assertThat(filterQaris(sampleQaris, "   ")).hasSize(3)
  }

  @Test
  fun `filterQaris filters case-insensitively and returns empty when no match`() {
    val matched = filterQaris(sampleQaris, "baset")
    assertThat(matched.map { it.qariItem.id }).containsExactly(2)

    val noMatch = filterQaris(sampleQaris, "nonexistent-qari")
    assertThat(noMatch).isEmpty()
  }

  @Test
  fun `filterQaris normalizes Arabic alef, ta-marbuta, and alef-maqsura variants`() {
    val arabicQaris = persistentListOf(
      QariUiModel(
        qariItem = QariItem(id = 10, name = "أحمد نعينع", url = "u10", path = "p10", hasGaplessAlternative = false, db = "db10"),
        sectionHeader = R.string.qarilist_gapless
      ),
      QariUiModel(
        qariItem = QariItem(id = 11, name = "إبراهيم الأخضر", url = "u11", path = "p11", hasGaplessAlternative = false, db = "db11"),
        sectionHeader = R.string.qarilist_gapless
      ),
      QariUiModel(
        qariItem = QariItem(id = 12, name = "خليفة الطنيجي", url = "u12", path = "p12", hasGaplessAlternative = false, db = "db12"),
        sectionHeader = R.string.qarilist_gapless
      ),
      QariUiModel(
        qariItem = QariItem(id = 13, name = "مصطفي إسماعيل", url = "u13", path = "p13", hasGaplessAlternative = false, db = "db13"),
        sectionHeader = R.string.qarilist_gapless
      )
    )

    assertThat(filterQaris(arabicQaris, "احمد").map { it.qariItem.id }).containsExactly(10)
    assertThat(filterQaris(arabicQaris, "ابراهيم").map { it.qariItem.id }).containsExactly(11)
    assertThat(filterQaris(arabicQaris, "خليفه").map { it.qariItem.id }).containsExactly(12)
    assertThat(filterQaris(arabicQaris, "مصطفى").map { it.qariItem.id }).containsExactly(13)
  }
}
