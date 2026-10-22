plugins {
  id("quran.android.library.android")
}

android {
  namespace = "com.quran.mobile.feature.memorize.engine.sherpa"
  defaultConfig {
    consumerProguardFiles("consumer-rules.pro")
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  // library modules only build release; the on-device benchmark runs against it
  testBuildType = "release"
}

// the speech model for on-device tests, put there by tools/memorize/run_device_benchmark.sh (not in
// git); without it the tests download it
androidComponents {
  onVariants { variant ->
    (variant as? com.android.build.api.variant.HasDeviceTests)?.deviceTests?.values?.forEach {
      it.sources.assets?.addStaticSourceDirectory(rootProject.file("build/memorize-testlab-model").path)
    }
  }
}

dependencies {
  api(project(":feature:memorize:tracker"))
  // the only module with native code; see tools/memorize/fetch_sherpa_onnx.sh
  implementation(libs.sherpa.onnx)

  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.truth)
}
