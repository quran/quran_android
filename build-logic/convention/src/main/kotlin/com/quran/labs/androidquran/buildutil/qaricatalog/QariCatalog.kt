package com.quran.labs.androidquran.buildutil.qaricatalog

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class QariCatalog(
  val schemaVersion: Int,
  val qaris: List<QariCatalogEntry>,
  val resourcePackage: String? = null
)

@JsonClass(generateAdapter = true)
internal data class QariCatalogEntry(
  val id: Int,
  val nameResource: String,
  val url: String,
  val path: String,
  val hasGaplessAlternative: Boolean,
  val opusUrl: String? = null,
  val db: String? = null
)
