package com.quran.labs.androidquran.hybrid.search

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

object SearchTestTags {
  const val SEARCH_INPUT = "hybrid_search_input"
  const val HIT_COUNT_BADGE = "hybrid_search_hit_count_badge"
  const val CLEAR_BUTTON = "hybrid_search_clear_button"
  const val RUST_SPEED_BADGE = "hybrid_search_rust_speed_badge"
  const val SCOPE_AYAHS = "hybrid_search_scope_ayahs"
  const val SCOPE_SURAS = "hybrid_search_scope_suras"
  const val SCOPE_TAFSIR = "hybrid_search_scope_tafsir"
  const val RESULTS_LIST = "hybrid_search_results_list"
  const val WARNING_TEXT = "hybrid_search_warning_text"
  const val ACTION_CTA_BUTTON = "hybrid_search_action_cta_button"
  const val NO_RESULTS_TEXT = "hybrid_search_no_results_text"

  fun ayahCard(sura: Int, ayah: Int): String = "hybrid_ayah_card_${sura}_$ayah"
  fun ayahText(sura: Int, ayah: Int): String = "hybrid_ayah_text_${sura}_$ayah"
  fun listenButton(sura: Int, ayah: Int): String = "hybrid_listen_${sura}_$ayah"
  fun bookmarkButton(sura: Int, ayah: Int): String = "hybrid_bookmark_${sura}_$ayah"
  fun openPageButton(sura: Int, ayah: Int): String = "hybrid_open_page_${sura}_$ayah"
  fun suraCard(sura: Int): String = "hybrid_sura_card_$sura"
  fun tafsirCard(sura: Int, ayah: Int): String = "hybrid_tafsir_card_${sura}_$ayah"
  fun recentChip(query: String): String = "hybrid_recent_chip_$query"

  val HighlightRangesKey = SemanticsPropertyKey<List<HighlightSpanRange>>("HybridHighlightRanges")
  var SemanticsPropertyReceiver.highlightRanges by HighlightRangesKey
}
