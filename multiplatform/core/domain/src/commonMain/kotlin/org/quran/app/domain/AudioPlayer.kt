package org.quran.app.domain

/** Platform playback port. Completion fires only after a media item actually ends. */
interface AudioPlayer {
    fun loadLocal(uri: String)
    fun play(onCompleted: () -> Unit, onError: (String) -> Unit)
    fun pause()
    fun release()
}
