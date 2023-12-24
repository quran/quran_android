package com.quran.labs.androidquran.buildutil

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.the

// via various comments on https://github.com/gradle/gradle/issues/15383
fun Project.withLibraries(block: (LibrariesForLibs) -> Unit) {
  if (name != "gradle-kotlin-dsl-accessors") {
    val libs = the<LibrariesForLibs>()
    block.invoke(libs)
  }
}

fun Project.applyBoms() {
  dependencies {
    withLibraries { libs ->
      add("implementation", platform(libs.okhttp.bom))
      add("implementation", platform(libs.compose.bom))
    }
  }
}
