package com.quran.labs.androidquran.buildutil

import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

fun Project.applyKotlinCommon() {

  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_17)
      jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
      freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
  }
}
