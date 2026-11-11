package com.quran.data.model

sealed interface JumpLocation

@JvmInline
value class Page(val page: Int) : JumpLocation
