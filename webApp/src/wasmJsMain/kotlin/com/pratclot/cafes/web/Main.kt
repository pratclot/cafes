package com.pratclot.cafes.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.compose.runtime.LaunchedEffect
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  ComposeViewport(document.getElementById("ComposeTarget")!!) {
    CafeWebApp()
    LaunchedEffect(Unit) {
      document.getElementById("ComposeTarget")?.setAttribute("aria-busy", "false")
      document.getElementById("LoadingScreen")?.remove()
    }
  }
}
