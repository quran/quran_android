plugins {
  id("quran.android.library.android")
  alias(libs.plugins.metro)
}

android.namespace = "com.quran.labs.androidquran.pages.madani"

dependencies {
  implementation(project(":common:audio"))
  implementation(project(":common:data"))
  implementation(project(":common:pages"))
  implementation(project(":common:upgrade"))
  implementation(project(":common:drawing"))
  // has to be api, otherwise can't add classes to the correct components
  api(project(":feature:linebyline"))

  api(project(":pages:data:madani"))
  api(project(":pages:common:madani"))

  implementation(libs.androidx.annotation)
  implementation(libs.androidx.fragment.ktx)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
