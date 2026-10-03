package org.quran.app.data


/** Small transport port keeps data tests independent from real networking. */
interface TranslationHttpClient {
    suspend fun get(url: String): String
}
