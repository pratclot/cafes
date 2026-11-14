plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.compose.compiler)
}

kotlin {
  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
  wasmJs {
    browser {
      commonWebpackConfig { outputFileName = "webApp.js" }
    }
    binaries.executable()
  }

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:passthrough"))
      implementation(project(":core:model"))
      implementation(compose.runtime)
      implementation(compose.foundation)
      implementation(compose.animation)
      implementation(compose.ui)
      implementation(compose.material3)
      implementation(compose.components.resources)
    }

    wasmJsMain.dependencies {
      implementation(libs.kotlinx.browser)
    }
  }
}
