package com.example.linuxhelm.ui.ulxLib

import androidx.compose.ui.graphics.Color

object ulxColorManager {
    fun setLight(){
        ulxColor.fondo = Color.White
    }
    fun setDark(){
        ulxColor.fondo = Color.Green
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
}
