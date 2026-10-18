package com.quran.data.model.bookmark

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Tag @JvmOverloads constructor(
  val id: String,
  val name: String,
  val isSystem: Boolean = false,
  val isDefault: Boolean = false,
  // users can't rename or delete it: system collections, and the color collections older Quran.com
  // apps created. Not part of backups.
  @Transient val isReadOnly: Boolean = isSystem
)
