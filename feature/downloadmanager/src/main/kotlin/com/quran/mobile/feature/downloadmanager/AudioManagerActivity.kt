package com.quran.mobile.feature.downloadmanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.downloadmanager.di.DownloadManagerComponentInterface
import com.quran.mobile.feature.downloadmanager.presenter.AudioManagerPresenter
import com.quran.mobile.feature.downloadmanager.ui.LoadingIndicator
import com.quran.mobile.feature.downloadmanager.ui.ShuyookhList
import com.quran.mobile.feature.downloadmanager.ui.common.DownloadManagerToolbar
import kotlinx.collections.immutable.persistentListOf
import javax.inject.Inject

class AudioManagerActivity : ComponentActivity() {

  @Inject
  lateinit var audioManagerPresenter: AudioManagerPresenter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? DownloadManagerComponentInterface
    injector?.downloadManagerComponentFactory()?.generate()?.inject(this)

    val downloadedShuyookhFlow =
      audioManagerPresenter.downloadedShuyookh { QariItem.fromQari(this, it) }

    enableEdgeToEdge()

    setContent {
      val downloadedShuyookhState = downloadedShuyookhFlow.collectAsState(persistentListOf())
      QuranTheme {
        Column(modifier = Modifier
          .background(MaterialTheme.colorScheme.surface)
          .fillMaxSize()
          .windowInsetsPadding(
            WindowInsets.systemBars
              .union(WindowInsets.displayCutout)
              .only(WindowInsetsSides.Horizontal)
          )
        ) {
          DownloadManagerToolbar(
            title = stringResource(R.string.audio_manager),
            onBackPressed = { finish() }
          )

          val shuyookhDownloadInfo = downloadedShuyookhState.value
          if (shuyookhDownloadInfo.isEmpty()) {
            LoadingIndicator()
          } else {
            ShuyookhList(shuyookh = shuyookhDownloadInfo, onQariItemClicked = ::onQariClicked)
          }
        }
      }
    }
  }

  private fun onQariClicked(item: QariItem) {
    val intent = Intent(this, SheikhAudioDownloadsActivity::class.java).apply {
      putExtra(SheikhAudioDownloadsActivity.EXTRA_QARI_ID, item.id)
    }
    startActivity(intent)
  }
}
