package com.quran.mobile.translation.model

data class LocalTranslation(
  val id: Long = -1,
  val filename: String,
  val name: String = "",
  val translator: String? = "",
  val translatorForeign: String? = "",
  val url: String = "",
  val languageCode: String? = "",
  val version: Int = 1,
  val minimumVersion: Int = 2,
  val displayOrder: Int = -1) {

  fun resolveTranslatorName(): String {
    return when {
      !translatorForeign.isNullOrEmpty() -> translatorForeign
      !translator.isNullOrEmpty() -> translator
      name.isNotEmpty() -> name
      else -> filename
    }
  }
}
