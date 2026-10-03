package org.quran.app.data

import io.ktor.client.HttpClient
import org.quran.app.domain.SettingsStore
import org.quran.app.domain.TranslationRepository

/** Owns the platform network engine for one application composition. */
class TranslationDataModule(
    settings: SettingsStore,
    private val client: HttpClient = platformTranslationHttpClient(),
) {
    val repository: TranslationRepository = QuranEncTranslationRepository(
        http = KtorTranslationHttpClient(client),
        cache = settings,
    )

    fun close() = client.close()
}
