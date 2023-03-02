package com.quran.labs.androidquran.util

import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.util.QariUtil
import com.quran.labs.androidquran.service.AudioService
import java.io.File
import java.util.Locale
import javax.inject.Inject
import timber.log.Timber

class AudioUtils @Inject constructor(
  private val quranInfo: QuranInfo,
  private val quranFileUtils: QuranFileUtils,
  private val qariUtil: QariUtil
) {

  private val totalPages = quranInfo.numberOfPages

  internal object LookAheadAmount {
    const val PAGE = 1
    const val SURA = 2
    const val JUZ = 3

    // make sure to update these when a lookup type is added
    const val MIN = 1
    const val MAX = 3
  }

  /**
   * Get a list of QariItem representing the qaris to show
   *
   * This removes gapped qaris that have a gapless alternative if
   * no files are downloaded for that qari.
   *
   * This list sorts gapless qaris before gapped qaris, with each
   * set being alphabetically sorted.
   */
  fun getQariList(context: Context): List<QariItem> {
    return qariUtil.getQariList(context)
      .filter {
        it.isGapless || (it.hasGaplessAlternative && !haveAnyFiles(it.path))
      }
      .sortedWith { lhs, rhs ->
        if (lhs.isGapless != rhs.isGapless) {
          if (lhs.isGapless) -1 else 1
        } else {
          lhs.name.compareTo(rhs.name)
        }
      }
  }

  private fun haveAnyFiles(path: String): Boolean {
    val basePath = quranFileUtils.audioFileDirectory()
    val file = File(basePath, path)
    return file.isDirectory && file.list()?.isNotEmpty() ?: false
  }

  fun getQariUrl(qari: Qari): String {
    return qari.url + if (qari.isGapless) {
      "%03d$AUDIO_EXTENSION"
    } else {
      "%03d%03d$AUDIO_EXTENSION"
    }
  }

  fun getQariUrl(item: QariItem): String {
    return item.url + if (item.isGapless) {
      "%03d$AUDIO_EXTENSION"
    } else {
      "%03d%03d$AUDIO_EXTENSION"
    }
  }

  fun getLocalQariUrl(item: QariItem): String? {
    val rootDirectory = quranFileUtils.audioFileDirectory()
    return if (rootDirectory == null) null else rootDirectory + item.path
  }

  fun getLocalQariUri(item: QariItem): String? {
    val rootDirectory = quranFileUtils.audioFileDirectory()
    return if (rootDirectory == null) null else
      rootDirectory + item.path + File.separator + if (item.isGapless) {
        "%03d$AUDIO_EXTENSION"
      } else {
        "%d" + File.separator + "%d" + AUDIO_EXTENSION
      }
  }

  fun getQariDatabasePathIfGapless(item: QariItem): String? {
    var databaseName = item.databaseName
    if (databaseName != null) {
      val path = getLocalQariUrl(item)
      if (path != null) {
        databaseName = path + File.separator + databaseName + DB_EXTENSION
      }
    }
    return databaseName
  }

  fun getLastAyahToPlay(
    startAyah: SuraAyah,
    currentPage: Int,
    mode: Int,
    isDualPageVisible: Boolean
  ): SuraAyah? {
    val potentialPage =
      if (isDualPageVisible &&
        mode == LookAheadAmount.PAGE &&
        currentPage % 2 == 1
      ) {
        // if we download page by page and we are currently in tablet mode
        // and playing from the right page, get the left page as well.
        currentPage + 1
      } else {
        currentPage
      }

    // in cases like Shermerly in dual screen where the last page is empty, use
    // the page prior to the last page.
    val page = if (isDualPageVisible && potentialPage == (totalPages + 1)) {
      totalPages
    } else {
      potentialPage
    }

    var pageLastSura = 114
    var pageLastAyah = 6

    // page < 0 - intentional, because nextPageAyah looks up the ayah on the next page
    if (page > totalPages || page < 0) {
      return null
    }

    if (mode == LookAheadAmount.SURA) {
      val sura = startAyah.sura
      val lastAyah = quranInfo.getNumberOfAyahs(sura)
      if (lastAyah == -1) {
        return null
      }
      return SuraAyah(sura, lastAyah)
    } else if (mode == LookAheadAmount.JUZ) {
      val juz = quranInfo.getJuzFromPage(page)
      if (juz == 30) {
        return SuraAyah(114, 6)
      } else if (juz in 1..29) {
        val endJuz = quranInfo.getQuarterByIndex(juz * 8)
        if (pageLastSura > endJuz.sura) {
          // ex between jathiya and a7qaf
          return getQuarterForNextJuz(juz)
        } else if (pageLastSura == endJuz.sura && pageLastAyah > endJuz.ayah) {
          // ex surat al anfal
          return getQuarterForNextJuz(juz)
        }

        return SuraAyah(endJuz.sura, endJuz.ayah)
      }
    } else {
      val range = quranInfo.getVerseRangeForPage(page)
      pageLastSura = range.endingSura
      pageLastAyah = range.endingAyah
    }

    // page mode (fallback also from errors above)
    return SuraAyah(pageLastSura, pageLastAyah)
  }

  private fun getQuarterForNextJuz(currentJuz: Int): SuraAyah {
    return if (currentJuz < 29) {
      val juz = quranInfo.getQuarterByIndex((currentJuz + 1) * 8)
      SuraAyah(juz.sura, juz.ayah)
    } else {
      // if we're currently at the 29th juz', just return the end of the 30th.
      SuraAyah(114, 6)
    }
  }

  fun shouldDownloadBasmallah(
    baseDirectory: String,
    start: SuraAyah,
    end: SuraAyah,
    isGapless: Boolean
  ): Boolean {
    if (isGapless) {
      return false
    }

    if (baseDirectory.isNotEmpty()) {
      var f = File(baseDirectory)
      if (f.exists()) {
        val filename = 1.toString() + File.separator + 1 + AUDIO_EXTENSION
        f = File(baseDirectory + File.separator + filename)
        if (f.exists()) {
          Timber.d("already have basmalla...")
          return false
        }
      } else {
        f.mkdirs()
      }
    }

    return doesRequireBasmallah(start, end)
  }

  @VisibleForTesting
  fun doesRequireBasmallah(minAyah: SuraAyah, maxAyah: SuraAyah): Boolean {
    Timber.d("seeing if need basmalla...")

    for (i in minAyah.sura..maxAyah.sura) {
      val firstAyah: Int = if (i == minAyah.sura) {
        minAyah.ayah
      } else {
        1
      }
      if (firstAyah == 1 && i != 1 && i != 9) {
        return true
      }
    }

    return false
  }

  fun haveAllFiles(
    baseUrl: String,
    path: String,
    start: SuraAyah,
    end: SuraAyah,
    isGapless: Boolean
  ): Boolean {
    if (path.isEmpty()) {
      return false
    }

    var f = File(path)
    if (!f.exists()) {
      f.mkdirs()
      return false
    }

    val startSura = start.sura
    val startAyah = start.ayah

    val endSura = end.sura
    val endAyah = end.ayah

    if (endSura < startSura || endSura == startSura && endAyah < startAyah) {
      throw IllegalStateException(
        "End isn't larger than the start: $startSura:$startAyah to $endSura:$endAyah"
      )
    }

    for (i in startSura..endSura) {
      val lastAyah = if (i == endSura) {
        endAyah
      } else {
        quranInfo.getNumberOfAyahs(i)
      }
      val firstAyah = if (i == startSura) {
        startAyah
      } else {
        1
      }

      if (isGapless) {
        if (i == endSura && endAyah == 0) {
          continue
        }
        val fileName = String.format(Locale.US, baseUrl, i)
        Timber.d("gapless, checking if we have %s", fileName)
        f = File(fileName)
        if (!f.exists()) {
          return false
        }
        continue
      }

      Timber.d("not gapless, checking each ayah...")
      for (j in firstAyah..lastAyah) {
        val filename = i.toString() + File.separator + j + AUDIO_EXTENSION
        f = File(path + File.separator + filename)
        if (!f.exists()) {
          return false
        }
      }
    }

    return true
  }

  fun getAudioIntent(context: Context, action: String): Intent {
    return Intent(context, AudioService::class.java).apply {
      setAction(action)
    }
  }

  companion object {
    const val ZIP_EXTENSION = ".zip"
    const val AUDIO_EXTENSION = ".mp3"

    private const val DB_EXTENSION = ".db"
  }
}
