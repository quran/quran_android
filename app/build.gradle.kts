import net.ltgt.gradle.errorprone.ErrorProneOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Locale

plugins {
  id("quran.android.application")
  alias(libs.plugins.kotlin.kapt)
  alias(libs.plugins.kotlin.parcelize)
  alias(libs.plugins.ksp)
  alias(libs.plugins.errorprone)
  alias(libs.plugins.anvil)
}

// whether or not to use Firebase - Firebase is enabled by default, and is only disabled for
// providing apks for open source distribution stores.
val useFirebase = !project.hasProperty("disableFirebase") &&
    !project.hasProperty("disableCrashlytics")

// only want to apply the Firebase plugin if we're building a release build. moving this to the
// release build type won't work, since debug builds would also implicitly get the plugin.
if (getGradle().startParameter.taskRequests.toString().contains("Release") && useFirebase) {
  apply(plugin = "com.google.gms.google-services")
  apply(plugin = "com.google.firebase.crashlytics")
}

anvil {
  useKsp(
    contributesAndFactoryGeneration = true,
    componentMerging = true
  )
}

android {
  namespace = "com.quran.labs.androidquran"

  defaultConfig {
    versionCode = 3441
    versionName = "3.4.4"
    testInstrumentationRunner = "com.quran.labs.androidquran.core.QuranTestRunner"
  }

  dependenciesInfo {
    // only keep dependency info block for builds with Firebase
    includeInApk = useFirebase
    includeInBundle = useFirebase
  }

  buildFeatures.buildConfig = true

  signingConfigs {
    create("release") {
      storeFile = file((project.property("STORE_FILE") as String))
      storePassword = project.property("STORE_PASSWORD") as String
      keyAlias = project.property("KEY_ALIAS") as String
      keyPassword = project.property("KEY_PASSWORD") as String
    }
  }

  flavorDimensions += listOf("pageType")
  productFlavors {
    create("madani") {
      applicationId = "com.quran.labs.androidquran"
    }
  }

  buildTypes {
    create("beta") {
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard.cfg")
      signingConfig = signingConfigs.getByName("release")
      versionNameSuffix = "-beta"
      matchingFallbacks += listOf("debug")
    }

    getByName("debug") {
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-debug"
    }

    getByName("release") {
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard.cfg")
      signingConfig = signingConfigs.getByName("release")
    }
  }

  applicationVariants.all {
    resValue("string", "authority", "${applicationId}.data.QuranDataProvider")
    resValue("string", "file_authority", "${applicationId}.fileprovider")
    if (applicationId.endsWith("debug")) {
      mergedFlavor.manifestPlaceholders["app_debug_label"] =
        "Quran ${flavorName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }}"
    }
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all {
        it.testLogging {
          events("passed", "skipped", "failed", "standardOut", "standardError")
          showStandardStreams = true
        }
      }
    }
  }

  packaging {
    resources {
      excludes += setOf("META-INF/*.kotlin_module", "META-INF/DEPENDENCIES", "META-INF/INDEX.LIST")
    }
  }
}

tasks.withType<KotlinCompile>().configureEach {
  // TODO necessary until anvil supports something for K2 contribution merging
  compilerOptions {
    progressiveMode.set(false)
    languageVersion.set(KotlinVersion.KOTLIN_1_9)
  }
}

// required so that Errorprone doesn't look at generated files
afterEvaluate {
  tasks.withType<JavaCompile>().configureEach {
    (options as ExtensionAware).extensions.configure<ErrorProneOptions> {
      excludedPaths.set(".*/build/generated/.*")
      disableWarningsInGeneratedCode = true
    }
  }
}

if (File(rootDir, "extras/extras.gradle").exists()) {
  apply(from = File(rootDir, "extras/extras.gradle"))
} else {
  apply(from = "pluggable.gradle")
}

dependencies {
  implementation(project(":common:analytics"))
  implementation(project(":common:audio"))
  implementation(project(":common:bookmark"))
  implementation(project(":common:data"))
  implementation(project(":common:di"))
  implementation(project(":common:download"))
  implementation(project(":common:networking"))
  implementation(project(":common:pages"))
  implementation(project(":common:preference"))
  implementation(project(":common:reading"))
  implementation(project(":common:recitation"))
  implementation(project(":common:search"))
  implementation(project(":common:toolbar"))
  implementation(project(":common:translation"))
  implementation(project(":common:upgrade"))
  implementation(project(":common:ui:core"))

  implementation(project(":feature:audio"))
  implementation(project(":feature:audiobar"))
  implementation(project(":feature:downloadmanager"))
  implementation(project(":feature:qarilist"))

  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)

  implementation(libs.retrofit)
  implementation(libs.converter.moshi)

  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.media)
  implementation(libs.androidx.localbroadcastmanager)
  implementation(libs.androidx.preference.ktx)
  implementation(libs.androidx.recyclerview)
  implementation(libs.material)
  implementation(libs.androidx.swiperefreshlayout)
  implementation(libs.androidx.window)

  // compose
  implementation(libs.compose.ui)

  // okio
  implementation(libs.okio)

  // rx
  implementation(libs.rxjava)
  implementation(libs.rxandroid)

  // dagger
  kapt(libs.dagger.compiler)
  kaptTest(libs.dagger.compiler)
  implementation(libs.dagger.runtime)

  // workmanager
  implementation(libs.androidx.work.runtime.ktx)

  implementation(libs.okhttp)

  implementation(libs.moshi)
  ksp(libs.moshi.codegen)

  implementation(libs.insetter)
  implementation(libs.timber)
  debugImplementation(libs.leakcanary.android)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.mockito.core)
  testImplementation(libs.okhttp.mockserver)
  testImplementation(libs.junit.ktx)
  testImplementation(libs.robolectric)
  testImplementation(libs.espresso.core)
  testImplementation(libs.espresso.intents)
  testImplementation(libs.turbine)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(project(":pages:data:madani"))

  errorprone(libs.errorprone.core)

  // Number Picker
  implementation(libs.number.picker)
}
