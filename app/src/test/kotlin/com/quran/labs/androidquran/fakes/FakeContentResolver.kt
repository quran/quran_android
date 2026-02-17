package com.quran.labs.androidquran.fakes

import android.net.Uri
import java.io.InputStream

/**
 * Fake implementation for testing content resolver operations.
 *
 * Provides configurable input stream responses for URI content access.
 * Used by QuranImportPresenterTest.
 *
 * Note: This is not a subclass of ContentResolver (which is final in many methods).
 * Instead, it provides a compatible interface that tests can use with dependency injection.
 */
class FakeContentResolver {

  private val inputStreams = mutableMapOf<Uri, InputStream?>()

  fun setInputStream(uri: Uri, stream: InputStream?) {
    inputStreams[uri] = stream
  }

  fun clearStreams() {
    inputStreams.clear()
  }

  fun openInputStream(uri: Uri): InputStream? {
    return inputStreams[uri]
  }
}
