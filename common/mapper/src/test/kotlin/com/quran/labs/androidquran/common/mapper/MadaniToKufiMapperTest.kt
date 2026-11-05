package com.quran.labs.androidquran.common.mapper

import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.pages.data.warsh.WarshDataSource
import org.junit.Test

class MadaniToKufiMapperTest {
  private val mapper = MadaniToKufiMapper(QuranInfo(WarshDataSource()))

  @Test
  fun testFatihaMapping() {
    // start at "ayah 0" so that verse N is found at fatiha[N] to make the test clearer
    val fatiha = generateSuraAyah(1, 0, 7)

    // ayahs 1-5 madani should be 2-6 kufi
    assertThat(mapper.mapAyah(fatiha[1])).containsExactly(fatiha[2])
    assertThat(mapper.mapAyah(fatiha[2])).containsExactly(fatiha[3])
    assertThat(mapper.mapAyah(fatiha[3])).containsExactly(fatiha[4])
    assertThat(mapper.mapAyah(fatiha[4])).containsExactly(fatiha[5])
    assertThat(mapper.mapAyah(fatiha[5])).containsExactly(fatiha[6])

    // ayahs 6-7 madani should be ayah 7 kufi
    assertThat(mapper.mapAyah(fatiha[6])).containsExactly(fatiha[7])
    assertThat(mapper.mapAyah(fatiha[7])).containsExactly(fatiha[7])
  }

  @Test
  fun testFatihaReverseMapping() {
    val fatiha = generateSuraAyah(1, 0, 7)

    // ayahs 1 kufi doesn't exist in madani, but map it to 1
    assertThat(mapper.reverseMapAyah(fatiha[1])).containsExactly(fatiha[1])

    // ayahs 2-6 kufi should be ayahs 1-5 madani
    assertThat(mapper.reverseMapAyah(fatiha[2])).containsExactly(fatiha[1])
    assertThat(mapper.reverseMapAyah(fatiha[3])).containsExactly(fatiha[2])
    assertThat(mapper.reverseMapAyah(fatiha[4])).containsExactly(fatiha[3])
    assertThat(mapper.reverseMapAyah(fatiha[5])).containsExactly(fatiha[4])
    assertThat(mapper.reverseMapAyah(fatiha[6])).containsExactly(fatiha[5])

    // ayah 7 kufi should be ayahs 6-7 madani
    assertThat(mapper.reverseMapAyah(fatiha[7])).containsExactly(fatiha[6], fatiha[7])
  }

  @Test
  fun testSuraBaqarahMapping() {
    val baqarah = generateSuraAyah(2, 0, 285)

    // ayah 1 in madani is ayat 1-2 in kufi
    assertThat(mapper.mapAyah(baqarah[1])).containsExactly(baqarah[1], baqarah[2])

    // ayahs 2-198 in madani is 3-199 in kufi
    (2..198).forEach {
      assertThat(mapper.mapAyah(baqarah[it])).containsExactly(baqarah[it + 1])
    }

    // ayah 199 in madani is ayat 200 and 201
    assertThat(mapper.mapAyah(baqarah[199])).containsExactly(baqarah[200], baqarah[201])

    // ayahs 200-252 in madani is ayat 202-254 in kufi
    (200..252).forEach {
      assertThat(mapper.mapAyah(baqarah[it])).containsExactly(baqarah[it + 2])
    }

    // ayahs 253-254 in madani is ayah 255 in kufi
    assertThat(mapper.mapAyah(baqarah[253])).containsExactly(baqarah[255])
    assertThat(mapper.mapAyah(baqarah[254])).containsExactly(baqarah[255])

    // 2:255-2:285 in madani map to 2:256-2:286 in kufi
    (255..284).forEach {
      assertThat(mapper.mapAyah(baqarah[it])).containsExactly(baqarah[it + 1])
    }
    assertThat(mapper.mapAyah(baqarah[285])).containsExactly(SuraAyah(2, 286))
  }

  @Test
  fun testSuraBaqarahReverseMapping() {
    val baqarah = generateSuraAyah(2, 0, 286)

    // ayah 1-2 in kufi is ayah 1 in madani
    assertThat(mapper.reverseMapAyah(baqarah[1])).containsExactly(baqarah[1])
    assertThat(mapper.reverseMapAyah(baqarah[2])).containsExactly(baqarah[1])

    // ayahs 3-199 in kufi are 2-198 in madani
    (3..199).forEach {
      assertThat(mapper.reverseMapAyah(baqarah[it])).containsExactly(baqarah[it - 1])
    }

    // ayahs 200 and 201 in kufi are ayah 199 in madani
    assertThat(mapper.reverseMapAyah(baqarah[200])).containsExactly(baqarah[199])
    assertThat(mapper.reverseMapAyah(baqarah[201])).containsExactly(baqarah[199])

    // ayahs 202-254 in kufi are ayahs 200-252 in madani
    (202..254).forEach {
      assertThat(mapper.reverseMapAyah(baqarah[it])).containsExactly(baqarah[it - 2])
    }

    // ayah 255 in kufi is ayahs 253-254 in madani
    assertThat(mapper.reverseMapAyah(baqarah[255])).containsExactly(baqarah[253], baqarah[254])

    // 2:256-2:286 in kufi are 2:255-2:285 in madani
    (256..286).forEach {
      assertThat(mapper.reverseMapAyah(baqarah[it])).containsExactly(baqarah[it - 1])
    }
  }

  @Test
  fun testSuraHajjMapping() {
    // ayah 19 madani is ayat 19-21 in kufi
    assertThat(mapper.mapAyah(SuraAyah(22, 19)))
        .containsExactly(SuraAyah(22, 19), SuraAyah(22, 20), SuraAyah(22, 21))
  }

  @Test
  fun testReverseSuraHajjMapping() {
    // ayah 19-21 kufi is ayah 19 madnai
    assertThat(mapper.reverseMapAyah(SuraAyah(22, 19)))
      .containsExactly(SuraAyah(22, 19))
    assertThat(mapper.reverseMapAyah(SuraAyah(22, 20)))
      .containsExactly(SuraAyah(22, 19))
    assertThat(mapper.reverseMapAyah(SuraAyah(22, 21)))
      .containsExactly(SuraAyah(22, 19))
  }

  @Test
  fun testSuraMaunMapping() {
    val maun = generateSuraAyah(107, 5, 6)
    // ayah 5 madani is ayah 5
    assertThat(mapper.mapAyah(maun[0])).containsExactly(maun[0])
    // ayah 6 madani is ayat 6 and 7 in kufi
    assertThat(mapper.mapAyah(maun[1])).containsExactly(maun[1], SuraAyah(107, 7))
  }

  @Test
  fun testSuraMaunReverseMapping() {
    // ayah 5 kufi is ayah 5 madani
    assertThat(mapper.reverseMapAyah(SuraAyah(107, 5))).containsExactly(SuraAyah(107, 5))
    // ayahs 6 and 7 kufi are ayah 6 madani
    assertThat(mapper.reverseMapAyah(SuraAyah(107, 6))).containsExactly(SuraAyah(107, 6))
    assertThat(mapper.reverseMapAyah(SuraAyah(107, 7))).containsExactly(SuraAyah(107, 6))
  }

  @Test
  fun testSuraKahfMapping() {
    val kahf = generateSuraAyah(18, 0, 25)
    // ayahs 22-23 madani are ayah 22 kufi
    assertThat(mapper.mapAyah(kahf[22])).containsExactly(kahf[22])
    assertThat(mapper.mapAyah(kahf[23])).containsExactly(kahf[22])
    // ayah 24 madani is ayat 23-24 in kufi
    assertThat(mapper.mapAyah(kahf[24])).containsExactly(kahf[23], kahf[24])
    assertThat(mapper.mapAyah(kahf[25])).containsExactly(kahf[25])
  }

  @Test
  fun testSuraKahfReverseMapping() {
    val kahf = generateSuraAyah(18, 0, 24)
    // ayah 22 kufi is ayahs 22-23 madani
    assertThat(mapper.reverseMapAyah(kahf[22])).containsExactly(kahf[22], kahf[23])
    // ayahs 23-24 kufi are ayah 24 madani
    assertThat(mapper.reverseMapAyah(kahf[23])).containsExactly(kahf[24])
    assertThat(mapper.reverseMapAyah(kahf[24])).containsExactly(kahf[24])
  }

  @Test
  fun testSuraTahaMapping() {
    val taha = generateSuraAyah(20, 0, 135)
    // ayahs 38-39 madani are ayah 39 kufi
    assertThat(mapper.mapAyah(taha[37])).containsExactly(taha[38])
    assertThat(mapper.mapAyah(taha[38])).containsExactly(taha[39])
    assertThat(mapper.mapAyah(taha[39])).containsExactly(taha[39])
    assertThat(mapper.mapAyah(taha[40])).containsExactly(taha[40])
    // ayah 41 madani is ayat 41-42 in kufi
    assertThat(mapper.mapAyah(taha[41])).containsExactly(taha[41], taha[42])
    // ayah 77 madani is ayat 78-79 in kufi
    assertThat(mapper.mapAyah(taha[77])).containsExactly(taha[78], taha[79])
    // ayahs 120-121 madani are ayah 123 kufi
    assertThat(mapper.mapAyah(taha[120])).containsExactly(taha[123])
    assertThat(mapper.mapAyah(taha[121])).containsExactly(taha[123])
    // ayahs 129-130 madani are ayah 131 kufi
    assertThat(mapper.mapAyah(taha[129])).containsExactly(taha[131])
    assertThat(mapper.mapAyah(taha[130])).containsExactly(taha[131])
    assertThat(mapper.mapAyah(taha[134])).containsExactly(taha[135])
  }

  @Test
  fun testSuraTahaReverseMapping() {
    val taha = generateSuraAyah(20, 0, 135)
    // ayah 39 kufi is ayahs 38-39 madani
    assertThat(mapper.reverseMapAyah(taha[39])).containsExactly(taha[38], taha[39])
    // ayahs 41-42 kufi are ayah 41 madani
    assertThat(mapper.reverseMapAyah(taha[41])).containsExactly(taha[41])
    assertThat(mapper.reverseMapAyah(taha[42])).containsExactly(taha[41])
    assertThat(mapper.reverseMapAyah(taha[131])).containsExactly(taha[129], taha[130])
    assertThat(mapper.reverseMapAyah(taha[135])).containsExactly(taha[134])
  }

  @Test
  fun testSuraAnkabutMapping() {
    val ankabut = generateSuraAyah(29, 0, 30)
    assertThat(mapper.mapAyah(ankabut[27])).containsExactly(ankabut[28])
    // ayahs 28-29 madani are ayah 29 kufi
    assertThat(mapper.mapAyah(ankabut[28])).containsExactly(ankabut[29])
    assertThat(mapper.mapAyah(ankabut[29])).containsExactly(ankabut[29])
    assertThat(mapper.mapAyah(ankabut[30])).containsExactly(ankabut[30])
  }

  @Test
  fun testSuraAnkabutReverseMapping() {
    val ankabut = generateSuraAyah(29, 0, 30)
    assertThat(mapper.reverseMapAyah(ankabut[28])).containsExactly(ankabut[27])
    // ayah 29 kufi is ayahs 28-29 madani
    assertThat(mapper.reverseMapAyah(ankabut[29])).containsExactly(ankabut[28], ankabut[29])
    assertThat(mapper.reverseMapAyah(ankabut[30])).containsExactly(ankabut[30])
  }

  @Test
  fun testSuraFajrMapping() {
    val fajr = generateSuraAyah(89, 0, 32)
    assertThat(mapper.mapAyah(fajr[24])).containsExactly(SuraAyah(89, 22))
    // ayahs 25-26 madani are ayah 23 kufi
    assertThat(mapper.mapAyah(fajr[25])).containsExactly(SuraAyah(89, 23))
    assertThat(mapper.mapAyah(fajr[26])).containsExactly(SuraAyah(89, 23))
    // ayahs 27-31 madani are ayat 24-28 in kufi
    (27..31).forEach {
      assertThat(mapper.mapAyah(fajr[it])).containsExactly(SuraAyah(89, it - 3))
    }
    // ayah 32 madani is ayat 29-30 in kufi
    assertThat(mapper.mapAyah(fajr[32])).containsExactly(SuraAyah(89, 29), SuraAyah(89, 30))
  }

  @Test
  fun testSuraFajrReverseMapping() {
    // ayah 23 kufi is ayahs 25-26 madani
    assertThat(mapper.reverseMapAyah(SuraAyah(89, 23)))
      .containsExactly(SuraAyah(89, 25), SuraAyah(89, 26))
    assertThat(mapper.reverseMapAyah(SuraAyah(89, 24))).containsExactly(SuraAyah(89, 27))
    // ayahs 29-30 kufi are ayah 32 madani
    assertThat(mapper.reverseMapAyah(SuraAyah(89, 29))).containsExactly(SuraAyah(89, 32))
    assertThat(mapper.reverseMapAyah(SuraAyah(89, 30))).containsExactly(SuraAyah(89, 32))
  }

  @Test
  fun testSuraAsrMapping() {
    val asr = generateSuraAyah(103, 0, 3)
    // ayah 1 madani is ayat 1-2 in kufi
    assertThat(mapper.mapAyah(asr[1])).containsExactly(asr[1], asr[2])
    // ayahs 2-3 madani are ayah 3 kufi
    assertThat(mapper.mapAyah(asr[2])).containsExactly(asr[3])
    assertThat(mapper.mapAyah(asr[3])).containsExactly(asr[3])
  }

  @Test
  fun testSuraAsrReverseMapping() {
    val asr = generateSuraAyah(103, 0, 3)
    // ayahs 1-2 kufi are ayah 1 madani
    assertThat(mapper.reverseMapAyah(asr[1])).containsExactly(asr[1])
    assertThat(mapper.reverseMapAyah(asr[2])).containsExactly(asr[1])
    // ayah 3 kufi is ayahs 2-3 madani
    assertThat(mapper.reverseMapAyah(asr[3])).containsExactly(asr[2], asr[3])
  }

  @Test
  fun testEveryAyahMapsToAnAyahInTheOtherCounting() {
    val madaniAyahCounts = WarshDataSource().numberOfAyahsForSuraArray
    val kufiAyahCounts = MadaniDataSource().numberOfAyahsForSuraArray
    (1..114).forEach { sura ->
      // mapping throws if more than one operation applies to an ayah
      (1..madaniAyahCounts[sura - 1]).forEach { ayah ->
        mapper.mapAyah(SuraAyah(sura, ayah)).forEach {
          assertThat(it.ayah).isIn(1..kufiAyahCounts[sura - 1])
        }
      }
      (1..kufiAyahCounts[sura - 1]).forEach { ayah ->
        mapper.reverseMapAyah(SuraAyah(sura, ayah)).forEach {
          assertThat(it.ayah).isIn(1..madaniAyahCounts[sura - 1])
        }
      }
    }
  }

  private fun generateSuraAyah(sura: Int, startAyah: Int, endAyah: Int): List<SuraAyah> {
    return (startAyah..endAyah).map {
      SuraAyah(sura, it)
    }
  }
}
