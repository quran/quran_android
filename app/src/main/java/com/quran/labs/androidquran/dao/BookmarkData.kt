package com.quran.labs.androidquran.dao

data class BookmarkData(val tags: List<Tag> = emptyList(),
                        val bookmarks: List<Bookmark> = emptyList(),
                        val recentPages: List<RecentPage> = emptyList())
