package org.quran.app.designsystem

import org.quran.app.model.AppLanguage

/** Compatibility helper while remaining feature copy moves to Compose resources. */
fun label(language: AppLanguage, english: String, arabic: String): String = if (language.isRtl) arabic else english
