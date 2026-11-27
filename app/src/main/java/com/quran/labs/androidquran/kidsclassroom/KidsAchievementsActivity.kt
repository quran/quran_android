package com.quran.labs.androidquran.kidsclassroom

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.util.QuranSettings

/**
 * Legacy entry point retained for compatibility — now renders the unified Full Kids Mode shell
 * opened directly on the **Rewards** vault tab, launching any Surah in Kids Session Mode so Back
 * returns smoothly to the Progress Islands.
 */
class KidsAchievementsActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    RewardsDataStore.initialize(applicationContext)
    KidsSurahAnalysisStore.ensureLoaded(applicationContext)
    val isNightMode = QuranSettings.getInstance(this).isNightMode

    setContent {
      QuranTheme(useDarkTheme = isSystemInDarkTheme() || isNightMode) {
        KidsModeRootScreen(
          initialTab = KidsModeTab.REWARDS,
          onExitToNormalMode = { finish() },
          onLaunchSurahLesson = { surah, ayah, launchMode ->
            launchSurahInKidsMode(surah, ayah, launchMode)
          }
        )
      }
    }
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
    QuranSettings.getInstance(this).pageType = "tajweed"
    val intent = Intent(this, PagerActivity::class.java).apply {
      putExtra("page", page)
      putExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, safeSurah)
      putExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, safeAyah)
      putExtra(PagerActivity.EXTRA_KIDS_SESSION_MODE, true)
      putExtra(PagerActivity.EXTRA_START_KIDS_MODE_SURAH, safeSurah)
      putExtra(PagerActivity.EXTRA_START_KIDS_MODE_AYAH, safeAyah)
      putExtra(PagerActivity.EXTRA_START_KIDS_AUTO_MODE, launchMode.name)
      addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    startActivity(intent)
    @Suppress("DEPRECATION")
    overridePendingTransition(R.anim.kids_lesson_enter, R.anim.kids_islands_exit)
    finish()
  }

  companion object {
    fun createIntent(context: Context): Intent =
      Intent(context, KidsAchievementsActivity::class.java)

    fun start(context: Context) {
      context.startActivity(createIntent(context))
    }
  }
}
