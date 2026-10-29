package com.quran.data.model.collection

/**
 * The collection that legacy page bookmarks move into. Every app stores and syncs this fixed English
 * name, recognizes it by name, and translates it only when showing it.
 */
object OldPageBookmarksCollection {
  const val NAME = "Old Page Bookmarks"

  /** Whether [name] is this collection's. Names compare trimmed and case-insensitively. */
  fun isNamed(name: String): Boolean = name.trim().equals(NAME, ignoreCase = true)
}
