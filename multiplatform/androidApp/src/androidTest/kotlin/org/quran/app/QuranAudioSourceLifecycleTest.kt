package org.quran.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.quran.app.data.platformAudioPlayer
import org.quran.app.data.initializePlatform
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class QuranAudioSourceLifecycleTest {
    @Test fun clearedSourceCannotResumeAndPlayerCanLoadAnotherSource() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "qa-source-clear-fixture.wav")
        val size = 8_000
        val wav = ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36 + size).put("WAVEfmt ".toByteArray())
        wav.putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000).putShort(2).putShort(16)
        wav.put("data".toByteArray()).putInt(size).put(ByteArray(size))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var player: org.quran.app.domain.AudioPlayer? = null
        val rejected = AtomicReference<String?>(null)
        val completed = CountDownLatch(1)
        val error = AtomicReference<String?>(null)
        try {
            file.writeBytes(wav.array()) // Synthetic silent PCM, never Quran recitation.
            instrumentation.runOnMainSync { initializePlatform(context); player = platformAudioPlayer() }
            val nativePlayer = checkNotNull(player)
            instrumentation.runOnMainSync {
                nativePlayer.loadLocal("file://${file.absolutePath}")
                nativePlayer.clearLocal()
                nativePlayer.play({ fail("Cleared source must not play") }, { rejected.set(it) })
            }
            assertNotNull("Cleared native source must reject resume", rejected.get())
            instrumentation.runOnMainSync {
                nativePlayer.loadLocal("file://${file.absolutePath}")
                nativePlayer.play({ completed.countDown() }, { error.set(it); completed.countDown() })
            }
            assertTrue("Reusable player must complete new source", completed.await(20, TimeUnit.SECONDS))
            assertNull(error.get())
        } finally {
            instrumentation.runOnMainSync { player?.release() }
            file.delete()
        }
    }
}
