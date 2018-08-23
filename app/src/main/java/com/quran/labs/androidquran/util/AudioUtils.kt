package com.quran.labs.androidquran.util

import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.QariItem
import com.quran.labs.androidquran.data.QuranInfo
import com.quran.labs.androidquran.data.SuraAyah
import com.quran.labs.androidquran.service.AudioService
import dagger.Reusable
import timber.log.Timber
import java.io.File
import java.util.Locale
import javax.inject.Inject

@Reusable
class AudioUtils @Inject
constructor(private val quranInfo: QuranInfo, private val quranFileUtils: QuranFileUtils) {

  private val totalPages = quranInfo.numberOfPages

  internal object LookAheadAmount {
    val PAGE = 1
    val SURA = 2
    val JUZ = 3

    // make sure to update these when a lookup type is added
    val MIN = 1
    val MAX = 3
  }

  /**
   * Get a list of QariItem representing the qaris to show
   *
   * This method takes into account qaris that exist both in gapped and gapless, and, in those
   * cases, hides the gapped version if it contains no files.
   *
   * @param context the current context
   * @return a list of QariItem representing the qaris to show.
   */
  fun getQariList(context: Context): List<QariItem> {
    val resources = context.resources
    val shuyookh = resources.getStringArray(R.array.quran_readers_name)
    val paths = resources.getStringArray(R.array.quran_readers_path)
    val urls = resources.getStringArray(R.array.quran_readers_urls)
    val databases = resources.getStringArray(R.array.quran_readers_db_name)
    val hasGaplessEquivalent = resources.getIntArray(R.array.quran_readers_have_gapless_equivalents)
    val items = mutableListOf<QariItem>()
    for (i in shuyookh.indices) {
      if (hasGaplessEquivalent[i] == 0 || haveAnyFiles(context, paths[i])) {
        items += QariItem(i, shuyookh[i], urls[i], paths[i], databases[i])
      }
    }

    return items.sortedWith(Comparator { lhs, rhs ->
      if (lhs.isGapless != rhs.isGapless) {
        if (lhs.isGapless) -1 else 1
      } else {
        lhs.name.compareTo(rhs.name)
      }
    })
  }

  fun getQariUrl(item: QariItem): String {
    return item.url + if (item.isGapless) {
      "%03d" + AudioUtils.AUDIO_EXTENSION
    } else {
      "%03d%03d" + AudioUtils.AUDIO_EXTENSION
    }
  }

  fun getLocalQariUrl(context: Context, item: QariItem): String? {
    val rootDirectory = quranFileUtils.getQuranAudioDirectory(context)
    return if (rootDirectory == null) null else rootDirectory + item.path
  }

  fun getQariDatabasePathIfGapless(context: Context, item: QariItem): String? {
    var databaseName = item.databaseName
    if (databaseName != null) {
      val path = getLocalQariUrl(context, item)
      if (path != null) {
        databaseName = path + File.separator + databaseName + DB_EXTENSION
      }
    }
    return databaseName
  }

  fun getGaplessDatabaseUrl(qari: QariItem): String? {
    if (!qari.isGapless || qari.databaseName == null) {
      return null
    }

    val dbName = qari.databaseName + ZIP_EXTENSION
    return quranFileUtils.gaplessDatabaseRootUrl + "/" + dbName
  }

  fun getLastAyahToPlay(startAyah: SuraAyah,
                        currentPage: Int,
                        mode: Int,
                        isDualPages: Boolean): SuraAyah? {
    val page = if (isDualPages && mode == LookAheadAmount.PAGE && currentPage % 2 == 1) {
      // if we download page by page and we are currently in tablet mode
      // and playing from the right page, get the left page as well.
      currentPage + 1
    } else {
      currentPage
    }

    var pageLastSura = 114
    var pageLastAyah = 6
    // page < 0 - intentional, because nextPageAyah looks up the ayah on the next page
    if (page > totalPages || page < 0) {
      return null
    }
    if (page < totalPages) {
      val nextPage = page + 1
      val nextPageSura = quranInfo.safelyGetSuraOnPage(nextPage)
      // using [page+1] as an index because we literally want the next page
      val nextPageAyah = quranInfo.getFirstAyahOnPage(nextPage)

      pageLastSura = nextPageSura
      pageLastAyah = nextPageAyah - 1
      if (pageLastAyah < 1) {
        pageLastSura--
        pageLastAyah = quranInfo.getNumAyahs(pageLastSura)
      }
    }

    if (mode == LookAheadAmount.SURA) {
      var sura = startAyah.sura
      var lastAyah = quranInfo.getNumAyahs(sura)
      if (lastAyah == -1) {
        return null
      }

      // if we start playback between two suras, download both suras
      if (pageLastSura > sura) {
        sura = pageLastSura
        lastAyah = quranInfo.getNumAyahs(sura)
      }
      return SuraAyah(sura, lastAyah)
    } else if (mode == LookAheadAmount.JUZ) {
      val juz = quranInfo.getJuzFromPage(page)
      if (juz == 30) {
        return SuraAyah(114, 6)
      } else if (juz in 1..29) {
        var endJuz = quranInfo.getQuarterByIndex(juz * 8)
        if (pageLastSura > endJuz[0]) {
          // ex between jathiya and a7qaf
          endJuz = quranInfo.getQuarterByIndex((juz + 1) * 8)
        } else if (pageLastSura == endJuz[0] && pageLastAyah > endJuz[1]) {
          // ex surat al anfal
          endJuz = quranInfo.getQuarterByIndex((juz + 1) * 8)
        }

        return SuraAyah(endJuz[0], endJuz[1])
      }
    }

    // page mode (fallback also from errors above)
    return SuraAyah(pageLastSura, pageLastAyah)
  }

  fun shouldDownloadBasmallah(baseDirectory: String,
                              start: SuraAyah,
                              end: SuraAyah,
                              isGapless: Boolean) : Boolean {
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
      val firstAyah: Int = if (i == minAyah.sura) { minAyah.ayah } else { 1 }
      if (firstAyah == 1 && i != 1 && i != 9) {
        return true
      }
    }

    return false
  }

  private fun haveAnyFiles(context: Context, path: String): Boolean {
    val basePath = quranFileUtils.getQuranAudioDirectory(context)
    val file = File(basePath, path)
    return file.isDirectory && file.list().isNotEmpty()
  }

  fun haveAllFiles(baseUrl: String,
                   path: String,
                   start: SuraAyah,
                   end: SuraAyah,
                   isGapless: Boolean): Boolean {
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
      throw IllegalStateException("End isn't larger than the start")
    }

    for (i in startSura..endSura) {
      val lastAyah = if (i == endSura) { endAyah } else { quranInfo.getNumAyahs(i) }
      val firstAyah = if (i == startSura) { startAyah } else { 1 }

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
    const val AUDIO_EXTENSION = ".mp3"

    private const val DB_EXTENSION = ".db"
    private const val ZIP_EXTENSION = ".zip"

    fun haveSuraAyahForQari(baseDir: String, sura: Int, ayah: Int): Boolean {
      val filename = baseDir + File.separator + sura +
          File.separator + ayah + AUDIO_EXTENSION
      return File(filename).exists()
    }
  }
}
