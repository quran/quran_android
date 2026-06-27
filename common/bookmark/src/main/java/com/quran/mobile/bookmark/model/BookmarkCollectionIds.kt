package com.quran.mobile.bookmark.model

import com.quran.shared.persistence.model.DEFAULT_COLLECTION_ID as SYNC_DEFAULT_COLLECTION_ID

/**
 * Canonical mobile-sync default collection ID exposed for app code that should not compile
 * directly against mobile-sync.
 */
val DEFAULT_BOOKMARK_COLLECTION_ID: String
  get() = SYNC_DEFAULT_COLLECTION_ID

/**
 * Returns true when this ID belongs to the virtual default bookmark collection.
 *
 * Prefer `Collection.isDefault` when a mobile-sync collection object is available. This helper is
 * kept for legacy app UI rows that currently carry only a tag ID.
 */
fun String.isDefaultBookmarkCollectionId(): Boolean {
  return this == DEFAULT_BOOKMARK_COLLECTION_ID
}
