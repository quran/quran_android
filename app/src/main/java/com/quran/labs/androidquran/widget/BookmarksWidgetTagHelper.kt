package com.quran.labs.androidquran.widget

import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.Tag

/**
 * Resolves the tag names shown on bookmarks widget rows.
 *
 * The widget lists bookmarks flat, without the tag headers the bookmarks tab groups
 * rows under, so a bookmark's tags are appended to its row's metadata instead (see
 * issue #2915). Only tags the user created are shown - the default collection, system
 * collections and read-only collections (for example the colour collections created by
 * older apps) are memberships, not user tags, and would add the same name to most rows.
 */
object BookmarksWidgetTagHelper {

  fun tagNames(bookmark: Bookmark, tagsById: Map<String, Tag>): List<String> {
    return bookmark.tags.mapNotNull { tagId ->
      tagsById[tagId]
        ?.takeIf { tag -> !tag.isSystem && !tag.isDefault && !tag.isReadOnly }
        ?.name
    }
  }

  fun metadataWithTags(metadata: String?, tagNames: List<String>): String? {
    if (tagNames.isEmpty()) {
      return metadata
    }
    val tagsText = tagNames.joinToString(", ")
    return if (metadata.isNullOrEmpty()) {
      tagsText
    } else {
      "$metadata • $tagsText"
    }
  }
}
