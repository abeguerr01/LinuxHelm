package com.example.linuxhelm

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.linuxhelm.ui.ulxLib.ulxSettings

fun main() {
    // Aplica el idioma y el tema guardados ANTES de componer la primera ventana.
    // Si se hiciera dentro de un composable, la primera vez se vería el idioma
    // y tema por defecto hasta que el efecto terminara (parpadeo).
    ulxSettings.loadAndApply()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "LinuxHelm",
        ) {
            App()
        }
    }
}
