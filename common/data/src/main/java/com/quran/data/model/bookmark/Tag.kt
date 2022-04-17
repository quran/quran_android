package com.quran.data.model.bookmark

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Tag(val id: Long, val name: String) {

  fun getCommaSeparatedNames() =
      "id, name"

  fun getCommaSeparatedValues() =
      "$id, $name"
}
