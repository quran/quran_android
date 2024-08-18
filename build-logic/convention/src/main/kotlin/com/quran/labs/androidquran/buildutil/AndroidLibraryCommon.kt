package com.quran.labs.androidquran.buildutil

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Project

// Disable the debug build type for libraries
fun Project.disableDebugVariant() {
  val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
  androidComponents.beforeVariants {
    if (it.buildType == "debug") {
      it.enable = false
    }
  }
}
