package com.quran.labs.androidquran.model.bookmark

import com.quran.data.model.bookmark.BookmarkData
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import okio.BufferedSink
import okio.BufferedSource
import java.io.IOException
import javax.inject.Inject

internal class BookmarkJsonModel @Inject constructor() {
  private val jsonAdapter: JsonAdapter<BookmarkData>

  init {
    val moshi = Moshi.Builder().build()
    jsonAdapter = moshi.adapter(BookmarkData::class.java)
  }

  @Throws(IOException::class)
  fun toJson(sink: BufferedSink, bookmarks: BookmarkData?) {
    jsonAdapter.toJson(sink, bookmarks)
  }

  @Throws(IOException::class)
  fun toCSV(sink: BufferedSink, bookmarks: BookmarkData) {
    com.quran.labs.androidquran.model.bookmark.toCSV(sink, bookmarks)
  }

  @Throws(IOException::class)
  fun fromJson(jsonSource: BufferedSource): BookmarkData {
    return jsonAdapter.fromJson(jsonSource)!!
  }
}
