package org.quran.app.domain

/** Protects a prepared queue until the playback session relinquishes its source. */
interface RecitationLease {
    /** Idempotent, including when called from a canceled coroutine's finally block. */
    suspend fun release()
}
