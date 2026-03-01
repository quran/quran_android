package com.quran.mobile.feature.voicesearch.asr

data class TranscriptionResult(
  val text: String,
  val normalizedText: String,
  val durationMs: Long
)
