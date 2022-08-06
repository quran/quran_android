package com.quran.data.model.bookmark

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RecentPage(val page: Int, val timestamp: Long) {

  fun getCommaSeparatedValues() =
      "recent,,, $page, $timestamp"
}
