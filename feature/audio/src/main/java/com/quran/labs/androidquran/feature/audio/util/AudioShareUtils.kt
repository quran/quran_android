package com.quran.labs.androidquran.feature.audio.util

import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.util.SparseIntArray
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.feature.audio.database.SuraTimingDatabaseHandler
import com.quran.labs.androidquran.feature.audio.database.SuraTimingDatabaseHandler.DatabaseUtils.closeCursor
import com.quran.labs.androidquran.feature.audio.util.soundfile.CheapSoundFile
import kotlinx.coroutines.*
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

  val audioCacheDirectory = File(
      Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).path +
          File.separator + "quran_android_cache")
  var audioCacheFilePaths: MutableList<String> = ArrayList()
  var selectedQari: QariItem? = null
  var selectedStartSuraAyah: SuraAyah? = null
  var selectedEndSuraAyah: SuraAyah? = null


  data class ProperAyahOrder(val start: SuraAyah, val end: SuraAyah)

  private fun getReorderedAyahPair(start: SuraAyah, end: SuraAyah): ProperAyahOrder {
    val (actualStart, actualEnd) = if (start <= end) {
      start to end
    } else {
      end to start
    }
    return ProperAyahOrder(actualStart, actualEnd)
  }


  fun createSharableAudioFile(context: Context, start: SuraAyah, end: SuraAyah, qari: QariItem, urlFormat: String, gaplessDatabase: String): String? {
    selectedStartSuraAyah = getReorderedAyahPair(start, end).start
    selectedEndSuraAyah = getReorderedAyahPair(start, end).end
    selectedQari = qari

    var sharablePath: String? = null

    return runBlocking {
      GlobalScope.launch(Dispatchers.IO) {
        val mapArray = async {
          getTimingData(start, end, gaplessDatabase)
        }.await()

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
          getSurahDuration(context,
              getSurahAudioPath(urlFormat, end.sura)!!)
        } else {
          startTimeOfAyahAfterEndAyah
        }

        val startAndEndAyahAreInSameSurah = start.sura == end.sura

        if (startAndEndAyahAreInSameSurah) {
          val audioSegmentPath: String = getSurahSegment(
              getSurahAudioPath(urlFormat, start.sura)!!, startAyahTime,
              endAyahTime)!!
          audioCacheFilePaths.add(audioSegmentPath)
          sharablePath = getRenamedSharableAudioFile(audioSegmentPath)
        } else {
          val segmentPaths = java.util.ArrayList<String>()
          val endOfSurah = -1
          val startOfSurah = 0
          val startSegmentPath: String = getSurahSegmentPath(context, urlFormat, start.sura,
              startAyahTime, endOfSurah)
          val lastSegmentPath: String = getSurahSegmentPath(context, urlFormat, end.sura,
              startOfSurah, endAyahTime)

          for (surahIndex in start.sura..end.sura) {
            val isTheFirstSurah = surahIndex == start.sura
            val isMiddleSurah = surahIndex != start.sura && surahIndex != end.sura
            if (isTheFirstSurah) {
              segmentPaths.add(startSegmentPath)
              audioCacheFilePaths.add(startSegmentPath)
              continue
            }
            if (isMiddleSurah) {
              segmentPaths.add(getSurahAudioPath(urlFormat, surahIndex)!!)
              continue
            }
            segmentPaths.add(lastSegmentPath)
            audioCacheFilePaths.add(lastSegmentPath)
          }

          val audioSegmentsWereCreated = segmentPaths.isNotEmpty()

          if (audioSegmentsWereCreated) {
            val sharableAudioFilePath: String = getMergedAudioFromSegments(
                segmentPaths)
            sharablePath = getRenamedSharableAudioFile(sharableAudioFilePath)
          } else {
            sharablePath = null
          }
        }
      }.join()
      return@runBlocking sharablePath
    }

  }

  private fun getSurahSegmentPath(context: Context, urlFormat: String, surah: Int,
                                  startAyahTime: Int, endAyahTime: Int): String {
    var upperBoundTime = endAyahTime
    val audioFilePath: String = getSurahAudioPath(urlFormat, surah)!!
    val isFirstSegment = endAyahTime < 0
    if (isFirstSegment) {
      upperBoundTime = getSurahDuration(context, audioFilePath)
    }
    return getSurahSegment(audioFilePath, startAyahTime, upperBoundTime)!!
  }

  private fun getRenamedSharableAudioFile(audioSegmentPath: String): String {
    val newAudioFileName: String = selectedQari!!.path + "_" + selectedStartSuraAyah!!.sura + "-" + selectedStartSuraAyah!!.ayah + "_" + selectedEndSuraAyah!!.sura + "-" + selectedEndSuraAyah!!.ayah
    val newAudioFilePath: String = audioCacheDirectory.toString() + File.separator + newAudioFileName + ".mp3"
    File(audioSegmentPath).renameTo(File(newAudioFilePath))
    audioCacheFilePaths.remove(audioSegmentPath)
    for (path in audioCacheFilePaths) {
      File(path).delete()
    }
    audioCacheFilePaths.clear()
    return newAudioFilePath
  }

  private fun getSurahDuration(context: Context, path: String): Int {
    val mmr = MediaMetadataRetriever()
    mmr.setDataSource(context, Uri.parse(path))
    val durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
    return durationStr!!.toInt()
  }

  private fun getSurahAudioPath(urlFormat: String, surah: Int): String? {
    return String.format(Locale.US, urlFormat, surah)
  }

  private fun getTimingData(start: SuraAyah, end: SuraAyah, gaplessDatabase: String): ArrayList<SparseIntArray> {
    val databasePath = gaplessDatabase!!

    val db: SuraTimingDatabaseHandler = SuraTimingDatabaseHandler.getDatabaseHandler(
        databasePath)
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
      closeCursor(firstSurahCursor)
      closeCursor(lastSurahCursor)
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

  private fun getSurahSegment(path: String, lowerCut: Int, upperCut: Int): String? {
    if (lowerCut == 0 && upperCut == 0) {
      return null
    }
    val tempAudioName = UUID.randomUUID().toString() + ".mp3"
    val destFile = File(audioCacheDirectory.path + File.separator + tempAudioName)
    val mSoundFile = arrayOfNulls<CheapSoundFile>(1)
    try {
      mSoundFile[0] = CheapSoundFile.create(path, null)
      val startTime = lowerCut.toFloat() / 1000
      val endTime = upperCut.toFloat() / 1000
      val samplesPerFrame = mSoundFile[0]?.samplesPerFrame
      val sampleRate = mSoundFile[0]?.sampleRate
      val avg = sampleRate?.div(samplesPerFrame!!)
      val startFrames = (startTime * avg!!).roundToInt()
      val endFrames = (endTime * avg).roundToInt()
      mSoundFile[0]?.WriteFile(destFile, startFrames, endFrames - startFrames)
    } catch (e: IOException) {
      e.printStackTrace()
    }
    return destFile.absolutePath
  }

  private fun getMergedAudioFromSegments(segments: ArrayList<String>): String {
    var mergedAudioPath = segments[0]
    if (segments.size > 1) {
      for (i in 1 until segments.size) {
        mergedAudioPath = mergeAudios(mergedAudioPath, segments[i])!!
        audioCacheFilePaths.add(mergedAudioPath)
      }
    }
    return mergedAudioPath
  }

  private fun mergeAudios(path1: String, path2: String): String? {
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
