@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package org.quran.app.data

import org.quran.app.domain.AudioPlayer
import platform.AVFAudio.*
import platform.Foundation.NSURL
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.darwin.NSObject

internal class IosAudioPlayer : AudioPlayer {
    private var player: AVAudioPlayer? = null
    private var completed: (() -> Unit)? = null
    private var failed: ((String) -> Unit)? = null
    private var playbackChanged: ((Boolean) -> Unit)? = null
    private var intendsPlayback = false
    private var wasPlayingBeforeInterruption = false
    private var interruptionObserver: Any? = NSNotificationCenter.defaultCenter.addObserverForName(
        name = AVAudioSessionInterruptionNotification,
        `object` = AVAudioSession.sharedInstance(),
        queue = null,
    ) { notification -> handleInterruption(notification) }
    // Retained separately: Kotlin interfaces cannot be mixed into an Objective-C subclass.
    private val delegate = object : NSObject(), AVAudioPlayerDelegateProtocol {
        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
            if (successfully) {
                player.currentTime = 0.0
                intendsPlayback = false
                completed?.invoke()
            } else {
                intendsPlayback = false
                playbackChanged?.invoke(false)
                failed?.invoke("Recording could not be played")
            }
        }
    }

    override fun loadLocal(uri: String) {
        require(uri.startsWith("file://")) { "Only a local recording can be imported" }
        val url = NSURL.URLWithString(uri) ?: error("Invalid recording URL")
        player?.stop()
        intendsPlayback = false
        wasPlayingBeforeInterruption = false
        player = AVAudioPlayer(contentsOfURL = url, error = null)
        require(player != null) { "Recording could not be read" }
        player?.delegate = delegate
        player?.prepareToPlay()
    }

    override fun play(onCompleted: () -> Unit, onError: (String) -> Unit, onPlaybackChanged: (Boolean) -> Unit) {
        completed = onCompleted
        failed = onError
        playbackChanged = onPlaybackChanged
        val audio = player
        if (audio == null) {
            intendsPlayback = false
            onError("Import a recording first")
            return
        }
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, error = null)
        AVAudioSession.sharedInstance().setActive(true, error = null)
        if (audio.currentTime >= audio.duration) audio.currentTime = 0.0
        if (!audio.play()) {
            intendsPlayback = false
            onError("Recording could not be played")
        } else {
            intendsPlayback = true
            wasPlayingBeforeInterruption = false
            onPlaybackChanged(true)
        }
    }

    override fun pause() {
        intendsPlayback = false
        wasPlayingBeforeInterruption = false
        player?.pause()
        playbackChanged?.invoke(false)
    }

    override fun clearLocal() {
        player?.stop()
        intendsPlayback = false
        wasPlayingBeforeInterruption = false
        player?.delegate = null
        player = null
        completed = null
        failed = null
        playbackChanged = null
    }

    override fun release() {
        clearLocal()
        AVAudioSession.sharedInstance().setActive(false, error = null)
        interruptionObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        interruptionObserver = null
    }

    private fun handleInterruption(notification: NSNotification?) {
        notification ?: return
        val userInfo = notification.userInfo ?: return
        val type = (userInfo[AVAudioSessionInterruptionTypeKey] as? NSNumber)?.unsignedLongValue ?: return
        when (type) {
            AVAudioSessionInterruptionTypeBegan -> {
                wasPlayingBeforeInterruption = intendsPlayback
                if (wasPlayingBeforeInterruption) {
                    player?.pause()
                    playbackChanged?.invoke(false)
                }
            }
            AVAudioSessionInterruptionTypeEnded -> {
                val options = (userInfo[AVAudioSessionInterruptionOptionKey] as? NSNumber)?.unsignedLongValue ?: 0uL
                val shouldResume = options and AVAudioSessionInterruptionOptionShouldResume != 0uL
                if (wasPlayingBeforeInterruption && shouldResume) {
                    AVAudioSession.sharedInstance().setActive(true, error = null)
                    if (player?.play() == true) {
                        intendsPlayback = true
                        playbackChanged?.invoke(true)
                    } else {
                        intendsPlayback = false
                        playbackChanged?.invoke(false)
                    }
                } else {
                    intendsPlayback = false
                }
                wasPlayingBeforeInterruption = false
            }
        }
    }
}
