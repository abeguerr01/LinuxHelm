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
        // Tamaño base de la ventana (dp de diseño). SIN tamaño fijo, Compose
        // dimensiona la ventana según su contenido: como el contenido de la
        // principal mide ~400x500 dp, la app salía diminuta (ese era el
        // "todo muy pequeño" al lanzar el .jar). Con un tamaño fijo la ventana
        // siempre abre grande y las pantallas usan el espacio con holgura.
        //
        // El tamaño se multiplica por la escala de Configuración: si el usuario
        // amplía la UI al 150%, la ventana crece igual para que el contenido
        // ampliado quepa entera (los dp de `size` se convierten con la densidad
        // del sistema, fuera del override de App.kt).
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
            title = "LinuxHelm",
            state = ventana,
        ) {
            App()
        }
    }
}
