package com.quran.labs.androidquran.buildutil

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KaptExtension

fun Project.applyKotlinCommon() {

  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_17)
      freeCompilerArgs.add(
        "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
      )
    }
  }

  pluginManager.withPlugin("org.jetbrains.kotlin.kapt") {
    extensions.configure<KaptExtension> {
      arguments {
        arg("dagger.ignoreProvisionKeyWildcards", "enabled")
        arg("dagger.experimentalDaggerErrorMessages", "enabled")
        arg("dagger.warnIfInjectionFactoryNotGeneratedUpstream", "enabled")
        arg("dagger.fastInit", "enabled")
      }
    }
  }
}
