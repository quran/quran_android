import java.security.MessageDigest

plugins {
  id("quran.android.library.compose")
  alias(libs.plugins.metro)
}

android.namespace = "com.quran.mobile.feature.voicesearch"

// Download sherpa-onnx AAR at build time instead of checking the 38MB binary into git.
// The AAR is hosted on the fork's GitHub releases, built from k2-fsa/sherpa-onnx v1.12.25.
val sherpaOnnxAar = layout.buildDirectory.file("sherpa-onnx/sherpa_onnx-release.aar")
val downloadSherpaOnnx by tasks.registering {
  val aarFile = sherpaOnnxAar.get().asFile
  outputs.file(aarFile)
  onlyIf { !aarFile.exists() }
  doLast {
    aarFile.parentFile.mkdirs()
    val url = "https://github.com/MahmoodMahmood/quran_android/releases/download/v1.0.0-sherpa-onnx-aar/sherpa_onnx-release.aar"
    val expectedSha256 = "66805a13c848d46749c88522ae139d5654de2e34365b67fb5c62e5aa00fd923e"

    ant.invokeMethod("get", mapOf("src" to url, "dest" to aarFile, "skipexisting" to false))

    val digest = MessageDigest.getInstance("SHA-256")
    val actualSha256 = digest.digest(aarFile.readBytes()).joinToString("") { byte -> "%02x".format(byte) }
    if (actualSha256 != expectedSha256) {
      aarFile.delete()
      throw GradleException(
        "SHA-256 mismatch for sherpa_onnx-release.aar\n" +
        "  expected: $expectedSha256\n" +
        "  actual:   $actualSha256"
      )
    }
    logger.lifecycle("Downloaded and verified sherpa_onnx-release.aar (SHA-256 OK)")
  }
}

tasks.named("preBuild") { dependsOn(downloadSherpaOnnx) }

dependencies {
  implementation(project(":common:data"))
  implementation(project(":common:di"))
  implementation(project(":common:search"))
  implementation(project(":common:ui:core"))

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

  // sherpa-onnx for on-device ASR (downloaded automatically by the downloadSherpaOnnx task)
  implementation(files(sherpaOnnxAar))

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
