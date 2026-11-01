package com.quran.labs.androidquran.common.ui.core

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.quran.data.model.bookmark.Tag
import com.quran.data.model.collection.OldPageBookmarksCollection
import com.quran.data.model.collection.ReadingCollection
import com.quran.mobile.common.ui.core.R

object CollectionNames {
  fun displayName(context: Context, tag: Tag): String =
    displayName(context, tag.name, tag.isDefault)

  fun displayName(context: Context, collection: ReadingCollection): String =
    displayName(context, collection.name, collection.isDefault)

  private fun displayName(context: Context, name: String, isDefault: Boolean): String =
    localizedNameResource(name, isDefault)?.let(context::getString) ?: name

  @Composable
  fun displayName(collection: ReadingCollection): String =
    displayName(collection.name, collection.isDefault)

  @Composable
  fun displayName(name: String, isDefault: Boolean): String =
    localizedNameResource(name, isDefault)?.let { stringResource(it) } ?: name

  @StringRes
  private fun localizedNameResource(name: String, isDefault: Boolean): Int? = when {
    isDefault -> R.string.default_collection
    name.trim().equals(OldPageBookmarksCollection.NAME, ignoreCase = true) -> R.string.old_page_bookmarks
    else -> null
  }
}
