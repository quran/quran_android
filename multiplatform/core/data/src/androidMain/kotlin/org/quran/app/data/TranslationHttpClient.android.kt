package org.quran.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout

internal actual fun platformTranslationHttpClient(): HttpClient = HttpClient(CIO) {
    expectSuccess = true

    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 15_000
    }
}
