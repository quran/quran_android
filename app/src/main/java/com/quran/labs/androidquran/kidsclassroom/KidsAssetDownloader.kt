package com.quran.labs.androidquran.kidsclassroom

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * First-launch offline asset downloader for Children Learning Mode (`tilawi_kids_asr`):
 * - `fastconformer_quran_int8.onnx` (125.2 MB)
 * - `vocab.json` (`Map<String, Int>` `0..1024`, `ctcTokens: Array<String>(1025)`, `blankId = vocab["<blk>"] ?: 1024`)
 * - `minshawi_teacher_full.json` (all 6,236 verses, Surahs `1..114`)
 *
 * Downloads missing files once via `OkHttp` into `File(context.filesDir, "tilawi_kids_asr")`
 * using `.tmp` partial files with HTTP `Range` resume and atomic rename.
 */
object KidsAssetDownloader {

  const val SUBDIR_NAME = "tilawi_kids_asr"

  const val ONNX_FILE_NAME = "fastconformer_quran_int8.onnx"
  const val VOCAB_FILE_NAME = "vocab.json"
  const val HUSARY_TEACHER_FULL_JSON_FILE_NAME = "husary_muallim_full.json"
  const val MINSHAWI_TEACHER_FULL_JSON_FILE_NAME = "minshawi_teacher_full.json"
  const val TEACHER_QARIS_CONFIG_FILE_NAME = "teacher_qaris_config.json"
  const val TEACHER_FULL_JSON_FILE_NAME = HUSARY_TEACHER_FULL_JSON_FILE_NAME
  const val LEGACY_QURAN_JSON_FILE_NAME = "quran.json"

  private const val HF_BASE_URL = "https://huggingface.co/yousifgamalo/quran-asr-onnx/resolve/main/"

  const val ONNX_URL = HF_BASE_URL + ONNX_FILE_NAME
  const val VOCAB_URL = HF_BASE_URL + VOCAB_FILE_NAME
  const val TEACHER_FULL_JSON_URL = HF_BASE_URL + TEACHER_FULL_JSON_FILE_NAME

  const val VOCAB_ARRAY_SIZE = 1025
  const val DEFAULT_BLANK_ID = 1024

  internal const val MIN_ONNX_BYTES = 120_000_000L
  private const val EXPECTED_ONNX_BYTES = 131_305_000L
  internal const val MIN_VOCAB_BYTES = 5_000L
  private const val EXPECTED_VOCAB_BYTES = 18_000L
  internal const val MIN_TEACHER_FULL_JSON_BYTES = 1_500_000L
  private const val EXPECTED_TEACHER_FULL_JSON_BYTES = 24_000_000L

  data class VocabBundle(
    val vocabMap: Map<String, Int>,
    val ctcTokens: Array<String>,
    val blankId: Int
  )

  private data class RemoteAssetSpec(
    val fileName: String,
    val url: String,
    val minValidBytes: Long,
    val approxTotalBytes: Long
  )

  private val ASSETS = listOf(
    RemoteAssetSpec(
      fileName = VOCAB_FILE_NAME,
      url = VOCAB_URL,
      minValidBytes = MIN_VOCAB_BYTES,
      approxTotalBytes = EXPECTED_VOCAB_BYTES
    ),
    RemoteAssetSpec(
      fileName = TEACHER_FULL_JSON_FILE_NAME,
      url = TEACHER_FULL_JSON_URL,
      minValidBytes = MIN_TEACHER_FULL_JSON_BYTES,
      approxTotalBytes = EXPECTED_TEACHER_FULL_JSON_BYTES
    ),
    RemoteAssetSpec(
      fileName = ONNX_FILE_NAME,
      url = ONNX_URL,
      minValidBytes = MIN_ONNX_BYTES,
      approxTotalBytes = EXPECTED_ONNX_BYTES
    )
  )

  private val downloadMutex = Mutex()

  private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(90, TimeUnit.SECONDS)
      .followRedirects(true)
      .followSslRedirects(true)
      .build()
  }

  @JvmStatic
  fun getAssetDirectory(context: Context): File {
    val dir = File(context.filesDir, SUBDIR_NAME)
    if (!dir.exists()) {
      dir.mkdirs()
    }
    return dir
  }

  @JvmStatic
  fun getOnnxModelFile(context: Context): File =
    File(getAssetDirectory(context), ONNX_FILE_NAME)

  @JvmStatic
  fun getVocabFile(context: Context): File =
    File(getAssetDirectory(context), VOCAB_FILE_NAME)

  @JvmStatic
  fun getTeacherFullJsonFile(context: Context): File =
    getTeacherFullJsonFile(context, MinshawiJuz30Repository.getActiveTeacherQari())

  @JvmStatic
  fun getTeacherFullJsonFile(context: Context, qari: KidsTeacherQari): File =
    File(getAssetDirectory(context), qari.fullJsonFileName)

  @JvmStatic
  fun getLegacyQuranJsonFile(context: Context): File =
    File(getAssetDirectory(context), LEGACY_QURAN_JSON_FILE_NAME)

  @JvmStatic
  fun areCoreEngineAssetsReady(context: Context): Boolean {
    val onnx = getOnnxModelFile(context)
    val vocab = getVocabFile(context)
    return onnx.exists() && onnx.length() >= MIN_ONNX_BYTES &&
      vocab.exists() && vocab.length() >= MIN_VOCAB_BYTES
  }

  @JvmStatic
  fun isTeacherFullJsonReady(context: Context): Boolean =
    isTeacherFullJsonReady(context, MinshawiJuz30Repository.getActiveTeacherQari())

  @JvmStatic
  fun isTeacherFullJsonReady(context: Context, qari: KidsTeacherQari): Boolean =
    AsrModelManager.isTeacherFullJsonReady(context, qari)

  @JvmStatic
  fun areAllAssetsReady(context: Context): Boolean {
    return areCoreEngineAssetsReady(context) && isTeacherFullJsonReady(context)
  }

  /**
   * Parses `vocab.json` (`Map<String, Int>` `0..1024`) and builds:
   * - `ctcTokens: Array<String>(1025)` where `ctcTokens[id] = piece`
   * - `blankId = vocab["<blk>"] ?: 1024`
   */
  @JvmStatic
  fun parseVocabBundle(jsonText: String): VocabBundle {
    val trimmed = jsonText.trim()
    val ctcTokens = Array(VOCAB_ARRAY_SIZE) { "" }
    val vocabMap = LinkedHashMap<String, Int>(VOCAB_ARRAY_SIZE)

    if (trimmed.startsWith("[")) {
      val arr = JSONArray(trimmed)
      val limit = minOf(arr.length(), VOCAB_ARRAY_SIZE)
      for (id in 0 until limit) {
        val piece = arr.optString(id, "")
        ctcTokens[id] = piece
        if (piece.isNotEmpty()) {
          vocabMap[piece] = id
        }
      }
      val blankId = vocabMap["<blk>"] ?: DEFAULT_BLANK_ID
      return VocabBundle(vocabMap = vocabMap, ctcTokens = ctcTokens, blankId = blankId)
    }

    val obj = JSONObject(trimmed)
    if (obj.has("tokens") && obj.opt("tokens") is JSONArray) {
      val arr = obj.getJSONArray("tokens")
      val limit = minOf(arr.length(), VOCAB_ARRAY_SIZE)
      for (id in 0 until limit) {
        val piece = arr.optString(id, "")
        ctcTokens[id] = piece
        if (piece.isNotEmpty()) {
          vocabMap[piece] = id
        }
      }
      val blankId = vocabMap["<blk>"] ?: DEFAULT_BLANK_ID
      return VocabBundle(vocabMap = vocabMap, ctcTokens = ctcTokens, blankId = blankId)
    }

    val keys = obj.keys()
    while (keys.hasNext()) {
      val k = keys.next()
      when (val v = obj.opt(k)) {
        is Number -> {
          val id = v.toInt()
          if (id in 0 until VOCAB_ARRAY_SIZE) {
            ctcTokens[id] = k
            vocabMap[k] = id
          }
        }
        is String -> {
          val id = k.toIntOrNull()
          if (id != null && id in 0 until VOCAB_ARRAY_SIZE) {
            ctcTokens[id] = v
            vocabMap[v] = id
          }
        }
      }
    }

    val blankId = vocabMap["<blk>"] ?: DEFAULT_BLANK_ID
    return VocabBundle(vocabMap = vocabMap, ctcTokens = ctcTokens, blankId = blankId)
  }

  fun loadVocabBundle(context: Context): VocabBundle? {
    val file = getVocabFile(context)
    if (!file.exists() || file.length() <= 0L) return null
    return try {
      parseVocabBundle(file.readText(Charsets.UTF_8))
    } catch (t: Throwable) {
      Timber.e(t, "Failed to parse vocab.json")
      null
    }
  }

  /**
   * Downloads any missing offline Stage-4 + streaming ASR + 114-Surah Minshawi assets via
   * [AsrModelManager], automatically validating `SHA256SUMS` and replacing legacy cached models.
   */
  suspend fun ensureAllAssetsDownloaded(
    context: Context,
    onProgress: (AssetDownloadProgress) -> Unit = {}
  ): Boolean = AsrModelManager.ensureAllAssetsDownloaded(
    context = context,
    includeStreaming = true,
    onProgress = onProgress
  )
}
