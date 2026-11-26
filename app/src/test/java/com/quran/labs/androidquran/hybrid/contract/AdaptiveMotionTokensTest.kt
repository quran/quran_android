package com.quran.labs.androidquran.hybrid.contract

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class AdaptiveMotionTokensTest {

  @Test
  fun `all exported motion specs are finite and never infinite`() {
    val panelSpec = AdaptiveMotionTokens.panelSpring<Float>()
    val drawerSpec = AdaptiveMotionTokens.drawerSheetSpring<Float>()

    val specs: List<Any> = listOf(
      AdaptiveMotionTokens.TabSwitchFloatSpec,
      AdaptiveMotionTokens.TabSwitchColorSpec,
      AdaptiveMotionTokens.QuickFadeFloatSpec,
      panelSpec,
      drawerSpec
    )

    for (spec in specs) {
      assertThat(spec).isInstanceOf(FiniteAnimationSpec::class.java)
      assertThat(spec).isNotInstanceOf(InfiniteRepeatableSpec::class.java)
    }

    assertThat(AdaptiveMotionTokens.TabSwitchFloatSpec).isInstanceOf(TweenSpec::class.java)
    assertThat(AdaptiveMotionTokens.QuickFadeFloatSpec).isInstanceOf(TweenSpec::class.java)
    assertThat(panelSpec).isInstanceOf(SpringSpec::class.java)
    assertThat(drawerSpec).isInstanceOf(SpringSpec::class.java)
  }

  @Test
  fun `displayRefreshRateHz returns safe 60hz floor and areAnimationsEnabled reads animator scale`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertThat(AdaptiveMotionTokens.displayRefreshRateHz(context)).isAtLeast(60f)

    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    assertThat(AdaptiveMotionTokens.areAnimationsEnabled(context)).isFalse()

    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    assertThat(AdaptiveMotionTokens.areAnimationsEnabled(context)).isTrue()
  }
}
