package com.quran.labs.androidquran.test.fakes

import android.content.Context
import androidx.annotation.StringRes
import com.quran.data.model.SuraAyah
import com.quran.page.common.data.QuranNaming

/**
 * Fake implementation of QuranDisplayData for testing.
 *
 * Pattern: Simple configuration with default values
 *
 * Usage:
 * ```
 * val fakeDisplayData = FakeQuranDisplayData()
 * fakeDisplayData.setSuraName("Al-Fatiha")
 * fakeDisplayData.setNotificationTitle("Playing Al-Fatiha")
 *
 * // Use in presenter
 * val presenter = AudioPresenter(fakeDisplayData, ...)
 * ```
 */
class FakeQuranDisplayData : QuranNaming {

  private var suraName: String = "Test Sura"
  private var suraNameWithNumber: String = "1. Test Sura"
  private var notificationTitle: String = "Test Notification"
  private var ayahString: String = "1:1"

  private val getSuraNameCalls = mutableListOf<GetSuraNameCall>()
  private val getNotificationTitleCalls = mutableListOf<GetNotificationTitleCall>()

  data class GetSuraNameCall(val sura: Int, val wantPrefix: Boolean)

  data class GetNotificationTitleCall(
    val minVerse: SuraAyah,
    val maxVerse: SuraAyah,
    val isGapless: Boolean
  )

  override fun getSuraName(context: Context, sura: Int, wantPrefix: Boolean): String {
    getSuraNameCalls.add(GetSuraNameCall(sura, wantPrefix))
    return suraName
  }

  override fun getSuraNameWithNumber(context: Context, sura: Int, wantPrefix: Boolean): String {
    getSuraNameCalls.add(GetSuraNameCall(sura, wantPrefix))
    return suraNameWithNumber
  }

  fun getNotificationTitle(
    context: Context,
    minVerse: SuraAyah,
    maxVerse: SuraAyah,
    isGapless: Boolean
  ): String {
    getNotificationTitleCalls.add(GetNotificationTitleCall(minVerse, maxVerse, isGapless))
    return notificationTitle
  }

  fun getSuraAyahString(context: Context, sura: Int, ayah: Int): String {
    return ayahString
  }

  fun getSuraAyahString(
    context: Context,
    sura: Int,
    ayah: Int,
    @StringRes resource: Int
  ): String {
    return ayahString
  }

  // Configuration methods
  fun setSuraName(name: String) {
    suraName = name
  }

  fun setSuraNameWithNumber(name: String) {
    suraNameWithNumber = name
  }

  fun setNotificationTitle(title: String) {
    notificationTitle = title
  }

  fun setAyahString(string: String) {
    ayahString = string
  }

  // Assertion helpers
  fun assertGetSuraNameCalled(sura: Int, wantPrefix: Boolean) {
    require(getSuraNameCalls.any { it.sura == sura && it.wantPrefix == wantPrefix }) {
      "Expected getSuraName($sura, $wantPrefix) but was called with: $getSuraNameCalls"
    }
  }

  fun assertGetNotificationTitleCalled() {
    require(getNotificationTitleCalls.isNotEmpty()) {
      "Expected getNotificationTitle() to be called but it wasn't"
    }
  }

  // Query helpers
  fun getGetSuraNameCallCount(): Int = getSuraNameCalls.size

  fun getGetNotificationTitleCallCount(): Int = getNotificationTitleCalls.size

  fun getLastGetSuraNameCall(): GetSuraNameCall? = getSuraNameCalls.lastOrNull()

  fun getLastGetNotificationTitleCall(): GetNotificationTitleCall? =
    getNotificationTitleCalls.lastOrNull()

  // Reset for test isolation
  fun reset() {
    getSuraNameCalls.clear()
    getNotificationTitleCalls.clear()
    suraName = "Test Sura"
    suraNameWithNumber = "1. Test Sura"
    notificationTitle = "Test Notification"
    ayahString = "1:1"
  }
}
