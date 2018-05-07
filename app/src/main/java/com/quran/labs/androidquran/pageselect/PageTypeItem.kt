package com.quran.labs.androidquran.pageselect

import android.support.annotation.StringRes

internal data class PageTypeItem(val pageType: String,
                                 val previewUrl: String,
                                 @StringRes val title: Int,
                                 @StringRes val subtitle: Int,
                                 val isSelected: Boolean = false)
