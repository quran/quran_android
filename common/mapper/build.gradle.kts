plugins {
  id("quran.android.library.android")
  alias(libs.plugins.metro)
}

android.namespace = "com.quran.labs.androidquran.common.mapper"

dependencies {
  implementation(project(":common:data"))
  implementation(project(":pages:data:madani"))

  // testing
  testImplementation(project(":pages:data:warsh"))
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
