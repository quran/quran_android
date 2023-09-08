package com.quran.labs.androidquran.buildutil

import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

fun Project.applyBoms() {
  dependencies {
    add("implementation", platform("com.squareup.okhttp3:okhttp-bom:4.10.0"))
    add("implementation", platform("androidx.compose:compose-bom:2023.09.00"))
  }
}
