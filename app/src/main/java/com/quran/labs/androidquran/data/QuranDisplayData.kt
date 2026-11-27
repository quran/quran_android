package com.quran.labs.androidquran.data

import android.content.Context
import android.text.TextUtils
import androidx.annotation.StringRes
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.SuraAyahIterator
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.page.common.data.QuranNaming
import dev.zacsweers.metro.Inject
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReferenceArray
import com.quran.mobile.common.ui.core.R as UiCoreR

class QuranDisplayData @Inject constructor(private val quranInfo: QuranInfo): QuranNaming, QuranDisplayInterface {

  @Volatile
  private var cachedSuraNames: CachedSuraNames? = null

  private fun getSuraNameTranslationsArray(context: Context): Array<String> {
    val cached = getOrLoadCachedSuraNames(context)
    cached.suraNameTranslations?.let { return it }
    return synchronized(this) {
      val current = getOrLoadCachedSuraNames(context)
      current.suraNameTranslations ?: context.resources
        .getStringArray(UiCoreR.array.sura_names_translation)
        .also { current.suraNameTranslations = it }
    }
  }

  private fun getOrLoadCachedSuraNames(context: Context): CachedSuraNames {
    val resources = context.resources
    val localeTags = resources.configuration.locales.toLanguageTags()
    val resourcesId = System.identityHashCode(resources)
    val existing = cachedSuraNames
    if (existing != null &&
      existing.resourcesIdentity == resourcesId &&
      existing.localeTags == localeTags
    ) {
      return existing
    }
    return synchronized(this) {
      val current = cachedSuraNames
      if (current != null &&
        current.resourcesIdentity == resourcesId &&
        current.localeTags == localeTags
      ) {
        current
      } else {
        CachedSuraNames(
          resourcesIdentity = resourcesId,
          localeTags = localeTags,
          suraNames = resources.getStringArray(UiCoreR.array.sura_names)
        ).also { cachedSuraNames = it }
      }
    }
  }

  fun clearCache() {
    cachedSuraNames = null
  }

  private class CachedSuraNames(
    val resourcesIdentity: Int,
    val localeTags: String,
    val suraNames: Array<String>,
    @Volatile var suraNameTranslations: Array<String>? = null,
    @Volatile var suraTitleTemplate: StringTemplate? = null,
    @Volatile var juzDescriptionTemplate: StringTemplate? = null,
    @Volatile var makkiPrefix: String? = null,
    @Volatile var madaniPrefix: String? = null,
    val suraNamesWithPrefix: AtomicReferenceArray<String> = AtomicReferenceArray(Constants.SURAS_COUNT),
    val suraListMetaStrings: AtomicReferenceArray<String> = AtomicReferenceArray(Constants.SURAS_COUNT)
  )

  private class StringTemplate(
    val prefix: String,
    val suffix: String
  ) {
    fun format(arg: String): String {
      return if (suffix.isEmpty()) {
        prefix + arg
      } else if (prefix.isEmpty()) {
        arg + suffix
      } else {
        prefix + arg + suffix
      }
    }

    companion object {
      private const val SENTINEL = "\u0000"

      fun from(context: Context, @StringRes resId: Int): StringTemplate {
        val rendered = context.getString(resId, SENTINEL)
        val idx = rendered.indexOf(SENTINEL)
        return if (idx >= 0) {
          StringTemplate(rendered.substring(0, idx), rendered.substring(idx + 1))
        } else {
          StringTemplate(rendered, "")
        }
      }
    }
  }

  private fun getSuraTitleTemplate(context: Context, cached: CachedSuraNames): StringTemplate {
    return cached.suraTitleTemplate ?: StringTemplate.from(context, R.string.quran_sura_title).also {
      cached.suraTitleTemplate = it
    }
  }

  private fun getJuzDescriptionTemplate(context: Context, cached: CachedSuraNames): StringTemplate {
    return cached.juzDescriptionTemplate ?: StringTemplate.from(context, R.string.juz2_description).also {
      cached.juzDescriptionTemplate = it
    }
  }

  fun getJuzDisplayString(context: Context, juz: Int): String {
    val cached = getOrLoadCachedSuraNames(context)
    return getJuzDescriptionTemplate(context, cached).format(QuranUtils.getLocalizedNumber(juz))
  }

  /**
   * Get localized sura name from resources
   *
   * @param context    Application context
   * @param sura       Sura number (1~114)
   * @param wantPrefix Whether or not to show prefix "Sura"
   * @return Compiled sura name without translations
   */
  override fun getSuraName(context: Context, sura: Int, wantPrefix: Boolean): String {
    return getSuraName(context, sura, wantPrefix, false)
  }

  override fun getSuraNameWithNumber(context: Context, sura: Int, wantPrefix: Boolean): String {
    val name = getSuraName(context, sura, wantPrefix)
    val prefix = QuranUtils.getLocalizedNumber(sura)
    return "$prefix. $name"
  }

  /**
   * Get localized sura name from resources
   *
   * @param context         Application context
   * @param sura            Sura number (1~114)
   * @param wantPrefix      Whether or not to show prefix "Sura"
   * @param wantTranslation Whether or not to show sura name translations
   * @return Compiled sura name based on provided arguments
   */
  fun getSuraName(
    context: Context, sura: Int, wantPrefix: Boolean, wantTranslation: Boolean
  ): String {
    if (sura < Constants.SURA_FIRST || sura > Constants.SURA_LAST) return ""

    val cached = getOrLoadCachedSuraNames(context)
    val index = sura - 1
    val baseName = cached.suraNames[index]
    if (!wantPrefix && !wantTranslation) {
      return baseName
    }

    if (wantPrefix && !wantTranslation) {
      cached.suraNamesWithPrefix.get(index)?.let { return it }
      val prefixed = getSuraTitleTemplate(context, cached).format(baseName)
      cached.suraNamesWithPrefix.compareAndSet(index, null, prefixed)
      return cached.suraNamesWithPrefix.get(index) ?: prefixed
    }

    val builder = StringBuilder()
    if (wantPrefix) {
      val prefixed = cached.suraNamesWithPrefix.get(index) ?: run {
        val computed = getSuraTitleTemplate(context, cached).format(baseName)
        cached.suraNamesWithPrefix.compareAndSet(index, null, computed)
        cached.suraNamesWithPrefix.get(index) ?: computed
      }
      builder.append(prefixed)
    } else {
      builder.append(baseName)
    }
    if (wantTranslation) {
      val translation = getSuraNameTranslationsArray(context)[index]
      if (!TextUtils.isEmpty(translation)) {
        // Some sura names may not have translation
        builder.append(" (")
        builder.append(translation)
        builder.append(")")
      }
    }

    return builder.toString()
  }

  fun getSuraNameFromPage(context: Context, page: Int, wantTitle: Boolean): String {
    val sura = quranInfo.getSuraNumberFromPage(page)
    return if (sura > 0) getSuraName(context, sura, wantTitle, false) else ""
  }

  fun getPageSubtitle(context: Context, page: Int): String {
    val description = context.getString(R.string.page_description)
    return String.format(description,
      QuranUtils.getLocalizedNumber(page),
      QuranUtils.getLocalizedNumber(quranInfo.getJuzForDisplayFromPage(page)))
  }

  fun getJuzDisplayStringForPage(context: Context, page: Int): String {
    val description = context.getString(R.string.juz2_description)
    return String.format(description,
      QuranUtils.getLocalizedNumber(quranInfo.getJuzForDisplayFromPage(page)))
  }

  fun getManzilForPage(context: Context, page: Int): String {
    val manzil = quranInfo.manzilForPage(page)
    return if (manzil > 0) {
      context.getString(R.string.comma) + ' ' + context.getString(R.string.manzil_description, QuranUtils.getLocalizedNumber(manzil))
    } else {
      ""
    }
  }

  override fun getSuraAyahString(context: Context, sura: Int, ayah: Int): String {
    return getSuraAyahString(context, sura, ayah, R.string.sura_ayah_notification_str)
  }

  override fun getSuraPageString(context: Context, page: Int): String {
    val suraName = getAllSuraNamesForPageString(context, page)
    val pageText = context.getString(R.string.quran_page) + ' ' + QuranUtils.getLocalizedNumber(page)
    return if (suraName.isEmpty()) pageText else "$suraName ($pageText)"
  }

  fun getSuraAyahString(context: Context, sura: Int, ayah: Int, @StringRes resource: Int): String {
    val suraName = getSuraName(context, sura, wantPrefix = false, wantTranslation = false)
    return context.getString(resource, suraName, ayah)
  }

  override fun getNotificationTitle(
    context: Context, minVerse: SuraAyah, maxVerse: SuraAyah, isGapless: Boolean
  ): String {
    val minSura = minVerse.sura
    var maxSura = maxVerse.sura

    val notificationTitle = getSuraName(context, minSura, wantPrefix = true, wantTranslation = false)
    if (isGapless) {
      // for gapless, don't show the ayah numbers since we're
      // downloading the entire sura(s).
      return if (minSura == maxSura) {
        notificationTitle
      } else {
        "$notificationTitle - " + getSuraName(
          context, maxSura, wantPrefix = true, wantTranslation = false
        )
      }
    }

    var maxAyah = maxVerse.ayah
    if (maxAyah == 0) {
      maxSura--
      maxAyah = quranInfo.getNumberOfAyahs(maxSura)
    }

    val minAyahText = QuranUtils.getLocalizedNumber(minVerse.ayah)
    val maxAyahText = QuranUtils.getLocalizedNumber(maxAyah)
    return notificationTitle.plus(
      if (minSura == maxSura) {
        if (minVerse.ayah == maxAyah) {
          " ($maxAyahText)"
        } else {
          " ($minAyahText-$maxAyahText)"
        }
      } else {
        " ($minAyahText) - " +
            getSuraName(context, maxSura, wantPrefix = true, wantTranslation = false) +
            " ($maxAyahText)"
      }
    )
  }

  fun getSuraListMetaString(context: Context, sura: Int): String {
    if (sura in Constants.SURA_FIRST..Constants.SURA_LAST) {
      val metaArray = getOrLoadCachedSuraNames(context).suraListMetaStrings
      val index = sura - 1
      metaArray.get(index)?.let { return it }
      val computed = buildSuraListMetaString(context, sura)
      metaArray.compareAndSet(index, null, computed)
      return metaArray.get(index) ?: computed
    }
    return buildSuraListMetaString(context, sura)
  }

  private fun buildSuraListMetaString(context: Context, sura: Int): String {
    val cached = getOrLoadCachedSuraNames(context)
    val info = if (quranInfo.isMakki(sura)) {
      cached.makkiPrefix ?: (context.getString(R.string.makki) + " - ").also {
        cached.makkiPrefix = it
      }
    } else {
      cached.madaniPrefix ?: (context.getString(R.string.madani) + " - ").also {
        cached.madaniPrefix = it
      }
    }

    val ayahs = quranInfo.getNumberOfAyahs(sura)
    return info + context.resources.getQuantityString(
      R.plurals.verses, ayahs,
      QuranUtils.getLocalizedNumber(ayahs))
  }

  fun safelyGetSuraOnPage(page: Int): Int {
    return if (!quranInfo.isValidPage(page)) {
      Timber.e(IllegalArgumentException("safelyGetSuraOnPage with page: $page"))
      quranInfo.getSuraOnPage(1)
    } else {
      quranInfo.getSuraOnPage(page)
    }
  }

  fun getAyahString(sura: Int, ayah: Int, context: Context): String {
    return getSuraName(context, sura, true) + " - " + context.getString(R.string.quran_ayah, ayah)
  }

  fun getAyahMetadata(sura: Int, ayah: Int, page: Int, context: Context): String {
    val juz = quranInfo.getJuzForDisplayFromPage(page)
    return context.getString(R.string.quran_ayah_details,
      getSuraName(context, sura, true),
      QuranUtils.getLocalizedNumber(ayah),
      QuranUtils.getLocalizedNumber(quranInfo.getJuzFromSuraAyah(sura, ayah, juz)))
  }

  fun getAllSuraNamesForPageString(context: Context, page: Int): String {
    return if (!quranInfo.isValidPage(page)) {
      ""
    } else {
      val bounds = quranInfo.getPageBounds(page)
      val suraNames = (bounds[0]..bounds[2]).joinToString(context.getString(R.string.comma) + ' ') {
        getSuraName(context, it, wantPrefix = false)
      }
      context.getString(R.string.quran_sura_title, suraNames)
    }
  }

  fun getAyahKeysOnPage(page: Int): Set<String> {
    val ayahKeys: MutableSet<String> = LinkedHashSet()
    val bounds = quranInfo.getPageBounds(page)
    val start = SuraAyah(bounds[0], bounds[1])
    val end = SuraAyah(bounds[2], bounds[3])

    val iterator = SuraAyahIterator(quranInfo, start, end)
    while (iterator.next()) {
      ayahKeys.add(iterator.sura.toString() + ":" + iterator.ayah.toString())
    }
    return ayahKeys
  }
}
