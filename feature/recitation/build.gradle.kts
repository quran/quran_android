plugins {
  id("quran.android.library.android")
  alias(libs.plugins.metro)
}

android.namespace = "com.quran.mobile.recitation"

dependencies {
  implementation(project(":common:data"))
  implementation(project(":common:recitation"))
  implementation(libs.androidx.annotation)

  // coroutines
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)
}
