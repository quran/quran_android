package com.quran.labs.androidquran.feature.audio.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class MD5CalculatorTest {

  @Test
  fun testMd5() {
    val calculator = MD5Calculator
    val hash = calculator.calculateHash(File("src/test/resources/bismillah.mp3"))
    assertThat(hash).isEqualTo("560b0b011b3bcf09f35b55e449f92a54")
  }
}
