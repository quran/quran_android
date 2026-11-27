package com.quran.labs.androidquran.kidsclassroom

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Manages the full offline + streaming Tilawi Kids ASR asset bundle in
 * `File(context.filesDir, "tilawi_kids_asr")`:
 *
 * Root bundle (Stage-4 Offline Batch `4.72%` WER):
 * - `SHA256SUMS`
 * - `vocab.json` (`0..1023`, `<blk>=1024`)
 * - `minshawi_teacher_full.json` (all 6,236 Ayat across 114 Surahs)
 * - `fastconformer_quran_int8.onnx` (`125.2 MB`)
 *
 * Streaming bundle (`0 ms` lookahead stateful cache-aware):
 * - `streaming/SHA256SUMS`
 * - `streaming/streaming_config.json`
 * - `streaming/mel_basis_80x257.bin` (little-endian `float32[80][257]`)
 * - `streaming/fastconformer_quran_streaming_int8.onnx` (`125.4 MB`)
 *
 * Uses resumable `OkHttp` HTTP `Range` downloads with atomic `.tmp` writes.
 * On startup/check, verifies local `fastconformer_quran_int8.onnx` and
 * `streaming/fastconformer_quran_streaming_int8.onnx` against `SHA256SUMS`; if a legacy or
 * corrupted model is detected, it is automatically deleted and re-downloaded.
 */
object AsrModelManager {

  private const val TAG = "AsrModelManager"
  private const val BASE_DIR_NAME = "tilawi_kids_asr"
  private const val STREAMING_SUBDIR = "streaming"

  const val HF_BASE_URL =
    "https://huggingface.co/yousifgamalo/quran-asr-onnx/resolve/main"

  const val OFFLINE_ONNX_NAME = "fastconformer_quran_int8.onnx"
  const val ROOT_SHA256_NAME = "SHA256SUMS"
  const val VOCAB_NAME = "vocab.json"
  const val HUSARY_TEACHER_FULL_JSON_NAME = "husary_muallim_full.json"
  const val MINSHAWI_TEACHER_FULL_JSON_NAME = "minshawi_teacher_full.json"
  const val TEACHER_QARIS_CONFIG_NAME = "teacher_qaris_config.json"
  const val TEACHER_FULL_JSON_NAME = HUSARY_TEACHER_FULL_JSON_NAME
  private const val MINSHAWI_V3_SCHEMA_MARKER = "tilawi-minshawi-teacher-v3-husary-ref"

  const val STREAMING_ONNX_NAME = "fastconformer_quran_streaming_int8.onnx"
  const val STREAMING_CONFIG_NAME = "streaming_config.json"
  const val STREAMING_MEL_BASIS_NAME = "mel_basis_80x257.bin"
  const val STREAMING_SHA256_NAME = "SHA256SUMS"

  private const val MIN_ONNX_BYTES = 10L * 1024L * 1024L
  private const val MIN_VOCAB_BYTES = 2_000L
  private const val MIN_TEACHER_JSON_BYTES = 200_000L
  private const val MIN_TEACHER_CONFIG_BYTES = 100L
  private const val MIN_MEL_BASIS_BYTES = 80L * 257L * 4L // 82,240 bytes
  private const val MIN_CONFIG_BYTES = 64L

  data class ManagedAsset(
    val relativePath: String,
    val url: String,
    val displayLabel: String,
    val minExpectedBytes: Long,
    val expectedSizeHintBytes: Long,
    val sha256ManifestRelPath: String? = null,
    val sha256EntryName: String? = null,
    val isRequiredForCoreOffline: Boolean = true
  )

  val ALL_ASSETS: List<ManagedAsset> = listOf(
    ManagedAsset(
      relativePath = ROOT_SHA256_NAME,
      url = "$HF_BASE_URL/$ROOT_SHA256_NAME",
      displayLabel = "SHA256SUMS (Stage-4)",
      minExpectedBytes = 32L,
      expectedSizeHintBytes = 512L,
      isRequiredForCoreOffline = false
    ),
    ManagedAsset(
      relativePath = VOCAB_NAME,
      url = "$HF_BASE_URL/$VOCAB_NAME",
      displayLabel = "BPE Vocabulary (vocab.json)",
      minExpectedBytes = MIN_VOCAB_BYTES,
      expectedSizeHintBytes = 25_000L,
      sha256ManifestRelPath = ROOT_SHA256_NAME,
      sha256EntryName = VOCAB_NAME,
      isRequiredForCoreOffline = true
    ),
    ManagedAsset(
      relativePath = TEACHER_QARIS_CONFIG_NAME,
      url = "$HF_BASE_URL/$TEACHER_QARIS_CONFIG_NAME",
      displayLabel = "Teacher Reciters Config",
      minExpectedBytes = MIN_TEACHER_CONFIG_BYTES,
      expectedSizeHintBytes = 1_200L,
      isRequiredForCoreOffline = false
    ),
    ManagedAsset(
      relativePath = TEACHER_FULL_JSON_NAME,
      url = "$HF_BASE_URL/$TEACHER_FULL_JSON_NAME",
      displayLabel = "Full 114-Surah Husary Muallim Metadata",
      minExpectedBytes = MIN_TEACHER_JSON_BYTES,
      expectedSizeHintBytes = 24_000_000L,
      isRequiredForCoreOffline = false
    ),
    ManagedAsset(
      relativePath = OFFLINE_ONNX_NAME,
      url = "$HF_BASE_URL/$OFFLINE_ONNX_NAME",
      displayLabel = "Stage-4 Offline ASR (125 MB)",
      minExpectedBytes = MIN_ONNX_BYTES,
      expectedSizeHintBytes = 131_300_000L,
      sha256ManifestRelPath = ROOT_SHA256_NAME,
      sha256EntryName = OFFLINE_ONNX_NAME,
      isRequiredForCoreOffline = true
    ),
    ManagedAsset(
      relativePath = "$STREAMING_SUBDIR/$STREAMING_SHA256_NAME",
      url = "$HF_BASE_URL/$STREAMING_SUBDIR/$STREAMING_SHA256_NAME",
      displayLabel = "Streaming SHA256SUMS",
      minExpectedBytes = 32L,
      expectedSizeHintBytes = 512L,
      isRequiredForCoreOffline = false
    ),
    ManagedAsset(
      relativePath = "$STREAMING_SUBDIR/$STREAMING_CONFIG_NAME",
      url = "$HF_BASE_URL/$STREAMING_SUBDIR/$STREAMING_CONFIG_NAME",
      displayLabel = "Streaming Config",
      minExpectedBytes = MIN_CONFIG_BYTES,
      expectedSizeHintBytes = 4_096L,
      sha256ManifestRelPath = "$STREAMING_SUBDIR/$STREAMING_SHA256_NAME",
      sha256EntryName = STREAMING_CONFIG_NAME,
      isRequiredForCoreOffline = false
    ),
    ManagedAsset(
      relativePath = "$STREAMING_SUBDIR/$STREAMING_MEL_BASIS_NAME",
      url = "$HF_BASE_URL/$STREAMING_SUBDIR/$STREAMING_MEL_BASIS_NAME",
      displayLabel = "Mel Filterbank (80×257)",
      minExpectedBytes = MIN_MEL_BASIS_BYTES,
      expectedSizeHintBytes = MIN_MEL_BASIS_BYTES,
      sha256ManifestRelPath = "$STREAMING_SUBDIR/$STREAMING_SHA256_NAME",
      sha256EntryName = STREAMING_MEL_BASIS_NAME,
      isRequiredForCoreOffline = false
    ),
    ManagedAsset(
      relativePath = "$STREAMING_SUBDIR/$STREAMING_ONNX_NAME",
      url = "$HF_BASE_URL/$STREAMING_SUBDIR/$STREAMING_ONNX_NAME",
      displayLabel = "Live Streaming ASR (125 MB)",
      minExpectedBytes = MIN_ONNX_BYTES,
      expectedSizeHintBytes = 131_500_000L,
      sha256ManifestRelPath = "$STREAMING_SUBDIR/$STREAMING_SHA256_NAME",
      sha256EntryName = STREAMING_ONNX_NAME,
      isRequiredForCoreOffline = false
    )
  )

  private val downloadMutex = Mutex()

  private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(25, TimeUnit.SECONDS)
      .readTimeout(90, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .followRedirects(true)
      .followSslRedirects(true)
      .retryOnConnectionFailure(true)
      .build()
  }

  fun getBaseDir(context: Context): File =
    File(context.applicationContext.filesDir, BASE_DIR_NAME).apply { if (!exists()) mkdirs() }

  fun getStreamingDir(context: Context): File =
    File(getBaseDir(context), STREAMING_SUBDIR).apply { if (!exists()) mkdirs() }

  fun getAssetFile(context: Context, relativePath: String): File =
    File(getBaseDir(context), relativePath)

  fun getOfflineOnnxFile(context: Context): File = getAssetFile(context, OFFLINE_ONNX_NAME)
  fun getVocabFile(context: Context): File = getAssetFile(context, VOCAB_NAME)
  fun getTeacherQarisConfigFile(context: Context): File = getAssetFile(context, TEACHER_QARIS_CONFIG_NAME)
  fun getTeacherFullJsonFile(
    context: Context,
    qari: KidsTeacherQari = MinshawiJuz30Repository.getActiveTeacherQari()
  ): File = getAssetFile(context, qari.fullJsonFileName)
  fun getRootSha256File(context: Context): File = getAssetFile(context, ROOT_SHA256_NAME)

  fun getStreamingOnnxFile(context: Context): File =
    getAssetFile(context, "$STREAMING_SUBDIR/$STREAMING_ONNX_NAME")

  fun getStreamingConfigFile(context: Context): File =
    getAssetFile(context, "$STREAMING_SUBDIR/$STREAMING_CONFIG_NAME")

  fun getStreamingMelBasisFile(context: Context): File =
    getAssetFile(context, "$STREAMING_SUBDIR/$STREAMING_MEL_BASIS_NAME")

  fun getStreamingSha256File(context: Context): File =
    getAssetFile(context, "$STREAMING_SUBDIR/$STREAMING_SHA256_NAME")

  fun isOfflineStage4Ready(context: Context): Boolean {
    val onnx = getOfflineOnnxFile(context)
    val vocab = getVocabFile(context)
    return onnx.exists() && onnx.length() >= MIN_ONNX_BYTES &&
      vocab.exists() && vocab.length() >= MIN_VOCAB_BYTES
  }

  fun isStreamingAsrReady(context: Context): Boolean {
    val onnx = getStreamingOnnxFile(context)
    val cfg = getStreamingConfigFile(context)
    val mel = getStreamingMelBasisFile(context)
    val vocab = getVocabFile(context)
    return onnx.exists() && onnx.length() >= MIN_ONNX_BYTES &&
      cfg.exists() && cfg.length() >= MIN_CONFIG_BYTES &&
      mel.exists() && mel.length() >= MIN_MEL_BASIS_BYTES &&
      vocab.exists() && vocab.length() >= MIN_VOCAB_BYTES
  }

  fun isTeacherFullJsonReady(
    context: Context,
    qari: KidsTeacherQari = MinshawiJuz30Repository.getActiveTeacherQari()
  ): Boolean {
    val f = getTeacherFullJsonFile(context, qari)
    if (!f.exists() || f.length() < MIN_TEACHER_JSON_BYTES) return false
    if (qari == KidsTeacherQari.MINSHAWI_TEACHER && !isMinshawiFileUpToDateV3(f)) {
      return false
    }
    return true
  }

  private fun isMinshawiFileUpToDateV3(file: File): Boolean {
    return try {
      val headerBuf = ByteArray(512)
      val read = FileInputStream(file).use { it.read(headerBuf) }
      if (read <= 0) return false
      String(headerBuf, 0, read, Charsets.UTF_8).contains(MINSHAWI_V3_SCHEMA_MARKER)
    } catch (_: Throwable) {
      false
    }
  }

  /**
   * Downloads the full 114-Surah JSON for [qari] if missing (or if an older pre-v3 Minshawi JSON
   * is cached on disk), then reloads [MinshawiJuz30Repository] in place.
   */
  suspend fun ensureTeacherFullJsonDownloaded(
    context: Context,
    qari: KidsTeacherQari,
    onProgress: (AssetDownloadProgress) -> Unit = {}
  ): Boolean = downloadMutex.withLock {
    withContext(Dispatchers.IO) {
      val appCtx = context.applicationContext
      val dest = getTeacherFullJsonFile(appCtx, qari)
      if (qari == KidsTeacherQari.MINSHAWI_TEACHER && dest.exists() && !isMinshawiFileUpToDateV3(dest)) {
        dest.delete()
        File(dest.parentFile, "${dest.name}.tmp").delete()
      }
      if (isTeacherFullJsonReady(appCtx, qari)) {
        MinshawiJuz30Repository.reloadIfFullDatasetAvailable(appCtx)
        return@withContext true
      }
      val approxBytes = if (qari == KidsTeacherQari.HUSARY_MUALLIM) 24_000_000L else 22_600_000L
      val label = if (qari == KidsTeacherQari.HUSARY_MUALLIM) {
        "Full 114-Surah Husary Muallim Metadata"
      } else {
        "Full 114-Surah Minshawi Teacher Metadata"
      }
      try {
        downloadFileResumable(
          url = "$HF_BASE_URL/${qari.fullJsonFileName}",
          destination = dest,
          forceOverwrite = false
        ) { done, total ->
          val effTotal = if (total > 0L) total else approxBytes
          onProgress(
            AssetDownloadProgress(
              isReady = isOfflineStage4Ready(appCtx),
              isDownloading = true,
              currentFileName = label,
              bytesDownloaded = done,
              totalBytes = maxOf(effTotal, done)
            )
          )
        }
        MinshawiJuz30Repository.reloadIfFullDatasetAvailable(appCtx)
        onProgress(
          AssetDownloadProgress(
            isReady = isOfflineStage4Ready(appCtx),
            isDownloading = false,
            currentFileName = label,
            bytesDownloaded = dest.length().coerceAtLeast(1L),
            totalBytes = dest.length().coerceAtLeast(1L)
          )
        )
        isTeacherFullJsonReady(appCtx, qari)
      } catch (t: Throwable) {
        Log.e(TAG, "Failed downloading ${qari.fullJsonFileName}", t)
        onProgress(
          AssetDownloadProgress(
            isReady = isOfflineStage4Ready(appCtx),
            isDownloading = false,
            currentFileName = label,
            errorMessage = t.message ?: "Download failed for $label"
          )
        )
        false
      }
    }
  }

  fun isFullBundleReady(context: Context): Boolean =
    isOfflineStage4Ready(context) && isStreamingAsrReady(context) && isTeacherFullJsonReady(context)

  /**
   * Parses standard GNU `sha256sum` format lines: `<64-hex-sha256>  <filename>` or `<hash> *<file>`.
   */
  fun parseSha256Sums(content: String): Map<String, String> {
    val map = LinkedHashMap<String, String>()
    content.lineSequence().forEach { rawLine ->
      val line = rawLine.trim()
      if (line.isEmpty() || line.startsWith("#")) return@forEach
      val parts = line.split(Regex("\\s+"), limit = 2)
      if (parts.size == 2) {
        val hash = parts[0].lowercase()
        val rawName = parts[1].trimStart('*').trim()
        val fileNameOnly = rawName.substringAfterLast('/')
        if (hash.length == 64 && hash.all { it in '0'..'9' || it in 'a'..'f' }) {
          map[rawName] = hash
          map[fileNameOnly] = hash
        }
      }
    }
    return map
  }

  /**
   * Computes lowercase hex SHA-256 of [file] in 64 KB chunks.
   */
  fun computeSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buf = ByteArray(64 * 1024)
    FileInputStream(file).use { input ->
      while (true) {
        val read = input.read(buf)
        if (read <= 0) break
        digest.update(buf, 0, read)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  /**
   * Checks a local model file against an expected SHA-256 map. If a mismatch is found (legacy
   * model cached from an earlier app version), deletes the stale file and any `.tmp` fragment.
   *
   * @return `true` if the local file exists and matches (or no hash entry exists for it),
   *   `false` if it was missing or deleted due to SHA-256 mismatch.
   */
  fun verifyOrInvalidateLegacyFile(
    targetFile: File,
    minExpectedBytes: Long,
    sha256Map: Map<String, String>,
    entryName: String?
  ): Boolean {
    if (!targetFile.exists() || targetFile.length() < minExpectedBytes) {
      return false
    }
    val expectedHash = entryName?.let { sha256Map[it] } ?: return true
    val actualHash = try {
      computeSha256(targetFile)
    } catch (t: Throwable) {
      Log.w(TAG, "SHA-256 computation failed for ${targetFile.name}", t)
      return false
    }
    if (!actualHash.equals(expectedHash, ignoreCase = true)) {
      Log.w(
        TAG,
        "Legacy/corrupt model detected for ${targetFile.name}: expected=$expectedHash actual=$actualHash. Deleting for replacement."
      )
      targetFile.delete()
      File(targetFile.parentFile, "${targetFile.name}.tmp").delete()
      return false
    }
    return true
  }

  /**
   * Checks on startup whether the cached offline/streaming ONNX models match the latest
   * `SHA256SUMS` manifest. If offline, silently keeps already-present models intact.
   *
   * @return `true` if any legacy model was invalidated and requires re-download.
   */
  suspend fun checkAndInvalidateLegacyModelsOnStartup(context: Context): Boolean =
    withContext(Dispatchers.IO) {
      var invalidated = false
      val appCtx = context.applicationContext
      val rootShaFile = getRootSha256File(appCtx)
      // Best-effort refresh of SHA256SUMS when network is reachable
      try {
        downloadFileResumable(
          url = "$HF_BASE_URL/$ROOT_SHA256_NAME",
          destination = rootShaFile,
          forceOverwrite = true,
          onBytes = { _, _ -> }
        )
      } catch (_: Throwable) {
        // Offline — fall back to cached SHA256SUMS if present
      }

      if (rootShaFile.exists()) {
        val rootMap = parseSha256Sums(rootShaFile.readText())
        val offlineModel = getOfflineOnnxFile(appCtx)
        if (offlineModel.exists()) {
          val ok = verifyOrInvalidateLegacyFile(
            targetFile = offlineModel,
            minExpectedBytes = MIN_ONNX_BYTES,
            sha256Map = rootMap,
            entryName = OFFLINE_ONNX_NAME
          )
          if (!ok) invalidated = true
        }
      }

      val streamShaFile = getStreamingSha256File(appCtx)
      try {
        downloadFileResumable(
          url = "$HF_BASE_URL/$STREAMING_SUBDIR/$STREAMING_SHA256_NAME",
          destination = streamShaFile,
          forceOverwrite = true,
          onBytes = { _, _ -> }
        )
      } catch (_: Throwable) {
        // Offline
      }

      if (streamShaFile.exists()) {
        val streamMap = parseSha256Sums(streamShaFile.readText())
        val streamModel = getStreamingOnnxFile(appCtx)
        if (streamModel.exists()) {
          val ok = verifyOrInvalidateLegacyFile(
            targetFile = streamModel,
            minExpectedBytes = MIN_ONNX_BYTES,
            sha256Map = streamMap,
            entryName = STREAMING_ONNX_NAME
          )
          if (!ok) invalidated = true
        }
      }

      invalidated
    }

  /**
   * Ensures all offline Stage-4, streaming ASR, and 114-Surah Minshawi metadata assets are
   * downloaded, SHA-256 verified, and ready for 100% offline use.
   */
  suspend fun ensureAllAssetsDownloaded(
    context: Context,
    includeStreaming: Boolean = true,
    onProgress: (AssetDownloadProgress) -> Unit
  ): Boolean = downloadMutex.withLock {
    withContext(Dispatchers.IO) {
      val appCtx = context.applicationContext
      getBaseDir(appCtx)
      getStreamingDir(appCtx)

      val targetAssets = if (includeStreaming) {
        ALL_ASSETS
      } else {
        ALL_ASSETS.filter { !it.relativePath.startsWith("$STREAMING_SUBDIR/") }
      }

      val shaMaps = HashMap<String, Map<String, String>>()
      val totalAssets = targetAssets.size

      // Step 1: Fetch SHA256SUMS manifests first (best-effort)
      val manifests = targetAssets.filter { it.relativePath.endsWith("SHA256SUMS") }
      for (manifest in manifests) {
        val dest = getAssetFile(appCtx, manifest.relativePath)
        try {
          downloadFileResumable(
            url = manifest.url,
            destination = dest,
            forceOverwrite = true,
            onBytes = { _, _ -> }
          )
        } catch (t: Throwable) {
          Log.w(TAG, "Unable to refresh ${manifest.relativePath}, using cached copy if present", t)
        }
        if (dest.exists()) {
          shaMaps[manifest.relativePath] = parseSha256Sums(dest.readText())
        }
      }

      // Step 2: Verify or download each payload asset
      val payloadAssets = targetAssets.filter { !it.relativePath.endsWith("SHA256SUMS") }
      val totalPayloadBytesApprox = payloadAssets.sumOf { asset ->
        val f = getAssetFile(appCtx, asset.relativePath)
        if (f.exists() && f.length() >= asset.minExpectedBytes) f.length() else asset.expectedSizeHintBytes
      }.coerceAtLeast(1L)
      var cumulativeCompletedBytes = 0L

      for ((index, asset) in payloadAssets.withIndex()) {
        val dest = getAssetFile(appCtx, asset.relativePath)
        val manifestMap = asset.sha256ManifestRelPath?.let { shaMaps[it] }.orEmpty()

        val alreadyValid = verifyOrInvalidateLegacyFile(
          targetFile = dest,
          minExpectedBytes = asset.minExpectedBytes,
          sha256Map = manifestMap,
          entryName = asset.sha256EntryName
        )

        if (alreadyValid) {
          cumulativeCompletedBytes += dest.length()
          onProgress(
            AssetDownloadProgress(
              isReady = false,
              isDownloading = true,
              currentFileName = asset.displayLabel,
              bytesDownloaded = cumulativeCompletedBytes,
              totalBytes = maxOf(totalPayloadBytesApprox, cumulativeCompletedBytes)
            )
          )
          continue
        }

        try {
          downloadFileResumable(
            url = asset.url,
            destination = dest,
            forceOverwrite = false
          ) { bytesDone, totalBytes ->
            val effectiveTotal = if (totalBytes > 0L) totalBytes else asset.expectedSizeHintBytes
            val dynamicTotal = maxOf(totalPayloadBytesApprox, cumulativeCompletedBytes + effectiveTotal)
            onProgress(
              AssetDownloadProgress(
                isReady = false,
                isDownloading = true,
                currentFileName = asset.displayLabel,
                bytesDownloaded = cumulativeCompletedBytes + bytesDone,
                totalBytes = maxOf(dynamicTotal, cumulativeCompletedBytes + bytesDone)
              )
            )
          }

          // Post-download SHA-256 check when manifest is available
          if (manifestMap.isNotEmpty() && asset.sha256EntryName != null) {
            val verified = verifyOrInvalidateLegacyFile(
              targetFile = dest,
              minExpectedBytes = asset.minExpectedBytes,
              sha256Map = manifestMap,
              entryName = asset.sha256EntryName
            )
            if (!verified) {
              throw IOException("SHA-256 verification failed for ${asset.relativePath}")
            }
          }
          cumulativeCompletedBytes += dest.length()
        } catch (t: Throwable) {
          Log.e(TAG, "Failed downloading ${asset.relativePath} ($index/$totalAssets)", t)
          onProgress(
            AssetDownloadProgress(
              isReady = isOfflineStage4Ready(appCtx),
              isDownloading = false,
              currentFileName = asset.displayLabel,
              bytesDownloaded = cumulativeCompletedBytes,
              totalBytes = totalPayloadBytesApprox,
              errorMessage = t.message ?: "Download failed for ${asset.displayLabel}"
            )
          )
          // Core offline models are mandatory; streaming failure shouldn't block Stage-4 offline grading
          if (asset.isRequiredForCoreOffline) {
            return@withContext false
          }
        }
      }

      MinshawiJuz30Repository.reloadIfFullDatasetAvailable(appCtx)
      val ready = isOfflineStage4Ready(appCtx)
      if (ready) {
        onProgress(
          AssetDownloadProgress(
            isReady = true,
            isDownloading = false,
            currentFileName = if (isStreamingAsrReady(appCtx)) {
              "Stage-4 + Streaming ASR Ready"
            } else {
              "Stage-4 Offline ASR Ready"
            },
            bytesDownloaded = cumulativeCompletedBytes.coerceAtLeast(1L),
            totalBytes = cumulativeCompletedBytes.coerceAtLeast(1L)
          )
        )
      }
      ready
    }
  }

  private fun downloadFileResumable(
    url: String,
    destination: File,
    forceOverwrite: Boolean,
    onBytes: (bytesDone: Long, totalBytes: Long) -> Unit
  ) {
    destination.parentFile?.mkdirs()
    val tempFile = File(destination.parentFile, "${destination.name}.tmp")
    if (forceOverwrite && tempFile.exists()) {
      tempFile.delete()
    }

    val existingOffset = if (!forceOverwrite && tempFile.exists()) tempFile.length() else 0L
    val reqBuilder = Request.Builder()
      .url(url)
      .header("User-Agent", "TilawiKidsQuranAndroid/2.0")
    if (existingOffset > 0L) {
      reqBuilder.header("Range", "bytes=$existingOffset-")
    }

    httpClient.newCall(reqBuilder.build()).execute().use { response ->
      val code = response.code
      if (code == 416) {
        // Range not satisfiable -> restart cleanly
        tempFile.delete()
        downloadFileResumable(url, destination, forceOverwrite = true, onBytes = onBytes)
        return
      }
      if (!response.isSuccessful) {
        throw IOException("HTTP $code downloading ${destination.name}")
      }

      val body = response.body ?: throw IOException("Empty response body for ${destination.name}")
      val isPartial = code == 206 && existingOffset > 0L
      val appendStart = if (isPartial) existingOffset else 0L
      val contentLen = body.contentLength()
      val totalBytes = if (contentLen > 0L) appendStart + contentLen else -1L

      FileOutputStream(tempFile, isPartial).use { fos ->
        val buffer = ByteArray(64 * 1024)
        var written = appendStart
        var lastEmitMs = 0L
        body.byteStream().use { input ->
          while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            fos.write(buffer, 0, read)
            written += read
            val now = System.currentTimeMillis()
            if (now - lastEmitMs >= 100L) {
              onBytes(written, totalBytes)
              lastEmitMs = now
            }
          }
        }
        fos.flush()
        onBytes(written, totalBytes)
      }
    }

    if (destination.exists()) {
      destination.delete()
    }
    if (!tempFile.renameTo(destination)) {
      tempFile.copyTo(destination, overwrite = true)
      tempFile.delete()
    }
  }
}
