package com.quran.mobile.feature.memorize.check

import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.memorize.check.DoubleCheckAdvice.Reason
import org.junit.Test

class DoubleCheckAdviceTest {

  private val gb = 1_000_000_000L

  @Test
  fun `recommended on phones with the memory for it`() {
    // an 8 GB phone reports a little less
    val advice = DoubleCheckAdvice.advise(7_400_000_000L, lowRamDevice = false, is64Bit = true, checkMs = 0)

    assertThat(advice.recommended).isTrue()
    assertThat(advice.reason).isEqualTo(Reason.SUITS)
  }

  @Test
  fun `not recommended with less memory, a low-RAM phone or a 32-bit processor`() {
    assertThat(DoubleCheckAdvice.advise(3_600_000_000L, false, true, 0).reason).isEqualTo(Reason.LOW_MEMORY)
    assertThat(DoubleCheckAdvice.advise(8 * gb, lowRamDevice = true, is64Bit = true, checkMs = 0).reason).isEqualTo(Reason.LOW_MEMORY)
    assertThat(DoubleCheckAdvice.advise(8 * gb, lowRamDevice = false, is64Bit = false, checkMs = 0).reason).isEqualTo(Reason.OLD_PROCESSOR)
  }

  @Test
  fun `once it has run here, how long it took decides`() {
    val fast = DoubleCheckAdvice.advise(8 * gb, false, true, checkMs = 600)
    assertThat(fast.recommended).isTrue()
    assertThat(fast.reason).isEqualTo(Reason.FAST)

    val slow = DoubleCheckAdvice.advise(8 * gb, false, true, checkMs = 2_400)
    assertThat(slow.recommended).isFalse()
    assertThat(slow.reason).isEqualTo(Reason.SLOW)
  }
}
