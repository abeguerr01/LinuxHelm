package com.example.linuxhelm

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.linuxhelm.ui.ulxLib.ulxSettings

fun main() {
    // Aplica el idioma y el tema guardados ANTES de componer la primera ventana.
    // Si se hiciera dentro de un composable, la primera vez se vería el idioma
    // y tema por defecto hasta que el efecto terminara (parpadeo).
    ulxSettings.loadAndApply()

    application {
        // Tamaño fijo de la ventana (dp de diseño): sin él, Compose la dimensiona
        // por contenido y la principal (~400x500dp) salía diminuta. Se multiplica
        // por la escala de Configuración para que el contenido ampliado quepa
        // (los dp de `size` se convierten con la densidad del sistema, fuera del
        // override de App.kt).
        val escala = ulxSettings.escala
        val ventana = rememberWindowState(
            size = DpSize(1150.dp * escala, 760.dp * escala)
        )
        // Al cambiar la escala en caliente (Configuración), la ventana se
        // redimensiona al mismo factor.
        LaunchedEffect(escala) {
            ventana.size = DpSize(1150.dp * escala, 760.dp * escala)
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = "Linux Helm",
            state = ventana,
        ) {
            App()
        }
    }
}
