package com.quran.labs.androidquran.buildutil

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KaptExtension

fun Project.applyKotlinCommon() {
  extensions.configure<KotlinProjectExtension> {
    jvmToolchain(17)
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
