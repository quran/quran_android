package com.quran.labs.androidquran.feature.audio.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AudioUpdates(
  @Json(name = "current_revision") val currentRevision: Int,
  val updates: List<AudioSetUpdate> = emptyList())
