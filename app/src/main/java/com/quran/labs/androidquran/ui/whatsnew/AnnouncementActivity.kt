package com.quran.labs.androidquran.ui.whatsnew

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.IntentCompat
import com.quran.labs.androidquran.BuildConfig
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.ui.QuranActivity
import com.quran.labs.androidquran.util.QuranSettings

class AnnouncementActivity : AppCompatActivity() {
  private lateinit var quranSettings: QuranSettings
  private lateinit var destination: Intent

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    quranSettings = QuranSettings.getInstance(this)
    destination = IntentCompat.getParcelableExtra(intent, EXTRA_DESTINATION, Intent::class.java)
      ?: Intent(this, QuranActivity::class.java).apply {
        putExtra(QuranActivity.EXTRA_SHOW_TRANSLATION_UPGRADE, quranSettings.haveUpdatedTranslations())
      }

    if (!shouldShow(quranSettings)) {
      launchDestination()
    } else {
      enableEdgeToEdge()
      setContentView(ComposeView(this).apply {
        setContent {
          QuranTheme {
            IconRevealScreen(
              onContinue = ::dismissAnnouncement,
              onNightChange = { isNight ->
                if (isNight) {
                  val night = SystemBarStyle.dark(Color.TRANSPARENT)
                  enableEdgeToEdge(statusBarStyle = night, navigationBarStyle = night)
                } else {
                  enableEdgeToEdge()
                }
              }
            )
          }
        }
      })
      onBackPressedDispatcher.addCallback(this) { dismissAnnouncement() }
    }
  }

  private fun dismissAnnouncement() {
    if (!isFinishing) {
      markSeen(quranSettings)
      launchDestination()
    }
  }

  private fun launchDestination() {
    startActivity(destination)
    finish()
  }

  companion object {
    private const val CURRENT_ANNOUNCEMENT_ID = 2
    private const val EXTRA_DESTINATION = "announcementDestination"

    fun shouldShow(quranSettings: QuranSettings): Boolean {
      @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
      return BuildConfig.FLAVOR == "madani" &&
          quranSettings.shouldShowAnnouncement(CURRENT_ANNOUNCEMENT_ID)
    }

    fun markSeen(quranSettings: QuranSettings) {
      quranSettings.markAnnouncementSeen(CURRENT_ANNOUNCEMENT_ID)
    }

    fun createIntent(context: Context, destination: Intent): Intent {
      return Intent(context, AnnouncementActivity::class.java)
        .putExtra(EXTRA_DESTINATION, destination)
    }
  }
}
