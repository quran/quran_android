package com.quran.labs.androidquran.dao.translation;

import com.squareup.moshi.Json;

import java.util.List;

public class TranslationList {
  @Json(name = "data") public final List<Translation> translations;

  public TranslationList(List<Translation> translations) {
    this.translations = translations;
  }
}
