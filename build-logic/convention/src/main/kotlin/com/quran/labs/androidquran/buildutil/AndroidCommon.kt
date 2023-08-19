package com.quran.labs.androidquran.buildutil

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions

fun CommonExtension<*, *, *, *, *>.applyAndroidCommon(project: Project) {
  compileSdk = 34
  defaultConfig.minSdk = 21

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  (this as ExtensionAware).extensions.configure<KotlinJvmOptions>("kotlinOptions") {
    jvmTarget = JavaVersion.VERSION_17.toString()
  }

  lint {
    checkReleaseBuilds = true
    enable.add("Interoperability")
    lintConfig = project.rootProject.file("lint.xml")
  }
}
