plugins {
  id("quran.android.library.android")
}

android.namespace = "com.quran.labs.androidquran.pages.data.warsh"

dependencies {
  implementation(project(":common:data"))
  api(project(":pages:data:madani"))
}
