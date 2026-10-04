@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package org.quran.app.data

import org.quran.app.domain.AudioPlayer
import org.quran.app.domain.SettingsStore
import platform.Foundation.*

actual fun platformSettingsStore(): SettingsStore = object : SettingsStore {
    override fun get(key: String): String? = NSUserDefaults.standardUserDefaults.stringForKey(key)
    override fun set(key: String, value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = key)
    }
}

actual fun platformAudioPlayer(): AudioPlayer = IosAudioPlayer()
