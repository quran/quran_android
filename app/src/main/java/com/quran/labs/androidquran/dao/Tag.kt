package com.quran.labs.androidquran.dao

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Tag(val id: Long, val name: String)
