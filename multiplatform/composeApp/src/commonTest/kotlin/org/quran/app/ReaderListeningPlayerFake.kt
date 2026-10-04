package org.quran.app

import org.quran.app.domain.AudioPlayer

internal class ReaderListeningPlayerFake(private val events: MutableList<String>) : AudioPlayer {
    var failPlayback = false
    val loaded = mutableListOf<String>()
    val completions = mutableListOf<() -> Unit>()
    override fun loadLocal(uri: String) { loaded += uri; events += "load" }
    override fun play(onCompleted: () -> Unit, onError: (String) -> Unit) { if (failPlayback) onError("native failure") else completions += onCompleted; events += "play" }
    override fun pause() { events += "pause" }
    override fun clearLocal() { events += "clear" }
    override fun release() { events += "terminal-release" }
}
