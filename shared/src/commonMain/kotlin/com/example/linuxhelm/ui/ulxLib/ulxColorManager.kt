package com.example.linuxhelm.ui.ulxLib

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Aplica una paleta completa a [ulxColor] según el tema elegido.
 *
 * Los valores de aquí son la ÚNICA fuente de verdad de los temas: el objeto
 * `ulxColor` solo define los nombres. Añadir un color nuevo implica añadirlo
 * tanto en `ulxColor` como en las dos funciones de esta clase.
 */
object ulxColorManager {

    /** MODO CLARO: fondo gris muy claro, tarjetas blanco-roto, acento azul. */
    fun setLight() {
        ulxColor.oscuro = false

        // Fondos (los tres originales conservan su nombre)
        ulxColor.fondo = Color(0xFFF2F5FA)
        ulxColor.div1 = Color(0xFFE6EBF4)
        ulxColor.div2 = Color(0xFFD8E0ED)

        // Acentos y superficie elevada
        ulxColor.primario = Color(0xFF2F6FED)
        ulxColor.sobrePrimario = Color(0xFFFFFFFF)
        ulxColor.primarioSuave = Color(0xFFDCE7FD)
        ulxColor.superficie = Color(0xFFFFFFFF)

        // Texto y bordes
        ulxColor.texto = Color(0xFF1C2431)
        ulxColor.textoSuave = Color(0xFF5C6779)
        ulxColor.borde = Color(0xFFC9D2E0)

        // Estados
        ulxColor.exito = Color(0xFF2E9E44)
        ulxColor.aviso = Color(0xFFE8A317)
        ulxColor.error = Color(0xFFD64545)
        ulxColor.desconocido = Color(0xFF9BA6B7)
    }

    /** MODO OSCURO: mismos roles, valores adaptados (aún sin pulir). */
    fun setDark() {
        ulxColor.oscuro = true

        // Fondos
        ulxColor.fondo = Color(0xFF141A22)
        ulxColor.div1 = Color(0xFF1E2632)
        ulxColor.div2 = Color(0xFF28323F)

        // Acentos y superficie elevada
        ulxColor.primario = Color(0xFF6C9BFF)
        ulxColor.sobrePrimario = Color(0xFF0B1220)
        ulxColor.primarioSuave = Color(0xFF24344F)
        ulxColor.superficie = Color(0xFF1E2632)

        // Texto y bordes
        ulxColor.texto = Color(0xFFE7ECF3)
        ulxColor.textoSuave = Color(0xFFA3AEBE)
        ulxColor.borde = Color(0xFF37414F)

        // Estados
        ulxColor.exito = Color(0xFF66BB6A)
        ulxColor.aviso = Color(0xFFFFCA28)
        ulxColor.error = Color(0xFFEF5350)
        ulxColor.desconocido = Color(0xFF7A8697)
    }

    /**
     * Aplica el tema guardado en la BD.
     *
     * Se guardan claves estables ("light"/"dark"), NO las etiquetas localizadas
     * ("Light"/"Claro"/…): si se guardara la etiqueta, cambiar de idioma dejaría
     * el valor guardado sin poder interpretar. Cualquier otro valor (o null)
     * deja el tema claro, el valor por defecto.
     */
    fun applySaved(theme: String?) {
        when (theme) {
            "dark" -> setDark()
            else -> setLight()
        }
    }

    /**
     * Traduce la paleta de [ulxColor] a un `ColorScheme` de Material 3.
     *
     * De aquí salen los colores "gratis" de botones, campos de texto, diálogos y
     * menús: sin esto, M3 pone su morado por defecto y la app queda inconsistente.
     *
     * Se llama dentro de la composición (en `App()`), de modo que al cambiar las
     * variables de [ulxColor] el tema se reconstruye automáticamente.
     */
    fun colorScheme(): ColorScheme {
        val c = ulxColor
        return if (c.oscuro) {
            darkColorScheme(
                primary = c.primario,
                onPrimary = c.sobrePrimario,
                primaryContainer = c.primarioSuave,
                onPrimaryContainer = c.texto,
                background = c.fondo,
                onBackground = c.texto,
                surface = c.superficie,
                onSurface = c.texto,
                surfaceVariant = c.div1,
                onSurfaceVariant = c.textoSuave,
                outline = c.borde,
                error = c.error
            )
        } else {
            lightColorScheme(
                primary = c.primario,
                onPrimary = c.sobrePrimario,
                primaryContainer = c.primarioSuave,
                onPrimaryContainer = c.texto,
                background = c.fondo,
                onBackground = c.texto,
                surface = c.superficie,
                onSurface = c.texto,
                surfaceVariant = c.div1,
                onSurfaceVariant = c.textoSuave,
                outline = c.borde,
                error = c.error
            )
        }
    }
}
