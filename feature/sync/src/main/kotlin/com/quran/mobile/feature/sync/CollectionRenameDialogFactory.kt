package com.quran.mobile.feature.sync

import androidx.fragment.app.DialogFragment

interface CollectionRenameDialogFactory {
  fun create(id: String, name: String): DialogFragment
}
