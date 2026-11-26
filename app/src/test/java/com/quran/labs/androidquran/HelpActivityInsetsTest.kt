package com.quran.labs.androidquran

import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class HelpActivityInsetsTest {

  @Test
  fun `dispatching window insets repeatedly does not accumulate bottom padding`() {
    ActivityScenario.launch(HelpActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        val helpWrapper = activity.findViewById<ViewGroup>(R.id.help_wrapper)
        val basePaddingBottom = helpWrapper.paddingBottom

        val firstInsets = WindowInsetsCompat.Builder()
          .setInsets(
            WindowInsetsCompat.Type.systemBars(),
            Insets.of(0, 24, 0, 48)
          )
          .build()

        ViewCompat.dispatchApplyWindowInsets(helpWrapper, firstInsets)
        val paddingAfterFirst = helpWrapper.paddingBottom

        ViewCompat.dispatchApplyWindowInsets(helpWrapper, firstInsets)
        val paddingAfterSecond = helpWrapper.paddingBottom

        assertThat(paddingAfterFirst).isEqualTo(basePaddingBottom + 48)
        assertThat(paddingAfterSecond).isEqualTo(paddingAfterFirst)

        val smallerInsets = WindowInsetsCompat.Builder()
          .setInsets(
            WindowInsetsCompat.Type.systemBars(),
            Insets.of(0, 24, 0, 16)
          )
          .build()
        ViewCompat.dispatchApplyWindowInsets(helpWrapper, smallerInsets)
        assertThat(helpWrapper.paddingBottom).isEqualTo(basePaddingBottom + 16)
      }
    }
  }
}
