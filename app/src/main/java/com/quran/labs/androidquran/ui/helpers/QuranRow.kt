package com.quran.labs.androidquran.ui.helpers

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.data.model.highlight.HighlightColor

class QuranRow private constructor(
  @JvmField val text: String?,
  @JvmField val metadata: String?,
  @JvmField val rowType: Int,
  @JvmField val sura: Int,
  @JvmField val ayah: Int,
  @JvmField val page: Int,
  @JvmField val imageResource: Int?,
  @JvmField val imageFilterColorResource: Int?,
  @JvmField val imageContentDescription: String?,
  @JvmField val juzType: Int?,
  @JvmField val juzOverlayText: String?,
  @JvmField val bookmarkId: String?,
  @JvmField val tagId: String?,
  @JvmField val bookmark: Bookmark?,
  @JvmField val dateAddedInMillis: Long,
  @JvmField val itemCount: Int?,
  @JvmField val isCollapsible: Boolean,
  @JvmField val isCollapsed: Boolean,
  // a collection users can't rename or delete: the system collections (Favorites) and the color
  // collections older Quran.com apps created
  @JvmField val isReadOnlyCollection: Boolean,
  @JvmField val highlightColor: HighlightColor?,
  @JvmField val readingBookmarkType: ReadingBookmarkType?
) {

  val isHeader: Boolean
    get() = rowType == HEADER || rowType == BOOKMARK_HEADER || rowType == HIGHLIGHTS_HEADER

  val isHighlightsHeader: Boolean
    get() = rowType == HIGHLIGHTS_HEADER

  val isBookmarkHeader: Boolean
    get() = rowType == BOOKMARK_HEADER

  val isEditableCollectionHeader: Boolean
    get() = isBookmarkHeader && tagId != null && !isReadOnlyCollection

  val isBookmark: Boolean
    get() = rowType == PAGE_BOOKMARK || rowType == AYAH_BOOKMARK

  val isReadingBookmark: Boolean
    get() = rowType == PAGE_READING_BOOKMARK || rowType == AYAH_READING_BOOKMARK

  val isPlacedReadingBookmark: Boolean
    get() = isReadingBookmark && readingBookmarkType != null

  val isAyahBookmark: Boolean
    get() = rowType == AYAH_BOOKMARK || rowType == AYAH_READING_BOOKMARK

  val isHighlightColor: Boolean
    get() = rowType == HIGHLIGHT_COLOR

  val isHighlightedAyah: Boolean
    get() = rowType == HIGHLIGHTED_AYAH

  class Builder {
    private var text: String? = null
    private var metadata: String? = null
    private var sura: Int = 0
    private var ayah: Int = 0
    private var page: Int = 0
    private var rowType: Int = NONE
    private var imageResource: Int? = null
    private var juzType: Int? = null
    private var tagId: String? = null
    private var bookmarkId: String? = null
    private var juzOverlayText: String? = null
    private var dateAddedInMillis: Long = 0L
    private var imageFilterColorResource: Int? = null
    private var imageContentDescription: String? = null
    private var bookmark: Bookmark? = null
    private var itemCount: Int? = null
    private var isCollapsible: Boolean = false
    private var isCollapsed: Boolean = false
    private var isReadOnlyCollection: Boolean = false
    private var highlightColor: HighlightColor? = null
    private var readingBookmarkType: ReadingBookmarkType? = null

    fun withType(type: Int): Builder = apply {
      rowType = type
    }

    fun withText(text: String?): Builder = apply {
      this.text = text
    }

    fun withMetadata(metadata: String?): Builder = apply {
      this.metadata = metadata
    }

    fun withBookmark(bookmark: Bookmark): Builder = apply {
      if (!bookmark.isPageBookmark()) {
        sura = bookmark.sura ?: 0
        ayah = bookmark.ayah ?: 0
      }
      page = bookmark.page
      this.bookmark = bookmark
      bookmarkId = bookmark.id
    }

    fun withSura(sura: Int): Builder = apply {
      this.sura = sura
    }

    /**
     * Sets the ayah number for rows that navigate to a specific ayah without owning a Bookmark.
     */
    fun withAyah(ayah: Int): Builder = apply {
      this.ayah = ayah
    }

    fun withPage(page: Int): Builder = apply {
      this.page = page
    }

    fun withImageResource(@DrawableRes resId: Int): Builder = apply {
      imageResource = resId
    }

    fun withImageOverlayColorResource(@ColorRes colorResource: Int): Builder = apply {
      imageFilterColorResource = colorResource
    }

    fun withImageContentDescription(contentDescription: String?): Builder = apply {
      imageContentDescription = contentDescription
    }

    fun withJuzType(juzType: Int): Builder = apply {
      this.juzType = juzType
    }

    fun withJuzOverlayText(text: String?): Builder = apply {
      juzOverlayText = text
    }

    fun withTagId(id: String?): Builder = apply {
      tagId = id
    }

    fun withDate(timeStamp: Long): Builder = apply {
      dateAddedInMillis = timeStamp * 1000
    }

    fun withItemCount(itemCount: Int): Builder = apply {
      this.itemCount = itemCount
    }

    fun withCollapsedState(isCollapsed: Boolean): Builder = apply {
      this.isCollapsible = true
      this.isCollapsed = isCollapsed
    }

    fun withReadOnlyCollection(isReadOnlyCollection: Boolean): Builder = apply {
      this.isReadOnlyCollection = isReadOnlyCollection
    }

    fun withHighlightColor(highlightColor: HighlightColor?): Builder = apply {
      this.highlightColor = highlightColor
    }

    fun withReadingBookmarkType(readingBookmarkType: ReadingBookmarkType?): Builder = apply {
      this.readingBookmarkType = readingBookmarkType
    }

    fun build(): QuranRow {
      return QuranRow(
        text = text,
        metadata = metadata,
        rowType = rowType,
        sura = sura,
        ayah = ayah,
        page = page,
        imageResource = imageResource,
        imageFilterColorResource = imageFilterColorResource,
        imageContentDescription = imageContentDescription,
        juzType = juzType,
        juzOverlayText = juzOverlayText,
        bookmarkId = bookmarkId,
        tagId = tagId,
        bookmark = bookmark,
        dateAddedInMillis = dateAddedInMillis,
        itemCount = itemCount,
        isCollapsible = isCollapsible,
        isCollapsed = isCollapsed,
        isReadOnlyCollection = isReadOnlyCollection,
        highlightColor = highlightColor,
        readingBookmarkType = readingBookmarkType
      )
    }
  }

  companion object {
    // Row Types
    const val NONE = 0
    const val HEADER = 1
    const val PAGE_BOOKMARK = 2
    const val AYAH_BOOKMARK = 3
    const val BOOKMARK_HEADER = 4
    const val PAGE_READING_BOOKMARK = 5
    const val AYAH_READING_BOOKMARK = 6
    const val HIGHLIGHT_COLOR = 7
    const val HIGHLIGHTED_AYAH = 8
    const val HIGHLIGHTS_HEADER = 9
  }
}
