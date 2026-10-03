package org.quran.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class Study(val surah: Int, val ayah: Int) : NavKey
