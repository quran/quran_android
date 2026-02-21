package com.quran.labs.feature.autoquran.common

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ApplicationInfo
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.collection.LruCache
import com.quran.data.model.audio.Qari
import com.quran.data.source.PageProvider
import com.quran.labs.feature.autoquran.di.QuranAutoInjector
import com.quran.mobile.di.QuranApplicationComponentProvider
import dev.zacsweers.metro.Inject
import timber.log.Timber
import java.io.FileNotFoundException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Resolves artwork URIs used by Android Auto surfaces.
 *
 * Clients receive only a content Uri in MediaMetadata. When they resolve it, we generate the
 * deterministic PNG on-demand, keeping browsing cheap.
 */
class QariArtworkContentProvider : ContentProvider() {
  private var didInject = false

  @Inject
  lateinit var pageProvider: PageProvider

  private val renderer: QariArtworkProvider by lazy {
    QariArtworkProvider(requireNotNull(context).applicationContext)
  }

  // Bounded queue to prevent unbounded growth of pending pipe-backed requests.
  private val executor = ThreadPoolExecutor(
    CORE_POOL_SIZE,
    CORE_POOL_SIZE,
    0L,
    TimeUnit.MILLISECONDS,
    ArrayBlockingQueue(MAX_QUEUE_SIZE),
    { r -> Thread(r, "autoquran-artwork").apply { isDaemon = true } },
    ThreadPoolExecutor.AbortPolicy(),
  )

  // Cache resolved bytes in-process. Android Auto can request the same image repeatedly during
  // scrolling and during metadata refreshes.
  private val artworkCache = object : LruCache<String, ByteArray>(ARTWORK_CACHE_MAX_BYTES) {
    override fun sizeOf(key: String, value: ByteArray): Int = value.size
  }
  private val knownQariIdCache = LruCache<Int, Boolean>(KNOWN_QARI_CACHE_MAX_ENTRIES)

  override fun onCreate(): Boolean = true

  override fun getType(uri: Uri): String? {
    return if (matchesKnownPath(uri)) "image/png" else null
  }

  override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
    if (!mode.startsWith("r")) throw FileNotFoundException("Read-only provider")
    val (qariId, sura) = parse(uri)
      ?: throw FileNotFoundException("Unknown uri: $uri")
    // Some qaris may legitimately have id == 0.
    if (qariId < 0) throw FileNotFoundException("Invalid qari id")
    if (sura != null && sura !in 1..114) throw FileNotFoundException("Invalid sura")

    val pipe = ParcelFileDescriptor.createPipe()
    val readSide = pipe[0]
    val writeSide = pipe[1]

    // Do all heavy work off the binder thread. The caller will block reading from the pipe until
    // we write + close, but the content provider call itself returns immediately.
    val work = Runnable {
      val startMs = SystemClock.uptimeMillis()
      val bytes = runCatching {
        if (!isKnownQariId(qariId)) return@runCatching null
        generateArtworkBytes(qariId, sura)
      }.getOrNull()

      val isDebuggable =
        ((context?.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_DEBUGGABLE) != 0
      if (isDebuggable) {
        val tookMs = SystemClock.uptimeMillis() - startMs
        if (bytes == null || tookMs > 300) {
          Timber.d("artwork qariId=$qariId sura=${sura ?: "-"} success=${bytes != null} tookMs=$tookMs uri=$uri")
        }
      }

      runCatching {
        ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { out ->
          // If generation fails, close without writing so the reader gets EOF.
          if (bytes != null) out.write(bytes)
        }
      }.onFailure {
        // If we couldn't write, ensure the write side is closed so the reader doesn't hang.
        runCatching { writeSide.close() }
      }
    }
    try {
      executor.execute(work)
    } catch (_: RejectedExecutionException) {
      // Saturated: fail fast and close both ends so we don't leak descriptors.
      runCatching { writeSide.close() }
      runCatching { readSide.close() }
      throw FileNotFoundException("Artwork provider busy")
    }

    return readSide
  }

  private fun isKnownQariId(qariId: Int): Boolean {
    return if (!ensureInjected()) {
      true
    } else {
      val cached = knownQariIdCache[qariId]
      if (cached != null) {
        cached
      } else {
        val exists = pageProvider.getQaris().any { it.id == qariId }
        knownQariIdCache.put(qariId, exists)
        exists
      }
    }
  }

  private fun generateArtworkBytes(qariId: Int, sura: Int?): ByteArray? {
    return if (sura == null) {
      val displayName = qariNameForId(qariId)
      // Include name in the key so an early empty-name render doesn't "stick" in cache.
      val cacheKey = "qari:$qariId|name:$displayName"
      cachedArtwork(cacheKey) {
        renderer.generateQariPng(qariId, displayName)
      }
    } else {
      val cacheKey = "sura:$qariId:$sura"
      cachedArtwork(cacheKey) {
        renderer.generateSuraPng(qariId, sura)
      }
    }
  }

  private fun cachedArtwork(cacheKey: String, generator: () -> ByteArray?): ByteArray? {
    val cached = artworkCache[cacheKey]
    return if (cached != null) {
      cached
    } else {
      val generated = generator()
      if (generated == null) {
        null
      } else {
        artworkCache.put(cacheKey, generated)
        generated
      }
    }
  }

  private fun qariNameForId(qariId: Int): String {
    val qari = qariForId(qariId)
    val context = context
    return if (qari == null || context == null) {
      ""
    } else {
      runCatching { context.getString(qari.nameResource) }.getOrDefault("")
    }
  }

  private fun qariForId(qariId: Int): Qari? {
    return if (!ensureInjected()) {
      null
    } else {
      pageProvider.getQaris().firstOrNull { it.id == qariId }
    }
  }

  private fun ensureInjected(): Boolean {
    return if (didInject) {
      true
    } else {
      val appContext = context?.applicationContext as? QuranApplicationComponentProvider
      val component = runCatching { appContext?.provideQuranApplicationComponent() }.getOrNull()
      val injector = component as? QuranAutoInjector
      if (injector != null) {
        injector.inject(this)
        didInject = true
        true
      } else {
        false
      }
    }
  }

  override fun query(
    uri: Uri,
    projection: Array<out String>?,
    selection: String?,
    selectionArgs: Array<out String>?,
    sortOrder: String?
  ): Cursor? = null

  override fun insert(uri: Uri, values: ContentValues?): Uri? {
    throw UnsupportedOperationException("insert not supported")
  }

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
    throw UnsupportedOperationException("delete not supported")
  }

  override fun update(
    uri: Uri,
    values: ContentValues?,
    selection: String?,
    selectionArgs: Array<out String>?
  ): Int {
    throw UnsupportedOperationException("update not supported")
  }

  private fun matchesKnownPath(uri: Uri): Boolean = parse(uri) != null

  private data class Parsed(val qariId: Int, val sura: Int?)

  private fun parse(uri: Uri): Parsed? {
    if (uri.scheme != "content") return null
    if (uri.authority.isNullOrBlank()) return null
    if (!uri.query.isNullOrEmpty()) return null
    if (uri.fragment != null) return null

    val segments = uri.pathSegments ?: return null
    if (segments.isEmpty()) return null

    // Expected:
    // content://<authority>/artwork/qari/<qariId>
    // content://<authority>/artwork/qari/<qariId>/sura/<sura>
    if (segments.getOrNull(0) != PATH_ARTWORK) return null
    if (segments.getOrNull(1) != PATH_QARI) return null
    val qariId = segments.getOrNull(2)?.toIntOrNull() ?: return null

    // qari artwork
    if (segments.size == 3) {
      return Parsed(qariId = qariId, sura = null)
    }

    // sura artwork
    if (segments.size != 5) return null
    if (segments.getOrNull(3) != PATH_SURA) return null
    val sura = segments.getOrNull(4)?.toIntOrNull() ?: return null
    return Parsed(qariId = qariId, sura = sura)
  }

  companion object {
    private const val CORE_POOL_SIZE = 4
    private const val MAX_QUEUE_SIZE = 64
    private const val ARTWORK_CACHE_MAX_BYTES = 4 * 1024 * 1024
    private const val KNOWN_QARI_CACHE_MAX_ENTRIES = 64

    const val PATH_ARTWORK = "artwork"
    const val PATH_QARI = "qari"
    const val PATH_SURA = "sura"
  }
}
