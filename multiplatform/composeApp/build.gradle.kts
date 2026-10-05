plugins { kotlin("multiplatform"); id("com.android.library"); id("org.jetbrains.compose"); id("org.jetbrains.kotlin.plugin.compose"); id("org.jetbrains.kotlin.plugin.serialization") }
kotlin {
 androidTarget { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }; jvm { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }; iosArm64(); iosSimulatorArm64()
listOf(iosArm64(),iosSimulatorArm64()).forEach { it.binaries.framework { baseName="QuranShared"; isStatic=true } }
 sourceSets {
 commonMain.dependencies { implementation(project(":core:model"))
implementation(project(":core:domain"))
implementation(project(":core:data"))
implementation(project(":core:designsystem"))
implementation(project(":feature:downloads"))
implementation(project(":feature:reader"))
implementation(project(":feature:translations"))
implementation(project(":feature:memorization"))
implementation(project(":feature:qibla"))
implementation(project(":feature:tutor"))
implementation("org.jetbrains.androidx.navigation3:navigation3-ui:1.1.1")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
implementation("io.insert-koin:koin-core:4.0.0")
implementation(compose.runtime)
implementation(compose.foundation)
implementation(compose.material3)
implementation(compose.ui) }
commonMain.dependencies { implementation(compose.components.resources) }
 commonTest.dependencies { implementation(kotlin("test")); implementation(libs.kotlinx.coroutines.test); implementation("io.insert-koin:koin-test:4.0.0") }
 }
}
android { namespace = "org.quran.app.composeApp"; compileSdk = 36; compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }; defaultConfig { minSdk = 26 } }
