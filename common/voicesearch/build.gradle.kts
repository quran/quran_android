plugins {
  id("quran.android.library.android")
}

android.namespace = "com.quran.mobile.common.voicesearch"

dependencies {
  implementation(project(":common:data"))
  implementation(project(":common:search"))

  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.kotlinx.coroutines.test)
}
