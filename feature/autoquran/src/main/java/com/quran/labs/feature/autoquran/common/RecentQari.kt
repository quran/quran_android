package com.quran.labs.feature.autoquran.common

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RecentQari(
  @param:Json(name = "qari_id") val qariId: Int,
  @param:Json(name = "last_sura") val lastSura: Int,
  val timestamp: Long
)
