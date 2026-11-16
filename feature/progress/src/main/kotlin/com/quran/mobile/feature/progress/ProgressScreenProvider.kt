package com.quran.mobile.feature.progress

import android.content.Context
import com.quran.data.di.AppScope
import com.quran.mobile.di.ExtraScreenProvider
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/** "Progress" in the main screen's menu. */
@ContributesIntoSet(AppScope::class)
@Inject
class ProgressScreenProvider : ExtraScreenProvider {
  override val order: Int = 10
  override val id: Int = R.id.progress_menu
  override val titleResId: Int = R.string.progress_title

  override fun onClick(context: Context): Boolean {
    context.startActivity(ProgressActivity.intent(context))
    return true
  }
}
