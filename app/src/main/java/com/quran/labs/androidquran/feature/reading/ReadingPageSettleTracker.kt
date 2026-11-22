package com.quran.labs.androidquran.feature.reading

import kotlin.math.abs

class ReadingPageSettleTracker {
  private var anchorPage: Int? = null
  private var anchorPosition: Int? = null

  fun anchor(page: Int, position: Int) {
    anchorPage = page
    anchorPosition = position
  }

  fun onSettled(page: Int, position: Int): SettleJump? {
    val fromPage = anchorPage
    val fromPosition = anchorPosition
    anchorPage = page
    anchorPosition = position
    if (fromPage == null || fromPosition == null || fromPage == page) {
      return null
    }
    if (abs(position - fromPosition) <= 1) {
      return null
    }
    return SettleJump(leaving = ReadingPlace(fromPage), destination = ReadingPlace(page))
  }
}

data class SettleJump(
  val leaving: ReadingPlace,
  val destination: ReadingPlace
)
