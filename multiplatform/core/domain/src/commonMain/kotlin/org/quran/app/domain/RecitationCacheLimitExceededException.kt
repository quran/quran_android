package org.quran.app.domain

/** New audio can be downloaded after the learner explicitly frees storage. */
class RecitationCacheLimitExceededException(val limitBytes: Long) : IllegalStateException("Recitation storage limit reached")
