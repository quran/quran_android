package com.quran.labs.androidquran.pageselect

import androidx.annotation.StringRes
import java.io.File

data class PageTypeItem(val pageType: String,
                        val previewImage: File?,
                        @StringRes val title: Int,
                        @StringRes val description: Int,
                        val isSelected: Boolean = false)
