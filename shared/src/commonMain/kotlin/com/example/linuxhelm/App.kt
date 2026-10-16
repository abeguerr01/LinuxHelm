package com.example.linuxhelm

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.example.linuxhelm.ui.compose.Pantalla
import com.example.linuxhelm.ui.compose.ScreenConfiguracion
import com.example.linuxhelm.ui.compose.ScreenInfo
import com.example.linuxhelm.ui.compose.ScreenPrincipal
import com.example.linuxhelm.ui.compose.ScreenTools

@Composable
@Preview
fun App() {
    // Estado de navegación: la pantalla que se está mostrando ahora mismo.
    // Vive aquí (en la raíz) para que todas las pantallas puedan usarlo.
    var pantalla by remember { mutableStateOf(Pantalla.Principal) }

    MaterialTheme {
        when (pantalla) {
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
