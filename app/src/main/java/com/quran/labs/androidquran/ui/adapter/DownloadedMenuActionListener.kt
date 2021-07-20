package com.quran.labs.androidquran.ui.adapter

import com.quran.labs.androidquran.dao.translation.TranslationItem

interface DownloadedMenuActionListener {
  fun startMenuAction(item: TranslationItem, downloadedItemActionListener: DownloadedItemActionListener?)
  fun finishMenuAction()
}
