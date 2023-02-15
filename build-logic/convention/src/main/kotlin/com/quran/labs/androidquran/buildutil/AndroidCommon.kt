package com.quran.labs.androidquran.buildutil

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions

fun CommonExtension<*, *, *, *>.applyAndroidCommon() {
  compileSdk = 33
  defaultConfig.minSdk = 21

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  (this as ExtensionAware).extensions.configure<KotlinJvmOptions>("kotlinOptions") {
    jvmTarget = JavaVersion.VERSION_1_8.toString()
  }
}
