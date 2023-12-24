package com.quran.labs.androidquran.dao.translation

import com.quran.mobile.translation.model.LocalTranslation

data class TranslationItem @JvmOverloads constructor(val translation: Translation,
                                                     val localVersion: Int = 0,
                                                     val displayOrder: Int = -1) : TranslationRowData {

  override fun isSeparator() = false

  fun exists() = localVersion > 0

  override fun name() = this.translation.displayName

  override fun needsUpgrade(): Boolean {
    return localVersion > 0 && this.translation.currentVersion > this.localVersion
  }

  fun withTranslationRemoved() = this.copy(localVersion = 0)

  fun withDisplayOrder(newDisplayOrder: Int) = this.copy(displayOrder = newDisplayOrder)

  fun withLocalVersionAndDisplayOrder(newVersion: Int, displayOrder: Int) = this.copy(localVersion = newVersion, displayOrder = displayOrder)

  fun asLocalTranslation(): LocalTranslation {
    return LocalTranslation(
      id = translation.id.toLong(),
      filename = translation.fileName,
      name = translation.displayName,
      translator = translation.translator,
      translatorForeign = translation.translatorNameLocalized,
      url = translation.fileUrl,
      languageCode = translation.languageCode,
      version = localVersion,
      minimumVersion = translation.minimumVersion,
      displayOrder = displayOrder
    )
  }
}
