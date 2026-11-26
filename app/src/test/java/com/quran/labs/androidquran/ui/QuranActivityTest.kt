package com.quran.labs.androidquran.ui

import android.content.Intent
import android.os.Looper
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.hybrid.contract.HybridNavTab
import com.quran.labs.androidquran.hybrid.hub.HubIndexSegment
import com.quran.labs.androidquran.util.QuranSettings
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration

@Config(application = TestApplication::class, sdk = [33], qualifiers = "w412dp-h915dp")
@RunWith(RobolectricTestRunner::class)
class QuranActivityTest {

  @Before
  fun setup() {
    @Suppress("DEPRECATION")
    ShadowChoreographer.setPostFrameCallbackDelay(1)
    QuranSettings.getInstance(ApplicationProvider.getApplicationContext()).pageType = "madani"
    Intents.init()
  }

  @After
  fun teardown() {
    Intents.release()
  }

  @Test
  fun testClickingOnSuraOnListViewNavigatesToSura() {
    Robolectric.buildActivity(QuranActivity::class.java).use { controller ->
      val activity = controller.create().start().resume().visible().get()
      advanceNativeFrames(activity.hubView)

      assertThat(activity.hubView.containsVisibleText("Al-Qur'an Al-Kareem")).isTrue()
      assertThat(activity.hubView.containsVisibleText("Continue Reading")).isTrue()

      val fatihaNode = activity.hubView.findClickableContainingText("Surah Al-Fātihah")
      assertThat(fatihaNode).isNotNull()
      assertThat(fatihaNode!!.performClick()).isTrue()
      advanceNativeFrames(activity.hubView)
    }

    Intents.intended(IntentMatchers.hasComponent(PagerActivity::class.java.name))
  }

  @Test
  fun testSegmentedIndexTabsAnd4TabNavLaunchAndSwitchCorrectly() {
    Robolectric.buildActivity(QuranActivity::class.java).use { controller ->
      val activity = controller.create().start().resume().visible().get()
      advanceNativeFrames(activity.hubView)

      // Switch to Juz and verify state + rendered Juz cards (Juz' 2 is distinct from Hero Juz' 1)
      val juzTab = activity.findHubViewByTag("hub_segment_juz")
      assertThat(juzTab).isNotNull()
      assertThat(juzTab!!.performClick()).isTrue()
      advanceNativeFrames(activity.hubView)
      assertThat(activity.hubStateHolder.uiState.value.indexSegment).isEqualTo(HubIndexSegment.JUZ)
      assertThat(activity.hubView.containsVisibleText("Juz' 2")).isTrue()

      // Switch to Hizb and verify state + rendered Hizb cards
      val hizbTab = activity.findHubViewByTag("hub_segment_hizb")
      assertThat(hizbTab).isNotNull()
      assertThat(hizbTab!!.performClick()).isTrue()
      advanceNativeFrames(activity.hubView)
      assertThat(activity.hubStateHolder.uiState.value.indexSegment).isEqualTo(HubIndexSegment.HIZB)
      assertThat(activity.hubView.containsVisibleText("Hizb 1")).isTrue()

      // Switch to SEARCH tab via FloatingBottomNav
      val searchNav = activity.hubView.findClickableWithText("Search")
      assertThat(searchNav).isNotNull()
      assertThat(searchNav!!.performClick()).isTrue()
      advanceNativeFrames(activity.hubView)
      assertThat(activity.hubStateHolder.uiState.value.selectedTab).isEqualTo(HybridNavTab.SEARCH)

      // Tap READ tab -> opens PagerActivity while keeping selectedTab on Hub tab
      val readNav = activity.hubView.findClickableWithText("Read")
      assertThat(readNav).isNotNull()
      assertThat(readNav!!.performClick()).isTrue()
      advanceNativeFrames(activity.hubView)
      assertThat(activity.hubStateHolder.uiState.value.selectedTab).isEqualTo(HybridNavTab.SEARCH)
    }

    Intents.intended(IntentMatchers.hasComponent(PagerActivity::class.java.name))
  }

  @Test
  fun testMainHubHasNoOptionsMenuOverflowAndPaletteSheetOpens() {
    Robolectric.buildActivity(QuranActivity::class.java).use { controller ->
      val activity = controller.create().start().resume().visible().get()
      advanceNativeFrames(activity.hubView)

      // Zero legacy Toolbar / 3-dot overflow menu
      assertThat(activity.supportActionBar).isNull()

      val paletteButton = activity.findHubViewByTag("hub_palette_button")
      assertThat(paletteButton).isNotNull()
      assertThat(paletteButton!!.performClick()).isTrue()
      advanceNativeFrames(activity.hubView)

      assertThat(activity.hubView.containsVisibleText("Calm Palette")).isTrue()
    }
  }

  @Test
  fun testInitialNavTabExtraAndOnNewIntentSwitchBetweenIndexAndBookmarks() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val initialIntent = Intent(context, QuranActivity::class.java).apply {
      putExtra(QuranActivity.EXTRA_INITIAL_NAV_TAB, HybridNavTab.BOOKMARKS.name)
    }

    Robolectric.buildActivity(QuranActivity::class.java, initialIntent).use { controller ->
      val activity = controller.create().start().resume().visible().get()
      advanceNativeFrames(activity.hubView)

      assertThat(activity.hubStateHolder.uiState.value.selectedTab).isEqualTo(HybridNavTab.BOOKMARKS)
      assertThat(activity.hubStateHolder.uiState.value.bookmarks.isLoaded).isTrue()

      controller.newIntent(
        Intent(context, QuranActivity::class.java).putExtra(
          QuranActivity.EXTRA_INITIAL_NAV_TAB,
          HybridNavTab.INDEX.name
        )
      )
      advanceNativeFrames(activity.hubView)
      assertThat(activity.hubStateHolder.uiState.value.selectedTab).isEqualTo(HybridNavTab.INDEX)

      controller.newIntent(
        Intent(context, QuranActivity::class.java).putExtra(
          QuranActivity.EXTRA_INITIAL_NAV_TAB,
          HybridNavTab.BOOKMARKS.name
        )
      )
      advanceNativeFrames(activity.hubView)
      assertThat(activity.hubStateHolder.uiState.value.selectedTab).isEqualTo(HybridNavTab.BOOKMARKS)

      // READ is never stored as selectedTab
      controller.newIntent(
        Intent(context, QuranActivity::class.java).putExtra(
          QuranActivity.EXTRA_INITIAL_NAV_TAB,
          HybridNavTab.READ.name
        )
      )
      advanceNativeFrames(activity.hubView)
      assertThat(activity.hubStateHolder.uiState.value.selectedTab).isEqualTo(HybridNavTab.BOOKMARKS)
    }
  }
}

private fun advanceNativeFrames(rootView: View) {
  val mainLooper = Shadows.shadowOf(Looper.getMainLooper())
  Choreographer.getInstance().postFrameCallback { }
  mainLooper.idle()
  repeat(4) {
    Choreographer.getInstance().postFrameCallback { }
    mainLooper.idleFor(Duration.ofMillis(16))
    mainLooper.idle()
  }
  val width = rootView.width.takeIf { it > 0 } ?: 1080
  val height = rootView.height.takeIf { it > 0 } ?: 1920
  fun forceTree(v: View) {
    v.forceLayout()
    if (v is ViewGroup) {
      for (i in 0 until v.childCount) forceTree(v.getChildAt(i))
    }
  }
  forceTree(rootView)
  rootView.measure(
    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
  )
  rootView.layout(0, 0, width, height)
  mainLooper.idle()
}

private fun QuranActivity.findHubViewByTag(tag: String): View? {
  fun walk(v: View): View? {
    if (v.visibility != View.VISIBLE) return null
    if (v.tag == tag) return v
    if (v is ViewGroup) {
      for (i in 0 until v.childCount) {
        walk(v.getChildAt(i))?.let { return it }
      }
    }
    return null
  }
  return walk(hubView)
}

private fun View.containsVisibleText(expected: String): Boolean {
  if (visibility != View.VISIBLE) return false
  if (this is TextView && text?.toString()?.contains(expected) == true) return true
  if (this is ViewGroup) {
    for (i in 0 until childCount) {
      if (getChildAt(i).containsVisibleText(expected)) return true
    }
  }
  return false
}

private fun View.findClickableContainingText(expected: String): View? {
  if (visibility != View.VISIBLE) return null
  if (isClickable && containsVisibleText(expected)) return this
  if (this is ViewGroup) {
    for (i in 0 until childCount) {
      getChildAt(i).findClickableContainingText(expected)?.let { return it }
    }
  }
  return null
}

private fun View.findClickableWithText(expected: String): View? {
  if (visibility != View.VISIBLE) return null
  if (isClickable && exactTextInside() == expected) return this
  if (this is ViewGroup) {
    for (i in 0 until childCount) {
      getChildAt(i).findClickableWithText(expected)?.let { return it }
    }
  }
  return null
}

private fun View.exactTextInside(): String {
  if (visibility != View.VISIBLE) return ""
  if (this is TextView) return text?.toString().orEmpty()
  if (this is ViewGroup) {
    val sb = StringBuilder()
    for (i in 0 until childCount) {
      sb.append(getChildAt(i).exactTextInside())
    }
    return sb.toString()
  }
  return ""
}
