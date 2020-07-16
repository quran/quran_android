package com.quran.labs.androidquran.ui.adapter;

import com.quran.labs.androidquran.dao.translation.TranslationItem;

public interface DownloadedMenuActionListener {
  void startMenuAction(TranslationItem item, DownloadedItemActionListener downloadedItemActionListener);
  void finishMenuAction();
}
