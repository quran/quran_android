package com.quran.mobile.feature.appicon.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

class AppIconCatalog(val sections: ImmutableList<AppIconSection>) {
  val options: List<AppIconOption> = sections.flatMap { it.options }
  val defaultOption: AppIconOption = options.single { it.isDefault }
}

@Immutable
data class AppIconSection(
  val id: String,
  @StringRes val title: Int,
  val previewSize: PreviewSize,
  val options: ImmutableList<AppIconOption>
) {
  enum class PreviewSize { LARGE, REGULAR }
}
