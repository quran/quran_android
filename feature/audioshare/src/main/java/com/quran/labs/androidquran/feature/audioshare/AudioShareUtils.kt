package com.quran.labs.androidquran.feature.audioshare

import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.util.SparseIntArray
import android.widget.Toast
import com.quran.common.util.database.DatabaseUtils
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.timing.SuraTimingDatabaseHandler
import com.quran.labs.androidquran.feature.audioshare.soundfile.CheapSoundFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.buffer
import okio.sink
import okio.source
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

class AudioShareUtils {
  fun createBlockingSharableAudioFile(
    context: Context,
    start: SuraAyah,
    end: SuraAyah,
    qari: QariItem,
    urlFormat: String,
    gaplessDatabase: String
  ): String? {
    return runBlocking {
      createSharableAudioFile(context, start, end, qari, urlFormat, gaplessDatabase)
    }
  }

  suspend fun createSharableAudioFile(
    context: Context,
    start: SuraAyah,
    end: SuraAyah,
    qari: QariItem,
    urlFormat: String,
    gaplessDatabase: String
  ): String? {
    assert(end >= start)

    val audioCacheDirectory = File(
      Environment.getExternalStoragePublicDirectory(
          Environment.DIRECTORY_MUSIC).path + File.separator + "quran_android_cache")

    if (!audioCacheDirectory.exists()) {
      if (!audioCacheDirectory.mkdirs()) {
        Toast.makeText(context, "could not create directory", Toast.LENGTH_SHORT).show()
        return null;
      }
    }

    var sharablePath: String?
    val audioCacheFilePaths = mutableListOf<String>()
    withContext(Dispatchers.IO) {
      val mapArray = getTimingData(start, end, gaplessDatabase)

      val startAyah = start.ayah
      val endAyah = end.ayah
      val startSurahTimingDataArray: SparseIntArray = mapArray[0]
      val endSurahTimingDataArray: SparseIntArray = mapArray[1]

      val isFirstAyahInSurah = startAyah == 1
      val startTimeOfAyahAfterEndAyah = endSurahTimingDataArray[endAyah + 1]
      val isLastAyahInSurah = startTimeOfAyahAfterEndAyah == 0

      val startAyahTime = if (isFirstAyahInSurah) {
        0
      } else {
        startSurahTimingDataArray[startAyah]
      }

      val endAyahTime = if (isLastAyahInSurah) {
        getSurahDuration(context, getSurahAudioPath(urlFormat, end.sura))
      } else {
        startTimeOfAyahAfterEndAyah
      }

      val startAndEndAyahAreInSameSurah = start.sura == end.sura

      if (startAndEndAyahAreInSameSurah) {
        val audioSegmentPath: String = getSurahSegment(
          audioCacheDirectory, getSurahAudioPath(urlFormat, start.sura), startAyahTime, endAyahTime
        )!!
        audioCacheFilePaths.add(audioSegmentPath)
        sharablePath = getRenamedSharableAudioFile(
          qari,
          start,
          end,
          audioSegmentPath,
          audioCacheDirectory.toString(),
          audioCacheFilePaths
        )
        audioCacheFilePaths.clear()
      } else {
        val segmentPaths = mutableListOf<String>()
        val endOfSurah = -1
        val startOfSurah = 0
        val startSegmentPath: String = getSurahSegmentPath(
          context, audioCacheDirectory, urlFormat, start.sura, startAyahTime, endOfSurah
        )
        val lastSegmentPath: String = getSurahSegmentPath(
          context, audioCacheDirectory , urlFormat, end.sura, startOfSurah, endAyahTime
        )

        for (surahIndex in start.sura..end.sura) {
          val isTheFirstSurah = surahIndex == start.sura
          val isMiddleSurah = surahIndex != start.sura && surahIndex != end.sura
          if (isTheFirstSurah) {
            segmentPaths.add(startSegmentPath)
            audioCacheFilePaths.add(startSegmentPath)
            continue
          }
          if (isMiddleSurah) {
            segmentPaths.add(getSurahAudioPath(urlFormat, surahIndex))
            continue
          }
          segmentPaths.add(lastSegmentPath)
          audioCacheFilePaths.add(lastSegmentPath)
        }

        val audioSegmentsWereCreated = segmentPaths.isNotEmpty()

        if (audioSegmentsWereCreated) {
          val (sharableAudioFilePath, cacheUpdates) =
            getMergedAudioFromSegments(audioCacheDirectory, segmentPaths)
          audioCacheFilePaths.addAll(cacheUpdates)
          sharablePath = getRenamedSharableAudioFile(
            qari,
            start,
            end,
            sharableAudioFilePath,
            audioCacheDirectory.toString(),
            audioCacheFilePaths
          )
          audioCacheFilePaths.clear()
        } else {
          sharablePath = null
        }
      }
    }
    return sharablePath
  }

  private fun getSurahSegmentPath(context: Context,
                                  audioCacheDirectory: File,
                                  urlFormat: String,
                                  surah: Int,
                                  startAyahTime: Int,
                                  endAyahTime: Int
  ): String {
    var upperBoundTime = endAyahTime
    val audioFilePath: String = getSurahAudioPath(urlFormat, surah)
    val isFirstSegment = endAyahTime < 0
    if (isFirstSegment) {
      upperBoundTime = getSurahDuration(context, audioFilePath)
    }
    return getSurahSegment(audioCacheDirectory, audioFilePath, startAyahTime, upperBoundTime)!!
  }

  private fun getRenamedSharableAudioFile(
    qari: QariItem,
    start: SuraAyah,
    end: SuraAyah,
    audioSegmentPath: String,
    audioCacheDirectory: String,
    cachedPaths: List<String>
  ): String {
    val newAudioFileName: String =
      qari.path + "_" + start.sura + "-" + start.ayah + "_" + end.sura + "-" + end.ayah
    val newAudioFilePath: String = audioCacheDirectory + File.separator + newAudioFileName + ".mp3"
    File(audioSegmentPath).renameTo(File(newAudioFilePath))
    cachedPaths
      .filter { it != audioSegmentPath }
      .onEach {
        File(it).delete()
      }
    return newAudioFilePath
  }

  private fun getSurahDuration(context: Context, path: String): Int {
    val mmr = MediaMetadataRetriever()
    mmr.setDataSource(context, Uri.parse(path))
    val durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
    return durationStr!!.toInt()
  }

  private fun getSurahAudioPath(urlFormat: String, surah: Int): String {
    return String.format(Locale.US, urlFormat, surah)
  }

  private fun getTimingData(start: SuraAyah, end: SuraAyah, gaplessDatabase: String): ArrayList<SparseIntArray> {
    val db: SuraTimingDatabaseHandler = SuraTimingDatabaseHandler.getDatabaseHandler(gaplessDatabase)
    var firstSurahMap = SparseIntArray()
    var lastSurahMap = SparseIntArray()
    var firstSurahCursor: Cursor? = null
    var lastSurahCursor: Cursor? = null

    try {
      firstSurahCursor = db.getAyahTimings(start.sura)
      firstSurahMap = populateArrayFromCursor(firstSurahCursor)!!
      lastSurahCursor = db.getAyahTimings(end.sura)
      lastSurahMap = populateArrayFromCursor(lastSurahCursor)
    } catch (sqlException: SQLException) {
      Timber.e(sqlException)
    } finally {
      DatabaseUtils.closeCursor(firstSurahCursor)
      DatabaseUtils.closeCursor(lastSurahCursor)
    }

    return ArrayList(
        listOf(firstSurahMap, lastSurahMap))
  }

  private fun populateArrayFromCursor(cursor: Cursor?): SparseIntArray {
    val sparseIntArray = SparseIntArray()
    if (cursor != null && cursor.moveToFirst()) {
      do {
        val ayah = cursor.getInt(1)
        val time = cursor.getInt(2)
        sparseIntArray.put(ayah, time)
      } while (cursor.moveToNext())
    }
    return sparseIntArray
  }

  private fun getSurahSegment(
    audioCacheDirectory: File,
    path: String,
    lowerCut: Int,
    upperCut: Int
  ): String? {
    if (lowerCut == 0 && upperCut == 0) {
      return null
    }
    val tempAudioName = UUID.randomUUID().toString() + ".mp3"
    val destFile = File(audioCacheDirectory.path + File.separator + tempAudioName)
    val soundFile = arrayOfNulls<CheapSoundFile>(1)
    try {
      soundFile[0] = CheapSoundFile.create(path, null)
      val startTime = lowerCut.toFloat() / 1000
      val endTime = upperCut.toFloat() / 1000
      val samplesPerFrame = soundFile[0]?.samplesPerFrame
      val sampleRate = soundFile[0]?.sampleRate
      val avg = sampleRate?.div(samplesPerFrame!!)
      val startFrames = (startTime * avg!!).roundToInt()
      val endFrames = (endTime * avg).roundToInt()
      soundFile[0]?.WriteFile(destFile, startFrames, endFrames - startFrames)
    } catch (e: IOException) {
      e.printStackTrace()
    }
    return destFile.absolutePath
  }

  private fun getMergedAudioFromSegments(
    audioCacheDirectory: File,
    segments: List<String>
  ): Pair<String, List<String>> {
    var mergedAudioPath = segments[0]
    val extraCacheFilePaths = mutableListOf<String>()
    if (segments.size > 1) {
      for (i in 1 until segments.size) {
        val path = mergeAudios(audioCacheDirectory, mergedAudioPath, segments[i])
        if (path != null) {
          mergedAudioPath = path
          extraCacheFilePaths.add(mergedAudioPath)
        }
      }
    }
    return mergedAudioPath to extraCacheFilePaths
  }

  private fun mergeAudios(audioCacheDirectory: File, path1: String, path2: String): String? {
    val tempAudioName = UUID.randomUUID().toString() + ".mp3"
    val destFile = File(audioCacheDirectory.path + File.separator + tempAudioName)
    try {
      val bufferedSink: BufferedSink = destFile.sink().buffer()
      bufferedSink.writeAll(File(path1).source())
      bufferedSink.writeAll(File(path2).source())
      bufferedSink.close()
      return destFile.path
    } catch (e2: FileNotFoundException) {
      e2.printStackTrace()
    } catch (e: IOException) {
      e.printStackTrace()
    }
    return null
  }
}
