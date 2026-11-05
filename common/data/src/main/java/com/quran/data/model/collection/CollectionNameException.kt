package com.quran.data.model.collection

/**
 * Why a collection (tag) name was refused. Each extends [IllegalArgumentException], so callers that
 * catch that still handle it. Messages leave out the name, which is user text.
 */
sealed class CollectionNameException(message: String) : IllegalArgumentException(message) {

  /** Another collection has the name. Names compare trimmed and case-insensitively. */
  class Duplicate(val existingName: String) :
    CollectionNameException("Another collection has this name.")

  /** Favorites, or a name the server reserves (those starting with `system:`). */
  class Reserved(val name: String) : CollectionNameException("This collection name is reserved.")

  /** Shorter than [minLength] once trimmed, such as a blank name. */
  class TooShort(val minLength: Int) :
    CollectionNameException("Collection names need at least $minLength characters.")

  /** Longer than [maxLength]. */
  class TooLong(val maxLength: Int) :
    CollectionNameException("Collection names can't be longer than $maxLength characters.")
}
