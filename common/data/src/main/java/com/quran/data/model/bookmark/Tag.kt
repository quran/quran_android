package com.quran.data.model.bookmark

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Tag @JvmOverloads constructor(
  val id: String,
  val name: String,
  val isSystem: Boolean = false,
  val isDefault: Boolean = false
)
