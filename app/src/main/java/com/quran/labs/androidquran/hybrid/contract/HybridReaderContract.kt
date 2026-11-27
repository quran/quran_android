package com.quran.labs.androidquran.hybrid.contract

import android.content.Intent

object HybridReaderContract {
  const val EXTRA_INITIAL_STUDY_TAB: String = "hybrid_initial_study_tab"
  const val EXTRA_AUTO_PLAY_AYAH: String = "hybrid_auto_play_ayah"
  const val EXTRA_HIGHLIGHT_SURA: String = "highlightSura"
  const val EXTRA_HIGHLIGHT_AYAH: String = "highlightAyah"

  enum class StudyTab(val extraValue: String) {
    RECITATION("recitation"),
    TAFSIR("tafsir"),
    APPEARANCE("appearance");

    companion object {
      val DRAWER_TABS: List<StudyTab> = listOf(RECITATION, TAFSIR)

      fun fromExtra(value: String?): StudyTab? =
        entries.firstOrNull { it.extraValue == value }
    }
  }

  fun applyToIntent(
    intent: Intent,
    sura: Int? = null,
    ayah: Int? = null,
    initialStudyTab: StudyTab? = null,
    autoPlayAyah: Boolean = false
  ): Intent = intent.apply {
    if (sura != null) {
      putExtra(EXTRA_HIGHLIGHT_SURA, sura)
    }
    if (ayah != null) {
      putExtra(EXTRA_HIGHLIGHT_AYAH, ayah)
    }
    if (initialStudyTab != null) {
      putExtra(EXTRA_INITIAL_STUDY_TAB, initialStudyTab.extraValue)
    }
    if (autoPlayAyah) {
      putExtra(EXTRA_AUTO_PLAY_AYAH, true)
    }
  }
}
