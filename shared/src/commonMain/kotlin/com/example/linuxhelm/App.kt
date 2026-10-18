package com.example.linuxhelm

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.example.linuxhelm.ui.compose.Pantalla
import com.example.linuxhelm.ui.compose.ScreenConfiguracion
import com.example.linuxhelm.ui.compose.ScreenInfo
import com.example.linuxhelm.ui.compose.ScreenPrincipal
import com.example.linuxhelm.ui.compose.ScreenTools
import com.example.linuxhelm.ui.ulxLib.ulxColorManager

@Composable
@Preview
fun App() {
    // Estado de navegación: la pantalla que se está mostrando ahora mismo.
    // Vive aquí (en la raíz) para que todas las pantallas puedan usarlo.
    var pantalla by remember { mutableStateOf(Pantalla.Principal) }

    MaterialTheme(
        // El esquema sale de la paleta de ulxColor: así los botones, campos y
        // diálogos dejan de usar el morado por defecto de Material 3.
        colorScheme = ulxColorManager.colorScheme()
    ) {
        // Surface es lo que en Material 3 define el color de CONTENIDO por
        // defecto (LocalContentColor): los Text sin color explícito cogen el de
        // aquí, no el negro fijo que trae la librería. Al pasarle
        // onBackground (= ulxColor.texto), el texto sale blanco en oscuro y
        // oscuro en claro automáticamente, en TODAS las pantallas.
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            // AnimatedContent transiciona entre pantallas: la nueva entra desde la
            // derecha con fade, la vieja sale hacia la izquierda con fade.
            AnimatedContent(
                targetState = pantalla,
                transitionSpec = {
                    (fadeIn() + slideInHorizontally { it / 4 }) togetherWith
                            (fadeOut() + slideOutHorizontally { -it / 4 })
                }
            ) { target ->
                when (target) {
                    // La principal sabe en qué pantalla terminar cada botón...
                    Pantalla.Principal -> ScreenPrincipal(
                        onNavigate = { destino -> pantalla = destino }
                    )

                    // ...y las secundarias solo necesitan saber cómo volver.
                    Pantalla.Info -> ScreenInfo(
                        onVolver = { pantalla = Pantalla.Principal }
                    )
                    Pantalla.Tools -> ScreenTools(
                        onVolver = { pantalla = Pantalla.Principal }
                    )
                    Pantalla.Configuracion -> ScreenConfiguracion(
                        onVolver = { pantalla = Pantalla.Principal }
                    )
                }
            }
        }
    }
}
