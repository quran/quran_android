package com.quran.labs.androidquran.common

class LocalTranslation(
  val id: Int,
  val filename: String,
  val name: String,
  val translator: String?,
  val translatorForeign: String?,
  val url: String,
  val languageCode: String?,
  val version: Int
) {

  val translatorName: String
    get() = when {
      translatorForeign != null -> translatorForeign
      translator != null -> translator
      else -> name
    }
}
