package com.quran.labs.androidquran.model.bookmark;

import com.quran.labs.androidquran.dao.BookmarkData;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

import java.io.IOException;

import okio.BufferedSink;
import okio.BufferedSource;

public class BookmarkJsonModel {
  private final JsonAdapter<BookmarkData> jsonAdapter;

  public BookmarkJsonModel() {
    Moshi moshi = new Moshi.Builder().build();
    jsonAdapter = moshi.adapter(BookmarkData.class);
  }

  public void toJson(BufferedSink sink, BookmarkData bookmarks) throws IOException {
    jsonAdapter.toJson(sink, bookmarks);
  }

  public BookmarkData fromJson(BufferedSource jsonSource) throws IOException {
    return jsonAdapter.fromJson(jsonSource);
  }
}
