package com.quran.labs.androidquran.common

import com.quran.data.model.SuraAyah

data class TranslationMetadata(val sura: Int,
                               val ayah: Int,
                               val text: CharSequence,
                               val localTranslationId: Int? = null,
                               val link: SuraAyah? = null)
