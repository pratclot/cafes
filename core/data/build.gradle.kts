plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
  wasmJs {
    browser()
  }

  android {
    namespace = "com.pratclot.cafes.core.data"
    compileSdk = 37
    minSdk = 24
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":core:model"))
      implementation(project(":core:passthrough"))
    }
  }
}
