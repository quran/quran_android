package com.quran.mobile.feature.memorize.check

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The double-check's model, in its own process ([DoubleCheckService]). Not on the main thread:
 * every call waits for the other process. If that process goes away (e.g. the system needs its
 * memory), the checks hear nothing, and the session corrects as it would without them.
 */
internal class RemotePieceRecognizer(private val context: Context, model: File, tokens: File) : PieceRecognizer {
  private val thread = HandlerThread("double-check replies").apply { start() }
  private val replies = LinkedBlockingQueue<Message>()
  private val replyTo = Messenger(Handler(thread.looper) { message -> replies.put(Message.obtain(message)); true })
  private val connected = CountDownLatch(1)
  @Volatile private var service: Messenger? = null
  private val connection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
      service = Messenger(binder)
      connected.countDown()
    }

    override fun onServiceDisconnected(name: ComponentName) {
      service = null
    }
  }

  init {
    val bound = context.bindService(Intent(context, DoubleCheckService::class.java), connection, Context.BIND_AUTO_CREATE)
    val opened = bound && connected.await(CONNECT_SECONDS, TimeUnit.SECONDS) &&
      call(
        DoubleCheckService.OPEN,
        Bundle().apply {
          putString(DoubleCheckService.MODEL, model.path)
          putString(DoubleCheckService.TOKENS, tokens.path)
        },
        LOAD_SECONDS,
      )?.arg1 == 1
    if (!opened) {
      close()
      throw IllegalStateException("the double-check's model didn't load")
    }
  }

  override fun pieces(audio: FloatArray): IntArray {
    // 16-bit, as speech models hear it, and half the size to send
    val samples = ShortArray(audio.size) { (audio[it] * 32_768f).toInt().coerceIn(-32_768, 32_767).toShort() }
    val reply = call(DoubleCheckService.PIECES, Bundle().apply { putShortArray(DoubleCheckService.AUDIO, samples) }, CHECK_SECONDS)
    return reply?.takeIf { it.arg1 == 1 }?.data?.getIntArray(DoubleCheckService.RESULT) ?: IntArray(0)
  }

  /** Sends [what] and waits for its reply; null if there was none in time. */
  @Synchronized
  private fun call(what: Int, data: Bundle, timeoutSeconds: Long): Message? {
    val service = service ?: return null
    replies.clear()
    val message = Message.obtain(null, what).apply {
      this.data = data
      replyTo = this@RemotePieceRecognizer.replyTo
    }
    try {
      service.send(message)
    } catch (_: RemoteException) {
      return null
    }
    while (true) {
      val reply = replies.poll(timeoutSeconds, TimeUnit.SECONDS) ?: return null
      // a late reply to an earlier call is dropped
      if (reply.what == what) return reply
    }
  }

  override fun close() {
    runCatching { context.unbindService(connection) }
    service = null
    thread.quitSafely()
  }

  private companion object {
    const val CONNECT_SECONDS = 10L
    const val LOAD_SECONDS = 30L
    const val CHECK_SECONDS = 10L
  }
}
