package com.quran.data.model.audio

import androidx.annotation.StringRes

data class Qari(
  val id: Int,
  @StringRes val nameResource: Int,
  val url: String,
  val path: String,
  val hasGaplessAlternative: Boolean,
  val db: String? = null
) {
  val databaseName = if (db.isNullOrEmpty()) null else db
  val isGapless: Boolean = databaseName != null
}
