@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package org.quran.app.data

import org.quran.app.domain.AudioPlayer
import org.quran.app.domain.SettingsStore
import platform.AVFAudio.*
import platform.Foundation.*
import platform.darwin.NSObject

actual fun platformSettingsStore(): SettingsStore = object : SettingsStore {
    override fun get(key: String): String? = NSUserDefaults.standardUserDefaults.stringForKey(key)
    override fun set(key: String, value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = key)
    }
}
internal actual fun platformTranslationHttpClient() = io.ktor.client.HttpClient(io.ktor.client.engine.darwin.Darwin) {
    expectSuccess = true
    install(io.ktor.client.plugins.HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 15_000
    }
}

actual fun platformAudioPlayer(): AudioPlayer = IosAudioPlayer()

private class IosAudioPlayer : AudioPlayer {
    private var player: AVAudioPlayer? = null
    private var completed: (() -> Unit)? = null
    private var failed: ((String) -> Unit)? = null
    // Retained separately: Kotlin interfaces cannot be mixed into an Objective-C subclass.
    private val delegate = object : NSObject(), AVAudioPlayerDelegateProtocol {
        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
            if (successfully) {
                player.currentTime = 0.0
                completed?.invoke()
            } else {
                failed?.invoke("Recording could not be played")
            }
        }
    }

    override fun loadLocal(uri: String) {
        require(uri.startsWith("file://")) { "Only a local recording can be imported" }
        val url = NSURL.URLWithString(uri) ?: error("Invalid recording URL")
        player?.stop()
        player = AVAudioPlayer(contentsOfURL = url, error = null)
        require(player != null) { "Recording could not be read" }
        player?.delegate = delegate
        player?.prepareToPlay()
    }

    override fun play(onCompleted: () -> Unit, onError: (String) -> Unit) {
        completed = onCompleted
        failed = onError
        val audio = player
        if (audio == null) { onError("Import a recording first"); return }
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, error = null)
        AVAudioSession.sharedInstance().setActive(true, error = null)
        if (audio.currentTime >= audio.duration) audio.currentTime = 0.0
        if (!audio.play()) onError("Recording could not be played")
    }

    override fun pause() { player?.pause() }

    override fun release() {
        player?.stop()
        player?.delegate = null
        player = null
        completed = null
        failed = null
        AVAudioSession.sharedInstance().setActive(false, error = null)
    }
}
