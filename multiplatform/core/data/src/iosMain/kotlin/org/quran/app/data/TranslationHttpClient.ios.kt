package org.quran.app.data

internal actual fun platformTranslationHttpClient() = io.ktor.client.HttpClient(io.ktor.client.engine.darwin.Darwin) {
    expectSuccess = true
    install(io.ktor.client.plugins.HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 15_000
    }
}
