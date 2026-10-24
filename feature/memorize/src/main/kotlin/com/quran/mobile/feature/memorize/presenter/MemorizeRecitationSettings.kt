package com.quran.mobile.feature.memorize.presenter

import com.quran.data.di.AppScope
import com.quran.mobile.feature.memorize.session.MemorizeSessionController
import com.quran.recitation.presenter.RecitationSettings
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
class MemorizeRecitationSettings @Inject constructor(
  private val controller: MemorizeSessionController,
) : RecitationSettings {
  override fun isRecitationEnabled(): Boolean = true

  /** The audio bar's "hide/show verses" button: peek at the hidden words and back. */
  override fun toggleAyahVisibility() = controller.togglePeek()
}
