package com.quran.labs.androidquran.buildutil

import app.cash.sqldelight.gradle.SqlDelightExtension
import app.cash.sqldelight.gradle.SqlDelightTask
import com.google.devtools.ksp.gradle.KspAATask
import com.google.devtools.ksp.gradle.KspExtension
import com.google.devtools.ksp.gradle.KspTaskJvm
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named

fun Project.applyDaggerAnvilCommon() {
  kspAfterSqlDelight()
  if (pluginManager.hasPlugin("com.google.devtools.ksp")) {
    extensions.getByType<KspExtension>().apply {
      arg("dagger.ignoreProvisionKeyWildcards", "enabled")
      arg("dagger.experimentalDaggerErrorMessages", "enabled")
      arg("dagger.warnIfInjectionFactoryNotGeneratedUpstream", "enabled")
      arg("dagger.fastInit", "enabled")
    }
  }
}

private fun Project.kspAfterSqlDelight() {
  // make KSP depend on SqlDelight task
  // https://github.com/slackhq/slack-gradle-plugin/blob/a69a630e2971c98eb5db29ea800c87ae167eb9fb/slack-plugin/src/main/kotlin/slack/gradle/SlackExtension.kt#L238
  project.afterEvaluate {
    afterEvaluate {
      if (pluginManager.hasPlugin("app.cash.sqldelight")) {
        val dbNames =
          extensions.getByType<SqlDelightExtension>().databases.names

        val sourceSet = "Release" // "CommonMain" if using KMP some day
        val sourceSetKspName = "Release" // "CommonMainMetadata" if using KMP some day
        for (dbName in dbNames) {
          val sqlDelightTask =
            tasks.named<SqlDelightTask>("generate${sourceSet}${dbName}Interface")
          val outputProvider = sqlDelightTask.flatMap { it.outputDirectory }

          tasks
            .named { it == "ksp${sourceSetKspName}Kotlin" }
            .configureEach {
              when (this) {
                is KspTaskJvm -> {
                  source(outputProvider)
                  dependsOn(sqlDelightTask)
                }

                is KspAATask -> {
                  kspConfig.javaSourceRoots.from(outputProvider)
                  dependsOn(sqlDelightTask)
                }
              }
            }
        }
      }
    }
  }
}
