package com.quran.labs.androidquran.model.bookmark


import com.quran.data.model.bookmark.BookmarkData
import com.quran.data.model.bookmark.Tag

import okio.Buffer
import java.io.IOException

import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat

class BookmarkJsonModelTest {

  companion object {
    private val TAGS = listOf(Tag(1, "First"), Tag(2, "Second"), Tag(3, "Third"))
  }

  private lateinit var jsonModel: BookmarkJsonModel

  @Before
  fun setUp() {
    jsonModel = BookmarkJsonModel()
  }

  @Test
  @Throws(IOException::class)
  fun simpleTestToFromJson() {
    val inputData = BookmarkData(TAGS, ArrayList(), ArrayList())
    val output = Buffer()
    jsonModel.toJson(output, inputData)
    val result = output.readUtf8()

    val buffer = Buffer().writeUtf8(result)
    val data = jsonModel.fromJson(buffer)
    assertThat(data).isNotNull()
    assertThat(data.bookmarks).isEmpty()
    assertThat(data.tags).hasSize(TAGS.size)
    assertThat(data.tags).isEqualTo(TAGS)
  }
}
