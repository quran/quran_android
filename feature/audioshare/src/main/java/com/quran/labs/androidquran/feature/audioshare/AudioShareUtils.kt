package com.quran.labs.androidquran.feature.audioshare

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.SparseIntArray
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

    val audioCacheDirectory = context.cacheDir

    var sharablePath: String?
    val audioCacheFilePaths = mutableListOf<String>()
    withContext(Dispatchers.IO) {
      val (startSurahTimingData, endSurahTimingData) = getTimingData(start, end, gaplessDatabase)

      val startAyah = start.ayah
      val endAyah = end.ayah

      val isFirstAyahInSurah = startAyah == 1
      val startTimeOfAyahAfterEndAyah = endSurahTimingData[endAyah + 1]
      val isLastAyahInSurah = startTimeOfAyahAfterEndAyah == 0

      val startAyahTime = if (isFirstAyahInSurah) {
        0
      } else {
        startSurahTimingData[startAyah]
      }

      val endAyahTime = if (isLastAyahInSurah) {
        val endMarker = endSurahTimingData.get(999, -1)
        if (endMarker > 0) {
          endMarker
        } else {
          getSurahDuration(context, getSurahAudioPath(urlFormat, end.sura))
        }
      } else {
        startTimeOfAyahAfterEndAyah
      }

      val startAndEndAyahAreInSameSurah = start.sura == end.sura

      if (startAndEndAyahAreInSameSurah) {
        val audioSegmentPath: String? = getSurahSegment(
          audioCacheDirectory, getSurahAudioPath(urlFormat, start.sura), startAyahTime, endAyahTime
        )

        sharablePath = if (audioSegmentPath != null) {
          audioCacheFilePaths.add(audioSegmentPath)
          getRenamedSharableAudioFile(
            qari,
            start,
            end,
            audioSegmentPath,
            audioCacheDirectory.toString(),
            audioCacheFilePaths
          )
        } else {
          null
        }
        audioCacheFilePaths.clear()
      } else {
        val segmentPaths = mutableListOf<String>()
        val endOfSurah = -1
        val startOfSurah = 0
        val startSegmentPath: String? = getSurahSegmentPath(
          context, audioCacheDirectory, urlFormat, start.sura, startAyahTime, endOfSurah
        )
        val lastSegmentPath: String? = getSurahSegmentPath(
          context, audioCacheDirectory, urlFormat, end.sura, startOfSurah, endAyahTime
        )

        if (startSegmentPath != null && lastSegmentPath != null) {
          for (surahIndex in start.sura..end.sura) {
            val isTheFirstSurah = surahIndex == start.sura
            val isMiddleSurah = surahIndex != start.sura && surahIndex != end.sura
            if (isTheFirstSurah) {
              segmentPaths.add(startSegmentPath)
              audioCacheFilePaths.add(startSegmentPath)
            } else if (isMiddleSurah) {
              segmentPaths.add(getSurahAudioPath(urlFormat, surahIndex))
            } else {
              segmentPaths.add(lastSegmentPath)
              audioCacheFilePaths.add(lastSegmentPath)
            }
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
  ): String? {
    var upperBoundTime = endAyahTime
    val audioFilePath: String = getSurahAudioPath(urlFormat, surah)
    val isFirstSegment = endAyahTime < 0
    if (isFirstSegment) {
      upperBoundTime = getSurahDuration(context, audioFilePath)
    }
    return getSurahSegment(audioCacheDirectory, audioFilePath, startAyahTime, upperBoundTime)
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

  private fun getTimingData(
    start: SuraAyah,
    end: SuraAyah,
    gaplessDatabase: String
  ): Pair<SparseIntArray, SparseIntArray> {
    val db: SuraTimingDatabaseHandler =
      SuraTimingDatabaseHandler.getDatabaseHandler(gaplessDatabase)
    val firstSurahMap = db.getAyahTimings(start.sura)
    val lastSurahMap = if (start.sura == end.sura) {
      firstSurahMap
    } else {
      db.getAyahTimings(end.sura)
    }

    return firstSurahMap to lastSurahMap
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
    val soundFile = CheapSoundFile.create(path, null)
    try {
      val startTime = lowerCut.toFloat() / 1000
      val endTime = upperCut.toFloat() / 1000
      val samplesPerFrame = soundFile.samplesPerFrame
      val sampleRate = soundFile.sampleRate
      val avg = sampleRate.div(samplesPerFrame)
      val startFrames = (startTime * avg).roundToInt()
      val endFrames = (endTime * avg).roundToInt()
      soundFile.WriteFile(destFile, startFrames, endFrames - startFrames)
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
