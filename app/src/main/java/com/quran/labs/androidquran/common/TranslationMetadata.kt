package com.quran.labs.androidquran.common

import com.quran.labs.androidquran.data.SuraAyah

data class TranslationMetadata(val sura: Int,
                               val ayah: Int,
                               val text: CharSequence,
                               val link: SuraAyah? = null)
