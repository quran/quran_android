package org.quran.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class Practice(val surah: Int, val ayah: Int) : NavKey
