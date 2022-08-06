package com.quran.data.model

/**
 * Reference to a portion in the Quran. Can be a single id (e.g. 18:1)
 * or can be multiple such as a range of ayahs (e.g. 18:1 to 18:10)
 */
sealed interface QuranRef {
  object None : QuranRef

  /**
   * Reference to a single Quran Identifier
   * (e.g. single word, ayatul kursi, pg. 23, Al-Kahf, 30th Juz2, ...)
   */
  sealed interface QuranId : QuranRef

  data class Word(val ayah: Ayah, val word: Int) : QuranId, Comparable<Word> {override fun compareTo(other: Word) = compare(other)}
  data class Ayah(val sura: Int,  val ayah: Int) : QuranId, Comparable<Ayah> {override fun compareTo(other: Ayah) = compare(other)}
  data class Sura(val sura: Int) : QuranId, Comparable<Sura> {override fun compareTo(other: Sura) = sura.compareTo(other.sura)}
  data class Page(val page: Int) : QuranId, Comparable<Page> {override fun compareTo(other: Page) = page.compareTo(other.page)}
  data class Rub3(val rub3: Int) : QuranId, Comparable<Rub3> {override fun compareTo(other: Rub3) = rub3.compareTo(other.rub3)}
  data class Hizb(val hizb: Int) : QuranId, Comparable<Hizb> {override fun compareTo(other: Hizb) = hizb.compareTo(other.hizb)}
  data class Juz2(val juz2: Int) : QuranId, Comparable<Juz2> {override fun compareTo(other: Juz2) = juz2.compareTo(other.juz2)}
  object TheQuran : QuranId, Comparable<TheQuran> {override fun compareTo(other: TheQuran): Int = 0}

  fun Word.compare(other: Word): Int = when {
    this == other -> 0
    ayah != other.ayah -> ayah.compareTo(other.ayah)
    else -> word.compareTo(other.word)
  }

  fun Ayah.compare(other: Ayah): Int = when {
    this == other -> 0
    sura != other.sura -> sura.compareTo(other.sura)
    else -> ayah.compareTo(other.ayah)
  }

  /**
   * Reference to a range of Quran IDs
   * (e.g. ayahs 18:1-18:10, pgs. 582-604, Juz' 30-30, surahs Al-Naba'-Al-Nas, ...)
   */
  sealed interface Range<T : QuranId> : QuranRef {
    val start: T
    val endInclusive: T
  }

  data class QuranRange<T : QuranId>(override val start: T, override val endInclusive: T): Range<T>
}
