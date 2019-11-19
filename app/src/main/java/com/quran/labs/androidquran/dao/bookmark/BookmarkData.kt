package com.quran.labs.androidquran.dao.bookmark

import com.quran.labs.androidquran.dao.RecentPage
import com.quran.labs.androidquran.dao.Tag
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BookmarkData(val tags: List<Tag> = emptyList(),
                        val bookmarks: List<Bookmark> = emptyList(),
                        val recentPages: List<RecentPage> = emptyList())
