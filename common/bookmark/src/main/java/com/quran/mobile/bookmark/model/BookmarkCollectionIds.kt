package com.quran.mobile.bookmark.model

import com.quran.shared.persistence.model.DEFAULT_COLLECTION_ID as SYNC_DEFAULT_COLLECTION_ID

/**
 * Canonical mobile-sync default collection ID exposed for app code that should not compile
 * directly against mobile-sync.
 */
val DEFAULT_BOOKMARK_COLLECTION_ID: String
  get() = SYNC_DEFAULT_COLLECTION_ID

fun String.isDefaultBookmarkCollectionId(): Boolean {
  return this == DEFAULT_BOOKMARK_COLLECTION_ID
}
