package com.quran.labs.androidquran.buildutil

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project

fun CommonExtension<*, *, *, *, *, *>.applyAndroidCommon(project: Project) {
  compileSdk = 35
  defaultConfig.minSdk = 21

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  lint {
    checkReleaseBuilds = true
    enable.add("Interoperability")
    lintConfig = project.rootProject.file("lint.xml")
  }
}
