plugins {
  id("quran.android.library.android")
  alias(libs.plugins.metro)
}

android.namespace = "com.quran.labs.androidquran.pages.madani"

dependencies {
  implementation(project(":common:data"))
  implementation(project(":common:pages"))
  implementation(project(":common:audio"))
  implementation(project(":common:upgrade"))

  implementation(project(":pages:common:madani"))

  implementation(libs.androidx.annotation)
  api(project(":pages:data:madani"))
}
