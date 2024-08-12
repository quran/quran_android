package com.quran.labs.androidquran.common.mapper

import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
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

  private fun generateSuraAyah(sura: Int, startAyah: Int, endAyah: Int): List<SuraAyah> {
    return (startAyah..endAyah).map {
      SuraAyah(sura, it)
    }
  }
}
