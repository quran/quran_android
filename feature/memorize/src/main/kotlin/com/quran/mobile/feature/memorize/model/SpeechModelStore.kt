package com.quran.mobile.feature.memorize.model

import android.content.Context
import android.net.Uri
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * The on-device speech model. It is downloaded once (or imported from a file) and verified, after
 * which everything runs offline. Nothing but the model files themselves is ever fetched.
 */
@SingleIn(AppScope::class)
class SpeechModelStore private constructor(
  private val context: Context,
  val spec: SpeechModelSpec,
) {
  @Inject constructor(@ApplicationContext context: Context) : this(context, SpeechModelSpec.DEFAULT)

  sealed interface State {
    data object NotInstalled : State
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : State
    data object Installed : State
    data class Failed(val reason: String) : State
  }

  private val directory = File(context.noBackupFilesDir, "memorize/models/${spec.id}")
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var downloadJob: Job? = null
  private val _state = MutableStateFlow<State>(if (isInstalled()) State.Installed else State.NotInstalled)
  val state: StateFlow<State> = _state.asStateFlow()

  val modelFile: File get() = File(directory, spec.model.name)
  val tokensFile: File get() = File(directory, spec.tokens.name)

  fun isInstalled(): Boolean = spec.files.all { File(directory, it.name).length() == it.size }

  /** Whether a download is running (it carries on if the screen that started it goes away). */
  val isDownloading: Boolean get() = downloadJob?.isActive == true

  /** Downloads any missing file in the background, verifying each; progress is in [state]. */
  fun startDownload() {
    if (isDownloading) return
    _state.value = State.Downloading(0, spec.downloadSize)
    downloadJob = scope.launch { download() }
  }

  fun cancelDownload() {
    downloadJob?.cancel()
  }

  private suspend fun download() {
    try {
      directory.mkdirs()
      val total = spec.downloadSize
      var done = spec.files.filter { File(directory, it.name).length() == it.size }.sumOf { it.downloadSize }
      for (file in spec.files) {
        val target = File(directory, file.name)
        if (target.length() == file.size) continue
        val connection = URL(file.url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        try {
          if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("HTTP ${connection.responseCode} for ${file.name}")
          }
          val base = done
          install(connection.inputStream, file, target) { written ->
            _state.value = State.Downloading(base + written, total)
          }
          done += file.downloadSize
        } finally {
          connection.disconnect()
        }
      }
      _state.value = State.Installed
    } catch (e: CancellationException) {
      _state.value = if (isInstalled()) State.Installed else State.NotInstalled
      throw e
    } catch (e: IOException) {
      _state.value = State.Failed(e.message ?: e.javaClass.simpleName)
    }
  }

  /** The model files that aren't installed yet. */
  fun missingFiles(): List<ModelFile> = spec.files.filter { File(directory, it.name).length() != it.size }

  /**
   * Installs model files the user picked (e.g. copied from a computer), for a fully offline setup.
   * True if the model is installed afterwards.
   */
  suspend fun import(uris: List<Uri>): Boolean {
    for (uri in uris) importFile(uri)
    return isInstalled()
  }

  /**
   * Installs what [uri] holds: one of the model files, or a .zip with them. Files are recognized by
   * their SHA-256 (their names may have changed on the way). Returns the files that were installed.
   */
  suspend fun importFile(uri: Uri): List<ModelFile> = withContext(Dispatchers.IO) {
    // a download would write the same files
    downloadJob?.cancelAndJoin()
    directory.mkdirs()
    val installed = ArrayList<ModelFile>()
    val resolver = context.contentResolver
    try {
      if (resolver.openInputStream(uri)?.use { isZip(it) } == true) {
        ZipInputStream(resolver.openInputStream(uri)).use { zip ->
          while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory) continue
            // install() closes what it reads; the zip has more entries to read
            installMatching(object : FilterInputStream(zip) { override fun close() = Unit })?.let { installed += it }
          }
        }
      } else {
        resolver.openInputStream(uri)?.let { input -> installMatching(input)?.let { installed += it } }
      }
    } catch (_: IOException) {
      // unreadable
    }
    _state.value = if (isInstalled()) State.Installed else State.NotInstalled
    installed
  }

  /** Copies [input] aside and installs it as the model file with the same SHA-256, if any. */
  private suspend fun installMatching(input: InputStream): ModelFile? {
    val partial = File(directory, "import.part")
    try {
      val digest = MessageDigest.getInstance("SHA-256")
      var size = 0L
      input.use { source ->
        partial.outputStream().use { out ->
          val buffer = ByteArray(1 shl 16)
          while (true) {
            currentCoroutineContext().ensureActive()
            val read = source.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
            out.write(buffer, 0, read)
            size += read
            // larger than any model file: not one of them
            if (size > spec.files.maxOf { maxOf(it.size, it.source?.size ?: 0) }) return null
          }
        }
      }
      val sha = digest.digest().joinToString("") { "%02x".format(it) }
      spec.files.firstOrNull { it.sha256 == sha && it.size == size }?.let { file ->
        return if (partial.renameTo(File(directory, file.name))) file else null
      }
      // a file as published, which the app converts
      val file = spec.files.firstOrNull { it.source?.sha256 == sha && it.source.size == size } ?: return null
      return try {
        convert(partial, file, File(directory, file.name))
        file
      } catch (_: IOException) {
        null
      }
    } finally {
      partial.delete()
    }
  }

  companion object {
    /** A store for another model (in its own directory), e.g. the double-check's. */
    internal fun of(context: Context, spec: SpeechModelSpec) = SpeechModelStore(context, spec)

    /** A store for a model of the test's own files. */
    internal fun forTest(context: Context, spec: SpeechModelSpec) = SpeechModelStore(context, spec)
  }

  private fun isZip(input: InputStream): Boolean {
    val header = ByteArray(4)
    return input.read(header) == 4 && header.contentEquals(byteArrayOf(0x50, 0x4b, 0x03, 0x04))
  }

  fun delete() {
    cancelDownload()
    directory.deleteRecursively()
    _state.value = State.NotInstalled
  }

  private suspend fun install(input: InputStream, file: ModelFile, target: File, progress: (Long) -> Unit) {
    val partial = File(target.path + ".part")
    val expected = file.source
    try {
      val digest = MessageDigest.getInstance("SHA-256")
      var written = 0L
      input.use { source ->
        partial.outputStream().use { out ->
          val buffer = ByteArray(1 shl 16)
          while (true) {
            currentCoroutineContext().ensureActive()
            val read = source.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
            out.write(buffer, 0, read)
            written += read
            progress(written)
          }
        }
      }
      val sha = digest.digest().joinToString("") { "%02x".format(it) }
      if (sha != (expected?.sha256 ?: file.sha256) || written != (expected?.size ?: file.size)) {
        throw IOException("${file.name} failed verification")
      }
      if (expected != null) {
        convert(partial, file, target)
      } else if (!partial.renameTo(target)) {
        throw IOException("can't install ${file.name}")
      }
    } finally {
      partial.delete()
    }
  }

  /** Converts the verified [source] of [file] into [target], and verifies the result. */
  private fun convert(source: File, file: ModelFile, target: File) {
    val converted = File(target.path + ".converted")
    try {
      file.source!!.converter.convert(source, converted)
      if (converted.length() != file.size || sha256(converted) != file.sha256) {
        throw IOException("${file.name} failed verification after conversion")
      }
      if (!converted.renameTo(target)) throw IOException("can't install ${file.name}")
    } finally {
      converted.delete()
    }
  }

  private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buffer = ByteArray(1 shl 16)
      while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }
}

/**
 * One file of a model, as installed. With a [source], what is downloaded (or imported) is the file
 * as published, which the app converts into this one.
 */
data class ModelFile(val name: String, val url: String, val sha256: String, val size: Long, val source: Source? = null) {
  data class Source(val sha256: String, val size: Long, val converter: ModelConverter)

  val downloadSize: Long get() = source?.size ?: size
}

/** Turns a downloaded file into the one the app uses (deterministically: the result is checked). */
fun interface ModelConverter {
  fun convert(source: File, target: File)
}

/** A downloadable speech model and the terms it comes with. */
data class SpeechModelSpec(
  val id: String,
  val model: ModelFile,
  val tokens: ModelFile,
  val license: String,
  val attribution: String,
  /** Any other files the model needs. */
  val extras: List<ModelFile> = emptyList(),
) {
  val files: List<ModelFile> get() = listOf(model, tokens) + extras

  /** What downloading every file takes. */
  val downloadSize: Long get() = files.sumOf { it.downloadSize }

  companion object {
    // Streaming Zipformer2-CTC phoneme recognizer by Quran-Lab (zipformer_p-arabic-v3.1), int8, as
    // re-hosted without gating by Saboorhsn/quran-stt-onnx: byte-identical to Quran-Lab's
    // zipformer_p_arabic_v3.1.int8.onnx and tokens.txt (gated repo, @3da514fc). Pinned to a commit
    // and checksummed.
    private const val BASE =
      "https://huggingface.co/Saboorhsn/quran-stt-onnx/resolve/51ec9bcddeaf1c480bbe39064a2a3b88bc2ff224"

    val DEFAULT = SpeechModelSpec(
      id = "zipformer-p-arabic-v3.1-int8",
      model = ModelFile(
        name = "quran-stt-int8.onnx",
        url = "$BASE/quran-stt-int8.onnx",
        sha256 = "31755836528da336a6192121cd7bc82cb41752dddb65566fd000b89c8686da6b",
        size = 72_705_392,
      ),
      tokens = ModelFile(
        name = "tokens.txt",
        url = "$BASE/tokens.txt",
        sha256 = "252c10687e442aa9291973065fae19fa39bcd681c4f5612ec496a647e20b43a1",
        size = 2_346,
      ),
      license = "Quran-Lab No-Profit License 1.2 (NPL-1.2)",
      attribution = "Quran-Lab, zipformer_p-arabic-v3.1",
    )
  }
}
