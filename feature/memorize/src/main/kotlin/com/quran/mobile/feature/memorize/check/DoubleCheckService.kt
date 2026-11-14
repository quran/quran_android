package com.quran.mobile.feature.memorize.check

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import com.quran.mobile.feature.memorize.engine.sherpa.SherpaPieceRecognizer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Runs the double-check's model in a process of its own (":doublecheck").
 *
 * With sherpa-onnx 1.13.8, a second model decoding in the same process as the streaming recognizer
 * corrupts the recognizer's state. On a Mi 11 the session went deaf for 20–45 s after a check,
 * until its stream started over. This happened whether or not the two decoded at the same time, and
 * on a desktop the two failed outright when they did. In its own process the check can't touch the
 * recognizer's memory. The app talks to it with [RemotePieceRecognizer].
 */
class DoubleCheckService : Service() {
  private val thread = HandlerThread("double-check").apply { start() }
  private var recognizer: SherpaPieceRecognizer? = null
  private val messenger = Messenger(Handler(thread.looper) { message -> handle(message); true })

  override fun onBind(intent: Intent): IBinder = messenger.binder

  private fun handle(message: Message) {
    val reply = Message.obtain(null, message.what)
    when (message.what) {
      OPEN -> {
        recognizer?.close()
        recognizer = null
        val data = message.data
        recognizer = runCatching {
          SherpaPieceRecognizer(File(data.getString(MODEL)!!), File(data.getString(TOKENS)!!))
        }.getOrNull()
        reply.arg1 = if (recognizer != null) 1 else 0
      }
      PIECES -> {
        // 16-bit little-endian samples, in a file of the app's (too big for a binder message)
        val audio = runCatching {
          val samples = ByteBuffer.wrap(File(message.data.getString(AUDIO)!!).readBytes()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
          FloatArray(samples.remaining()) { samples.get(it) / 32_768f }
        }.getOrNull()
        val pieces = audio?.let { a -> recognizer?.let { runCatching { it.pieces(a) }.getOrNull() } }
        reply.arg1 = if (pieces != null) 1 else 0
        reply.data = Bundle().apply { putIntArray(RESULT, pieces ?: IntArray(0)) }
      }
      else -> return
    }
    try {
      message.replyTo?.send(reply)
    } catch (_: RemoteException) {
      // the app went away
    }
  }

  override fun onDestroy() {
    // its looper runs no more messages after this
    Handler(thread.looper).post {
      recognizer?.close()
      recognizer = null
    }
    thread.quitSafely()
    super.onDestroy()
  }

  internal companion object {
    const val OPEN = 1
    const val PIECES = 2
    const val MODEL = "model"
    const val TOKENS = "tokens"
    const val AUDIO = "audio"
    const val RESULT = "pieces"
  }
}
