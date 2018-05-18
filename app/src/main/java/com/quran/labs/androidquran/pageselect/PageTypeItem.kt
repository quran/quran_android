package com.quran.labs.androidquran.pageselect

import android.support.annotation.StringRes

data class PageTypeItem(val pageType: String,
                        val previewUrl: String,
                        @StringRes val title: Int,
                        @StringRes val description: Int,
                        val isSelected: Boolean = false)
