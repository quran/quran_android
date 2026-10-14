package com.quran.mobile.bookmark.migration

interface LegacyBookmarksDataSource {
  fun snapshot(): LegacyBookmarksSnapshot
}
