package com.quran.labs.androidquran.common.ui.core

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.quran.data.model.bookmark.Tag
import com.quran.data.model.collection.ReadingCollection
import com.quran.mobile.common.ui.core.R

object CollectionNames {
  fun displayName(context: Context, tag: Tag): String =
    displayName(context, tag.name, tag.isDefault)

  fun displayName(context: Context, collection: ReadingCollection): String =
    displayName(context, collection.name, collection.isDefault)

  private fun displayName(context: Context, name: String, isDefault: Boolean): String =
    if (isDefault) context.getString(R.string.default_collection) else name

  @Composable
  fun displayName(collection: ReadingCollection): String =
    if (collection.isDefault) stringResource(R.string.default_collection) else collection.name
}
