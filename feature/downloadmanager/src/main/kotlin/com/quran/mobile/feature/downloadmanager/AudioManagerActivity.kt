package com.quran.mobile.feature.downloadmanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.downloadmanager.di.DownloadManagerComponentInterface
import com.quran.mobile.feature.downloadmanager.presenter.AudioManagerPresenter
import com.quran.mobile.feature.downloadmanager.ui.SheikhDownloadSummary
import javax.inject.Inject


class AudioManagerActivity : ComponentActivity() {

  @Inject
  lateinit var audioManagerPresenter: AudioManagerPresenter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? DownloadManagerComponentInterface
    injector?.downloadManagerComponentBuilder()?.build()?.inject(this)

    val downloadedShuyookhFlow =
      audioManagerPresenter.downloadedShuyookh { QariItem.fromQari(this, it) }

    setContent {
      val downloadedShuyookhState = downloadedShuyookhFlow.collectAsState(emptyList())
      QuranTheme {
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
          TopAppBar(
            title = {
              Text(
                text = stringResource(R.string.audio_manager),
                color = MaterialTheme.colorScheme.onPrimary
              )
            },
            navigationIcon = {
              IconButton(onClick = { finish() }) {
                Icon(
                  imageVector = Icons.Filled.ArrowBack,
                  contentDescription = "",
                  tint = MaterialTheme.colorScheme.onPrimary
                )
              }
            },
            backgroundColor = MaterialTheme.colorScheme.primary
          )

          Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            downloadedShuyookhState.value.forEach {
              SheikhDownloadSummary(it, ::onQariClicked)
            }
          }
        }
      }
    }
  }

  private fun onQariClicked(item: QariItem) {
    val className = "com.quran.labs.androidquran.ui.SheikhAudioManagerActivity"
    val intent = Intent(this, Class.forName(className)).apply {
      putExtra("SurahAudioManager.EXTRA_SHEIKH", item)
    }
    startActivity(intent)
  }
}
