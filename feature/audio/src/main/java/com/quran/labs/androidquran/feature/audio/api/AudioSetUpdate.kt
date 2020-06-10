package com.quran.labs.androidquran.feature.audio.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AudioSetUpdate(
  val path: String,
  @Json(name = "database_version") val databaseVersion: Int? = null,
  val files: List<AudioFileUpdate> = emptyList())
