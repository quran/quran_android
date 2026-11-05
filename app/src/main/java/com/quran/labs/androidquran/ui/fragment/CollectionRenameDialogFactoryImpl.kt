package com.quran.labs.androidquran.ui.fragment

import com.quran.data.di.AppScope
import com.quran.mobile.feature.sync.CollectionRenameDialogFactory
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

@ContributesBinding(AppScope::class)
class CollectionRenameDialogFactoryImpl @Inject constructor() : CollectionRenameDialogFactory {
  override fun create(id: String, name: String) = AddTagDialog.newInstance(id, name)
}
