package org.quran.app.data

internal actual fun platformRecitationHttpClient() = io.ktor.client.HttpClient(io.ktor.client.engine.darwin.Darwin) {
    expectSuccess = false
    followRedirects = false
    install(io.ktor.client.plugins.HttpTimeout) {
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 15_000
    }
}
