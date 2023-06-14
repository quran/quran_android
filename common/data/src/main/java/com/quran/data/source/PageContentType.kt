package com.quran.data.source

sealed class PageContentType {
  object Image : PageContentType()
  data class Line(val ratio: Float, val allowOverlapOfLines: Boolean): PageContentType()
}
