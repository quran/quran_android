package com.quran.labs.androidquran.feature.audio.dao

import com.quran.labs.androidquran.common.audio.model.QariItem

data class LocalUpdate(val qari: QariItem,
                       val files: List<String> = emptyList(),
                       val needsDatabaseUpgrade: Boolean = false)
