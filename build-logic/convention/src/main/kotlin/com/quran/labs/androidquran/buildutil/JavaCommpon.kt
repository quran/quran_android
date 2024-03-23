package com.quran.labs.androidquran.buildutil

import org.gradle.api.JavaVersion
import org.gradle.api.Project

fun Project.applyJavaCommon() {
  tasks.withType(org.gradle.api.tasks.compile.JavaCompile::class.java).configureEach {
    sourceCompatibility = JavaVersion.VERSION_17.toString()
    targetCompatibility = JavaVersion.VERSION_17.toString()
  }
}
