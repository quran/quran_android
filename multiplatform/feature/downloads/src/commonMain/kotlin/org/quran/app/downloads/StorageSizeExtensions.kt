package org.quran.app.downloads

/** Avoid hiding a small recording as zero and avoid addition overflow for large legacy totals. */
fun Long.toKibibytesRoundedUp(): Long = this / 1024 + if (this % 1024 == 0L) 0 else 1
fun Long.toMebibytes(): Long = this / (1024 * 1024)
