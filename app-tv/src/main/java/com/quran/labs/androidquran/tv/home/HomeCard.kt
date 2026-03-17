package com.quran.labs.androidquran.tv.home

import androidx.compose.ui.graphics.vector.ImageVector

sealed class HomeCard(
  val id: String,
  val title: String,
  val description: String,
  val icon: ImageVector? = null
) {
  data object Surahs : HomeCard(
    id = "surahs",
    title = "Surahs",
    description = "Read all 114 chapters of the Quran"
  )

  data object Juz : HomeCard(
    id = "juz",
    title = "Juz",
    description = "30 sections of the Quran"
  )

  data object Bookmarks : HomeCard(
    id = "bookmarks",
    title = "Bookmarks",
    description = "Access your saved verses"
  )

  data object ContinueReading : HomeCard(
    id = "continue_reading",
    title = "Continue Reading",
    description = "Resume from where you left off"
  )

  data object Search : HomeCard(
    id = "search",
    title = "Search",
    description = "Find verses in the Quran"
  )

  data object Settings : HomeCard(
    id = "settings",
    title = "Settings",
    description = "Customize your reading experience"
  )
}

val homeCards = listOf(
  HomeCard.Surahs,
  HomeCard.Juz,
  HomeCard.Bookmarks,
  HomeCard.ContinueReading,
  HomeCard.Search,
  HomeCard.Settings
)
