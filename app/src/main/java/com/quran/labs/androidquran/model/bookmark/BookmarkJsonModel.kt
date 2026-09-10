package com.quran.labs.androidquran.model.bookmark

import com.quran.data.model.bookmark.BookmarkData
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import dev.zacsweers.metro.Inject
import okio.BufferedSink
import okio.BufferedSource
import java.io.IOException

internal class BookmarkJsonModel @Inject constructor() {
  private val jsonAdapter: JsonAdapter<BookmarkData>

  init {
    val moshi = Moshi.Builder()
      .add(String::class.java, LegacyBackupStringAdapter)
      .build()
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

  /**
   * Old bookmark backup JSON encoded IDs as numbers. Runtime bookmark/tag IDs are strings now, so
   * this adapter lets Moshi's generated adapters keep parsing the old numeric tokens.
   */
  private object LegacyBackupStringAdapter : JsonAdapter<String>() {
    override fun fromJson(reader: JsonReader): String? {
      return when (reader.peek()) {
        JsonReader.Token.NULL -> reader.nextNull()
        JsonReader.Token.STRING -> reader.nextString()
        JsonReader.Token.NUMBER -> reader.nextLong().toString()
        else -> throw JsonDataException("Expected string or number at ${reader.path}")
      }
    }

    override fun toJson(writer: JsonWriter, value: String?) {
      if (value == null) {
        writer.nullValue()
      } else {
        writer.value(value)
      }
    }
  }
}
