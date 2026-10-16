package com.example.linuxhelm.ui.compose

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.linuxhelm.ui.ulxLib.ulxLang

/**
 * Todas las pantallas navegables de la aplicación.
 *
 * [Pantalla.Principal] es la raíz: desde ahí se llega al resto y
 * desde cualquier otra se vuelve pulsando el botón de volver.
 *
 * `Test` no está aquí a propósito: es una pantalla de pruebas, no forma parte del flujo.
 */
enum class Pantalla {
    Principal,
    Info,
    Tools,
    Configuracion
}

/**
 * Botón "Volver" reutilizable para las pantallas secundarias.
 *
 * [onVolver] lo inyecta `App()`: ahí vive el estado de navegación, así que este
 * composable no necesita saber a qué pantalla se vuelve, solo avisar de que toca volver.
 *
 * Ejemplo de uso dentro de una pantalla:
 * ```
 * Column(...) {
 *     BotonVolver(onVolver)
 *     Spacer(modifier = Modifier.height(8.dp))
 *     // ... resto del contenido
 * }
 * ```
 */
@Composable
fun BotonVolver(onVolver: () -> Unit) {
    Button(onClick = onVolver) {
        Icon(
            imageVector = Icons.Default.ArrowBack,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = ulxLang.Volver)
    }
}
