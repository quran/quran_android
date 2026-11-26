package com.quran.labs.androidquran.model.bookmark

import com.quran.data.model.bookmark.BookmarkData
import com.quran.mobile.bookmark.time.legacyTimestampMillis
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import dev.zacsweers.metro.Inject
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.io.IOException
import kotlin.time.Instant

internal class BookmarkJsonModel @Inject constructor() {
  private val jsonAdapter: JsonAdapter<BookmarkData>

  init {
    val moshi = Moshi.Builder()
      .add(String::class.java, LegacyBackupStringAdapter)
      .add(Instant::class.java, LegacyBackupInstantAdapter)
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
    return jsonSource.use { source ->
      var bytesRead = 0L
      val boundedSource = object : ForwardingSource(source) {
        override fun read(sink: Buffer, byteCount: Long): Long {
          val read = super.read(sink, byteCount)
          if (read > 0) {
            bytesRead += read
            if (bytesRead > MAX_JSON_SIZE_BYTES) {
              throw IOException("Backup JSON exceeds maximum allowed size")
            }
          }
          return read
        }
      }.buffer()
      jsonAdapter.fromJson(boundedSource)!!
    }
  }

  private object LegacyBackupInstantAdapter : JsonAdapter<Instant>() {
    override fun fromJson(reader: JsonReader): Instant? {
      return when (reader.peek()) {
        JsonReader.Token.NULL -> reader.nextNull()
        JsonReader.Token.NUMBER ->
          Instant.fromEpochMilliseconds(reader.nextLong().legacyTimestampMillis())
        else -> throw JsonDataException("Expected epoch seconds or milliseconds at ${reader.path}")
      }
    }

    override fun toJson(writer: JsonWriter, value: Instant?) {
      if (value == null) {
        writer.nullValue()
      } else {
        writer.value(value.epochSeconds)
      }
    }
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

  companion object {
    internal const val MAX_JSON_SIZE_BYTES = 16L * 1024L * 1024L
  }
}
