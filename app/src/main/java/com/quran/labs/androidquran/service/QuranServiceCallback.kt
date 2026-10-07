package com.quran.labs.androidquran.service

import androidx.media3.session.MediaLibraryService

/**
 * Callback for the [MediaLibraryService.MediaLibrarySession] to support media browsing
 * from external controllers (Android Auto, Google Assistant, etc.).
 *
 * Note: In Media3, transport controls (play/pause/stop/skip) are handled by the
 * [Player][androidx.media3.common.Player] directly and don't require callback overrides.
 * This callback exists for library-specific operations like
 * [onGetItem][MediaLibraryService.MediaLibrarySession.Callback.onGetItem],
 * [onGetChildren][MediaLibraryService.MediaLibrarySession.Callback.onGetChildren], etc.
 */
class QuranServiceCallback : MediaLibraryService.MediaLibrarySession.Callback
