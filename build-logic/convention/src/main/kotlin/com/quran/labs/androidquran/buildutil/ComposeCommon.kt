package com.quran.labs.androidquran.buildutil

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

fun CommonExtension<*, *, *, *, *, *>.applyComposeCommon(project: Project) {
  buildFeatures.compose = true

  project.withLibraries { libs ->
    project.pluginManager.apply(libs.plugins.compose.compiler.get().pluginId)
  }

  project.extensions.configure<ComposeCompilerGradlePluginExtension> {
    // manually enable source info until this is fixed:
    // https://issuetracker.google.com/issues/362780328
    includeSourceInformation.set(true)

    if (project.findProperty("composeCompilerReports") == "true") {
      reportsDestination.set(project.layout.buildDirectory.get().asFile.resolve("compose_compiler"))
      metricsDestination.set(project.layout.buildDirectory.get().asFile.resolve("compose_compiler"))
    }
  }

  project.tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
      freeCompilerArgs.addAll(
        listOf(
          "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
          "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
          "-opt-in=androidx.compose.material.ExperimentalMaterialApi",
          "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
      )
    }
  }

  project.dependencies {
    // all compose projects need the runtime.
    // we can switch this to implementation instead of api once a fix is pushed for
    // https://issuetracker.google.com/issues/209688774.
    project.withLibraries { libs ->
      add("api", libs.compose.runtime)
    }
  }
}
