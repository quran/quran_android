package org.quran.app.data

import org.quran.app.domain.SettingsStore
import org.quran.app.domain.TranslationRepository

/** Creates the platform-backed API adapter behind the domain repository contract. */
fun createTranslationRepository(settings: SettingsStore): TranslationRepository =
    QuranEncTranslationRepository(
        http = KtorTranslationHttpClient(platformTranslationHttpClient()),
        cache = settings,
    )
