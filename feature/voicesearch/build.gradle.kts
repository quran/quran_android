plugins {
  id("quran.android.library.compose")
  alias(libs.plugins.metro)
}

android.namespace = "com.quran.mobile.feature.voicesearch"

dependencies {
  implementation(project(":common:data"))
  implementation(project(":common:di"))
  implementation(project(":common:search"))
  implementation(project(":common:ui:core"))
  implementation(project(":common:voicesearch"))

  implementation(libs.androidx.annotation)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.preference.ktx)

  // compose
  implementation(libs.compose.animation)
  implementation(libs.compose.foundation)
  implementation(libs.compose.material)
  implementation(libs.compose.material3)
  implementation(libs.compose.ui)
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.ui.tooling)

  // immutable collections
  implementation(libs.kotlinx.collections.immutable)

  // coroutines
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)

  // okhttp for model download
  implementation(libs.okhttp)

  // sherpa-onnx for ASR — download the AAR from:
  // https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.12.25/sherpa-onnx-v1.12.25-android.tar.bz2
  // Then build the AAR following: android/SherpaOnnxAar/README.md
  // Place the resulting sherpa_onnx-release.aar in feature/voicesearch/libs/
  implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

  // timber
  implementation(libs.timber)

  // molecule
  implementation(libs.molecule)

  // testing
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockito.core)
}
