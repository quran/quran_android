package org.quran.app.memorization

/** Invalidates asynchronous imports when the learner changes source or leaves the session. */
internal class PlaybackLifetime(var active: Boolean = true, var importGeneration: Long = 0)
