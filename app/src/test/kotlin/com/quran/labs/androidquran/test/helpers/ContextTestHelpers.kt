package com.quran.labs.androidquran.test.helpers

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when` as whenever

/**
 * Test helper extensions for mocking Android Context behavior.
 *
 * These helpers reduce boilerplate when setting up Context mocks in unit tests.
 *
 * Usage:
 * ```kotlin
 * @Mock lateinit var context: Context
 *
 * @Before
 * fun setup() {
 *   context.mockGetString(R.string.app_name, "Quran Android")
 *   context.mockGetStringWithFormat(R.string.bookmark_header, "Bookmarks: %d")
 *   context.mockResources(mockResources)
 * }
 * ```
 */

/**
 * Mock getString(int) to return a specific value.
 */
fun Context.mockGetString(id: Int, value: String) {
  whenever(this.getString(id)).thenReturn(value)
}

/**
 * Mock getString(int, vararg) with a format template.
 *
 * Example:
 * ```kotlin
 * context.mockGetStringWithFormat(R.string.bookmark_header, "Bookmarks: %d")
 * val result = context.getString(R.string.bookmark_header, 5)
 * // result == "Bookmarks: 5"
 * ```
 */
fun Context.mockGetStringWithFormat(id: Int, template: String) {
  whenever(this.getString(eq(id), any())).thenAnswer { invocation ->
    val args = invocation.arguments.drop(1).toTypedArray()
    String.format(template, *args)
  }
}

/**
 * Mock getResources() to return a specific Resources instance.
 */
fun Context.mockResources(resources: Resources) {
  whenever(this.resources).thenReturn(resources)
}

/**
 * Mock getContentResolver() to return a specific ContentResolver instance.
 */
fun Context.mockContentResolver(contentResolver: ContentResolver) {
  whenever(this.contentResolver).thenReturn(contentResolver)
}

/**
 * Mock getApplicationContext() to return a specific context (or itself).
 */
fun Context.mockApplicationContext(applicationContext: Context = this) {
  whenever(this.applicationContext).thenReturn(applicationContext)
}

/**
 * Configure multiple string resources at once.
 *
 * Example:
 * ```kotlin
 * context.mockStrings(mapOf(
 *   R.string.app_name to "Quran Android",
 *   R.string.settings to "Settings"
 * ))
 * ```
 */
fun Context.mockStrings(strings: Map<Int, String>) {
  strings.forEach { (id, value) ->
    mockGetString(id, value)
  }
}

/**
 * Configure multiple string resources with format templates.
 *
 * Example:
 * ```kotlin
 * context.mockStringFormats(mapOf(
 *   R.string.bookmark_header to "Bookmarks: %d",
 *   R.string.page_description to "Page %d of %d"
 * ))
 * ```
 */
fun Context.mockStringFormats(formats: Map<Int, String>) {
  formats.forEach { (id, template) ->
    mockGetStringWithFormat(id, template)
  }
}

/**
 * Mock a Context to return a default "Test" string for any getString() call.
 * Useful for tests that don't care about specific string values.
 *
 * Example:
 * ```kotlin
 * context.mockAnyString("Test")
 * val result = context.getString(R.string.anything)
 * // result == "Test"
 * ```
 */
fun Context.mockAnyString(defaultValue: String = "Test") {
  whenever(this.getString(anyInt())).thenReturn(defaultValue)
}
