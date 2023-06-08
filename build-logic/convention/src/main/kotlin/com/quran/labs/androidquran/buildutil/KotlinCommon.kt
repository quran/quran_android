package com.quran.labs.androidquran.buildutil

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

fun Project.applyKotlinCommon() {
  extensions.configure<KotlinProjectExtension> {
    jvmToolchain(17)
  }
}
