package org.quran.app.data
import org.quran.app.domain.*
expect fun platformSettingsStore(): SettingsStore
expect fun platformAudioPlayer(): AudioPlayer

expect fun platformCompassProvider(): CompassProvider
