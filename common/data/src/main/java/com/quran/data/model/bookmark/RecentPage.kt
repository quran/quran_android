package com.quran.data.model.bookmark

import com.squareup.moshi.JsonClass
import kotlin.time.Instant

@JsonClass(generateAdapter = true)
data class RecentPage(val page: Int, val timestamp: Instant) {

  fun getCommaSeparatedValues() =
      "recent,,, $page, ${timestamp.epochSeconds},,"
}
