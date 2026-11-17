plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.kotlin.multiplatform.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.compose.compiler)
}

kotlin {
  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
  wasmJs {
    browser()
  }

  android {
    namespace = "com.pratclot.cafes.core.ui"
    compileSdk = 37
    minSdk = 24
    androidResources.enable = true
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":core:model"))
      api(project(":core:data"))
      api(project(":core:designsystem"))
      implementation(project(":core:passthrough"))
      implementation(compose.runtime)
      implementation(compose.animation)
      implementation(compose.foundation)
      implementation(compose.material3)
      implementation(compose.ui)
      api(compose.components.resources)
    }
  }
}

compose.resources {
  publicResClass = true
  packageOfResClass = "com.pratclot.cafes.core.ui.generated.resources"
}
