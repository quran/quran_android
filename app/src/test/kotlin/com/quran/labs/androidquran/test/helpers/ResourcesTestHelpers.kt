package com.quran.labs.androidquran.test.helpers

import android.content.res.Resources
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.`when` as whenever

/**
 * Test helper extensions for mocking Android Resources behavior.
 *
 * These helpers reduce boilerplate when setting up Resources mocks in unit tests.
 *
 * Usage:
 * ```kotlin
 * @Mock lateinit var resources: Resources
 *
 * @Before
 * fun setup() {
 *   resources.mockStringArray(R.array.sura_names, arrayOf("Al-Fatiha", "Al-Baqarah"))
 * }
 * ```
 */

/**
 * Mock getStringArray(int) to return a specific array.
 *
 * Example:
 * ```kotlin
 * resources.mockStringArray(R.array.sura_names, arrayOf("Al-Fatiha", "Al-Baqarah"))
 * val result = resources.getStringArray(R.array.sura_names)
 * // result == ["Al-Fatiha", "Al-Baqarah"]
 * ```
 */
fun Resources.mockStringArray(id: Int, value: Array<String>) {
  whenever(this.getStringArray(id)).thenReturn(value)
}

/**
 * Mock getInteger(int) to return a specific value.
 */
fun Resources.mockInteger(id: Int, value: Int) {
  whenever(this.getInteger(id)).thenReturn(value)
}

/**
 * Mock getBoolean(int) to return a specific value.
 */
fun Resources.mockBoolean(id: Int, value: Boolean) {
  whenever(this.getBoolean(id)).thenReturn(value)
}

/**
 * Mock getDimension(int) to return a specific value.
 */
fun Resources.mockDimension(id: Int, value: Float) {
  whenever(this.getDimension(id)).thenReturn(value)
}

/**
 * Mock getColor(int) to return a specific value.
 */
fun Resources.mockColor(id: Int, value: Int) {
  whenever(this.getColor(id)).thenReturn(value)
}

/**
 * Configure multiple string arrays at once.
 *
 * Example:
 * ```kotlin
 * resources.mockStringArrays(mapOf(
 *   R.array.sura_names to arrayOf("Al-Fatiha", "Al-Baqarah"),
 *   R.array.juz_names to arrayOf("Juz 1", "Juz 2")
 * ))
 * ```
 */
fun Resources.mockStringArrays(arrays: Map<Int, Array<String>>) {
  arrays.forEach { (id, value) ->
    mockStringArray(id, value)
  }
}

/**
 * Mock any getStringArray() call to return a default array.
 * Useful for tests that don't care about specific array values.
 *
 * Example:
 * ```kotlin
 * resources.mockAnyStringArray(emptyArray())
 * val result = resources.getStringArray(R.array.anything)
 * // result == []
 * ```
 */
fun Resources.mockAnyStringArray(defaultValue: Array<String> = emptyArray()) {
  whenever(this.getStringArray(anyInt())).thenReturn(defaultValue)
}
