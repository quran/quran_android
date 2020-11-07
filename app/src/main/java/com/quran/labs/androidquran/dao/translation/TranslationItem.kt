package com.quran.labs.androidquran.dao.translation

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

  fun withTranslationVersion(version: Int) = this.copy(localVersion = version)

  fun withDisplayOrder(newDisplayOrder: Int) = this.copy(displayOrder = newDisplayOrder)
}
