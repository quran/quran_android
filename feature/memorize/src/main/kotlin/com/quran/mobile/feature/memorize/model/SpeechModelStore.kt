package com.quran.mobile.feature.memorize.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * The on-device speech model. It is downloaded once (or imported from a file) and verified, after
 * which everything runs offline. Nothing but the model files themselves is ever fetched.
 */
@SingleIn(AppScope::class)
class SpeechModelStore @Inject constructor(
  @param:ApplicationContext private val context: Context
) {

  sealed interface State {
    data object NotInstalled : State
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : State
    data object Installed : State
    data class Failed(val reason: String) : State
  }

  val spec: SpeechModelSpec = SpeechModelSpec.DEFAULT

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
    _state.value = State.Downloading(0, spec.files.sumOf { it.size })
    downloadJob = scope.launch { download() }
  }

  fun cancelDownload() {
    downloadJob?.cancel()
  }

  private suspend fun download() {
    try {
      directory.mkdirs()
      val total = spec.files.sumOf { it.size }
      var done = spec.files.filter { File(directory, it.name).length() == it.size }.sumOf { it.size }
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
          done += file.size
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

  /**
   * Installs model files the user picked (e.g. copied from a computer), for a fully offline setup.
   * Files are recognized by their size and verified like downloads. True if the model is installed.
   */
  suspend fun import(uris: List<Uri>): Boolean = withContext(Dispatchers.IO) {
    // a download would write the same files
    downloadJob?.cancelAndJoin()
    directory.mkdirs()
    for (uri in uris) {
      val size = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
        if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
      }
      val file = spec.files.firstOrNull { it.size == size } ?: continue
      try {
        val input = context.contentResolver.openInputStream(uri) ?: continue
        install(input, file, File(directory, file.name)) {}
      } catch (_: IOException) {
        // not the expected file after all
      }
    }
    val installed = isInstalled()
    _state.value = if (installed) State.Installed else State.NotInstalled
    installed
  }

  fun delete() {
    cancelDownload()
    directory.deleteRecursively()
    _state.value = State.NotInstalled
  }

  private suspend fun install(input: InputStream, file: ModelFile, target: File, progress: (Long) -> Unit) {
    val partial = File(target.path + ".part")
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
      if (sha != file.sha256 || written != file.size) throw IOException("${file.name} failed verification")
      if (!partial.renameTo(target)) throw IOException("can't install ${file.name}")
    } finally {
      partial.delete()
    }
  }
}

data class ModelFile(val name: String, val url: String, val sha256: String, val size: Long)

/** A downloadable speech model and the terms it comes with. */
data class SpeechModelSpec(
  val id: String,
  val model: ModelFile,
  val tokens: ModelFile,
  val license: String,
  val attribution: String,
) {
  val files: List<ModelFile> get() = listOf(model, tokens)

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
