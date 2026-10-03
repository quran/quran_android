plugins { kotlin("multiplatform"); id("com.android.library"); id("org.jetbrains.compose"); id("org.jetbrains.kotlin.plugin.compose") }
kotlin {
 androidTarget { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }; jvm { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }; iosArm64(); iosSimulatorArm64()
 sourceSets {
 commonMain.dependencies { implementation(project(":core:model"))
implementation(compose.runtime)
implementation(compose.foundation)
implementation(compose.material3)
implementation(compose.ui) }
commonMain.dependencies { implementation(compose.components.resources) }
 commonTest.dependencies { implementation(kotlin("test")) }
 }
}
android { namespace = "org.quran.app.core.designsystem"; compileSdk = 36; compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }; defaultConfig { minSdk = 26 } }
