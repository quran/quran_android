package com.quran.labs.androidquran.presenter.translationlist

import com.quran.mobile.translation.model.LocalTranslation

fun interface TranslationListCallback {
  fun onTranslationsUpdated(
    titles: Array<String>,
    translations: List<LocalTranslation>
  )
}
