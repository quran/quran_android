package com.quran.labs.androidquran.kidsclassroom

import android.content.Context
import java.io.File

/**
 * Compatibility facade delegating to [KidsAssetDownloader] for offline AI teacher assets
 * stored in `File(context.filesDir, "tilawi_kids_asr")`:
 * 1. `fastconformer_quran_int8.onnx` (125.2 MB)
 * 2. `vocab.json` (`0..1024` BPE pieces + `<blk>`)
 * 3. `minshawi_teacher_full.json` (all 6,236 verses, Surahs `1..114`)
 */
object KidsAsrAssetDownloader {

  const val ONNX_FILE_NAME = KidsAssetDownloader.ONNX_FILE_NAME
  const val VOCAB_FILE_NAME = KidsAssetDownloader.VOCAB_FILE_NAME
  const val TEACHER_FULL_JSON_FILE_NAME = KidsAssetDownloader.TEACHER_FULL_JSON_FILE_NAME
  const val QURAN_JSON_FILE_NAME = KidsAssetDownloader.LEGACY_QURAN_JSON_FILE_NAME

  @JvmStatic
  fun getAssetDirectory(context: Context): File =
    KidsAssetDownloader.getAssetDirectory(context)

  @JvmStatic
  fun getOnnxModelFile(context: Context): File =
    KidsAssetDownloader.getOnnxModelFile(context)

  @JvmStatic
  fun getVocabFile(context: Context): File =
    KidsAssetDownloader.getVocabFile(context)

  @JvmStatic
  fun getTeacherFullJsonFile(context: Context): File =
    KidsAssetDownloader.getTeacherFullJsonFile(context)

  @JvmStatic
  fun getQuranJsonFile(context: Context): File =
    KidsAssetDownloader.getLegacyQuranJsonFile(context)

  @JvmStatic
  fun areCoreEngineAssetsReady(context: Context): Boolean =
    KidsAssetDownloader.areCoreEngineAssetsReady(context)

  @JvmStatic
  fun isTeacherFullJsonReady(context: Context): Boolean =
    KidsAssetDownloader.isTeacherFullJsonReady(context)

  @JvmStatic
  fun areAllAssetsReady(context: Context): Boolean =
    KidsAssetDownloader.areAllAssetsReady(context)

  suspend fun ensureAllAssetsDownloaded(
    context: Context,
    onProgress: (AssetDownloadProgress) -> Unit
  ): Boolean = KidsAssetDownloader.ensureAllAssetsDownloaded(context, onProgress)
}
