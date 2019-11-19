package com.quran.labs.androidquran.dao.translation

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TranslationList(@field:Json(name = "data") val translations: List<Translation>)
