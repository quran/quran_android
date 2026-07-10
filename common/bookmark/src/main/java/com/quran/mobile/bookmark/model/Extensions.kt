package com.quran.mobile.bookmark.model

import com.quran.data.model.bookmark.AyahBookmark
import com.quran.data.model.collection.ReadingCollection
import com.quran.data.model.collection.ReadingCollectionBookmarks
import com.quran.shared.persistence.model.Collection
import com.quran.shared.persistence.model.CollectionAyahBookmark
import com.quran.shared.persistence.model.CollectionWithAyahBookmarks

internal fun CollectionWithAyahBookmarks.asReadingCollectionBookmarks(): ReadingCollectionBookmarks {
  return ReadingCollectionBookmarks(
    readingCollection = collection.asReadingCollection(),
    bookmarks = bookmarks.map { it.asAyahBookmark() }
  )
}

internal fun Collection.asReadingCollection(): ReadingCollection {
  return ReadingCollection(
    id = id,
    name = name,
    lastUpdated = lastUpdated,
    isSystem = isDefault
  )
}

private fun CollectionAyahBookmark.asAyahBookmark(): AyahBookmark {
  return AyahBookmark(
    sura = sura,
    ayah = ayah,
    addedDate = bookmarkAddedDate,
    lastUpdated = bookmarkLastUpdated
  )
}
