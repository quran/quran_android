package com.quran.mobile.feature.voicesearch.asr

import android.content.Context
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

@SingleIn(AppScope::class)
class AsrModelManager @Inject constructor(
  @ApplicationContext private val appContext: Context,
  private val okHttpClient: OkHttpClient
) {

  private val _modelState = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
  val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

  @Volatile
  private var activeCall: okhttp3.Call? = null

  private val modelDir: File
    get() = File(appContext.filesDir, "models/whisper-quran")

  val encoderPath: String get() = File(modelDir, ENCODER_FILENAME).absolutePath
  val decoderPath: String get() = File(modelDir, DECODER_FILENAME).absolutePath
  val tokensPath: String get() = File(modelDir, TOKENS_FILENAME).absolutePath

  fun checkModelAvailability() {
    // Migrate old file names from previous versions
    migrateOldModelFiles()

    val ready = File(modelDir, ENCODER_FILENAME).exists() &&
        File(modelDir, DECODER_FILENAME).exists() &&
        File(modelDir, TOKENS_FILENAME).exists()
    _modelState.value = if (ready) ModelState.Ready else ModelState.NotDownloaded
  }

  private fun migrateOldModelFiles() {
    val oldNames = mapOf(
      "encoder.int8.onnx" to ENCODER_FILENAME,
      "decoder.int8.onnx" to DECODER_FILENAME,
      "tokens.txt" to TOKENS_FILENAME
    )
    for ((oldName, newName) in oldNames) {
      val oldFile = File(modelDir, oldName)
      val newFile = File(modelDir, newName)
      if (oldFile.exists() && !newFile.exists()) {
        oldFile.renameTo(newFile)
      }
    }
  }

  fun isModelReady(): Boolean = _modelState.value is ModelState.Ready

  suspend fun downloadModel() {
    if (_modelState.value is ModelState.Downloading) return

    _modelState.value = ModelState.Downloading(0f)

    try {
      withContext(Dispatchers.IO) {
        modelDir.mkdirs()

        val files = listOf(
          Triple(ENCODER_FILENAME, ENCODER_URL, ENCODER_SHA256),
          Triple(DECODER_FILENAME, DECODER_URL, DECODER_SHA256),
          Triple(TOKENS_FILENAME, TOKENS_URL, TOKENS_SHA256)
        )

        var completedBytes = 0L
        val totalSize = MODEL_TOTAL_SIZE_BYTES

        for ((filename, url, expectedSha256) in files) {
          val targetFile = File(modelDir, filename)
          if (targetFile.exists()) {
            // Verify existing file integrity
            if (expectedSha256.isNotEmpty() && !verifyChecksum(targetFile, expectedSha256)) {
              Timber.w("Checksum mismatch for existing %s, re-downloading", filename)
              targetFile.delete()
            } else {
              completedBytes += targetFile.length()
              _modelState.value = ModelState.Downloading(completedBytes.toFloat() / totalSize)
              continue
            }
          }

          val tempFile = File(modelDir, "$filename.tmp")
          downloadFile(url, tempFile) { bytesRead ->
            _modelState.value = ModelState.Downloading(
              (completedBytes + bytesRead).toFloat() / totalSize
            )
          }

          // Verify checksum after download
          if (expectedSha256.isNotEmpty() && !verifyChecksum(tempFile, expectedSha256)) {
            tempFile.delete()
            throw Exception("Checksum verification failed for $filename")
          }

          tempFile.renameTo(targetFile)
          completedBytes += targetFile.length()
        }

        _modelState.value = ModelState.Ready
      }
    } catch (e: Exception) {
      Timber.e(e, "Failed to download ASR model")
      _modelState.value = ModelState.Error(e.message ?: "Download failed")
    }
  }

  private fun downloadFile(url: String, target: File, onProgress: (Long) -> Unit) {
    val request = Request.Builder().url(url).build()
    val call = okHttpClient.newCall(request)
    activeCall = call
    val response = call.execute()

    if (!response.isSuccessful) {
      throw Exception("Download failed: HTTP ${response.code}")
    }

    val body = response.body ?: throw Exception("Empty response body")
    var bytesRead = 0L

    body.byteStream().use { input ->
      FileOutputStream(target).use { output ->
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
          output.write(buffer, 0, read)
          bytesRead += read
          onProgress(bytesRead)
        }
      }
    }

    activeCall = null
  }

  fun cancelDownload() {
    activeCall?.cancel()
    activeCall = null
    cleanupTempFiles()
    _modelState.value = ModelState.NotDownloaded
  }

  private fun cleanupTempFiles() {
    modelDir.listFiles()
      ?.filter { it.name.endsWith(".tmp") }
      ?.forEach { it.delete() }
  }

  private fun verifyChecksum(file: File, expectedSha256: String): Boolean {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
      val buffer = ByteArray(8192)
      var read: Int
      while (input.read(buffer).also { read = it } != -1) {
        digest.update(buffer, 0, read)
      }
    }
    val actual = digest.digest().joinToString("") { "%02x".format(it) }
    return actual == expectedSha256
  }

  companion object {
    private const val BASE_URL = "https://github.com/MahmoodMahmood/quran_android/releases/download/v1.0.0-asr-model"
    private const val ENCODER_FILENAME = "base-ar-quran-encoder.int8.onnx"
    private const val DECODER_FILENAME = "base-ar-quran-decoder.int8.onnx"
    private const val TOKENS_FILENAME = "base-ar-quran-tokens.txt"
    private const val ENCODER_URL = "$BASE_URL/$ENCODER_FILENAME"
    private const val DECODER_URL = "$BASE_URL/$DECODER_FILENAME"
    private const val TOKENS_URL = "$BASE_URL/$TOKENS_FILENAME"
    private const val MODEL_TOTAL_SIZE_BYTES = 160_639_555L // ~28 + 125 + 0.8 MB

    // SHA-256 checksums for model files (compute with: sha256sum <file>)
    // TODO: compute and fill actual checksums after downloading model files
    private const val ENCODER_SHA256 = ""
    private const val DECODER_SHA256 = ""
    private const val TOKENS_SHA256 = ""
  }
}
