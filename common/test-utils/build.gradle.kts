plugins {
  id("quran.android.library.android")
}

android.namespace = "com.quran.labs.test"

dependencies {
  // Coroutines test support
  api(libs.kotlinx.coroutines.core)
  api(libs.kotlinx.coroutines.test)

  // RxJava for RxSchedulerRule
  api(libs.rxjava)
  api(libs.rxandroid)

  // Testing frameworks - exposed as api so consumers get them transitively
  api(libs.junit)
  api(libs.truth)
  api(libs.mockk)
  api(libs.turbine)

  // Android test support
  api(libs.robolectric)
}
