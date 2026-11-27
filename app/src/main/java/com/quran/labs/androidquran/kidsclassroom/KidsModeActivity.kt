package com.quran.labs.androidquran.kidsclassroom

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.mutableStateOf
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.QuranActivity
import com.quran.labs.androidquran.util.QuranSettings

/**
 * Dedicated full-screen **Kids Mode** activity hosting the interactive **Progress Islands** map,
 * **Rewards Vault** (Surah medallions, stars, streak, rank, milestones), and **My Space**
 * (profile, teacher Qari, repetition/difficulty controls, and tricky-word revision analysis).
 *
 * Pressing Back inside any Kids Mode Mushaf lesson sails cleanly back here to the Progress Islands.
 */
class KidsModeActivity : AppCompatActivity() {

  private val tabOverrideState = mutableStateOf<KidsModeTab?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    RewardsDataStore.initialize(applicationContext)
    KidsSurahAnalysisStore.ensureLoaded(applicationContext)
    val isNightMode = QuranSettings.getInstance(this).isNightMode
    val initialTab = parseTabExtra(intent)

    setContent {
      QuranTheme(useDarkTheme = isSystemInDarkTheme() || isNightMode) {
        KidsModeRootScreen(
          initialTab = initialTab,
          requestedTabOverride = tabOverrideState.value,
          onTabOverrideConsumed = { tabOverrideState.value = null },
          onExitToNormalMode = { exitToNormalMode() },
          onLaunchSurahLesson = { surah, ayah, launchMode ->
            launchSurahInKidsMode(surah, ayah, launchMode)
          }
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (intent.hasExtra(EXTRA_INITIAL_TAB)) {
      tabOverrideState.value = parseTabExtra(intent)
    }
  }

  private fun parseTabExtra(intent: Intent?): KidsModeTab {
    val raw = intent?.getStringExtra(EXTRA_INITIAL_TAB)
    return KidsModeTab.entries.firstOrNull { it.name == raw } ?: KidsModeTab.ISLANDS
  }

  private fun launchSurahInKidsMode(
    surah: Int,
    ayah: Int,
    launchMode: KidsLessonLaunchMode
  ) {
    val safeSurah = surah.coerceIn(1, 114)
    val safeAyah = ayah.coerceAtLeast(1)
    val quranInfo = QuranInfo(MadaniDataSource())
    val page = quranInfo.getPageFromSuraAyah(safeSurah, safeAyah).coerceIn(1, 604)

    // Ensure Tajweed Mushaf is pre-selected before PagerActivity creates its pages (avoids restart flash)
    QuranSettings.getInstance(this).pageType = "tajweed"

    val lessonIntent = Intent(this, PagerActivity::class.java).apply {
      putExtra("page", page)
      putExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, safeSurah)
      putExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, safeAyah)
      putExtra(PagerActivity.EXTRA_KIDS_SESSION_MODE, true)
      putExtra(PagerActivity.EXTRA_START_KIDS_MODE_SURAH, safeSurah)
      putExtra(PagerActivity.EXTRA_START_KIDS_MODE_AYAH, safeAyah)
      putExtra(PagerActivity.EXTRA_START_KIDS_AUTO_MODE, launchMode.name)
      addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    startActivity(lessonIntent)
    @Suppress("DEPRECATION")
    overridePendingTransition(R.anim.kids_lesson_enter, R.anim.kids_islands_exit)
  }

  private fun exitToNormalMode() {
    if (KidsClassroomManager.isEnabled()) {
      KidsClassroomManager.disableClassroomMode(this)
    }
    val normalIntent = Intent(this, QuranActivity::class.java).apply {
      addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    startActivity(normalIntent)
    finish()
  }

  companion object {
    const val EXTRA_INITIAL_TAB = "kids_mode_initial_tab"

    fun createIntent(
      context: Context,
      initialTab: KidsModeTab = KidsModeTab.ISLANDS
    ): Intent = Intent(context, KidsModeActivity::class.java).apply {
      putExtra(EXTRA_INITIAL_TAB, initialTab.name)
    }

    fun start(
      context: Context,
      initialTab: KidsModeTab = KidsModeTab.ISLANDS
    ) {
      context.startActivity(
        createIntent(context, initialTab).apply {
          addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
      )
    }
  }
}
