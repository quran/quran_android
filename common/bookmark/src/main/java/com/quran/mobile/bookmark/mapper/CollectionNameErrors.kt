package com.quran.mobile.bookmark.mapper

import com.quran.data.model.collection.CollectionNameException
import com.quran.shared.persistence.DuplicateCollectionNameException
import com.quran.shared.persistence.ReservedCollectionNameException
import com.quran.shared.persistence.TextLengthBound
import com.quran.shared.persistence.TextLengthField
import com.quran.shared.persistence.TextLengthLimitException

/** Rethrows mobile-sync's collection name rejections as [CollectionNameException]s. */
internal inline fun <T> mapCollectionNameErrors(block: () -> T): T {
  return try {
    block()
  } catch (exception: DuplicateCollectionNameException) {
    throw CollectionNameException.Duplicate(exception.name)
  } catch (exception: ReservedCollectionNameException) {
    throw CollectionNameException.Reserved(exception.name)
  } catch (exception: TextLengthLimitException) {
    if (exception.field != TextLengthField.COLLECTION_NAME) throw exception
    throw when (exception.bound) {
      TextLengthBound.TOO_SHORT -> CollectionNameException.TooShort(exception.limit)
      TextLengthBound.TOO_LONG -> CollectionNameException.TooLong(exception.limit)
    }
  }
}
