package com.example.linuxhelm.ui.ulxLib

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Paleta de colores de la aplicación (nombre histórico: ulxColor).
 *
 * TODOS los colores viven aquí: las pantallas nunca crean un `Color(...)` a mano,
 * leen una variable de este objeto. Cambiar de tema = volver a asignar las
 * variables desde `ulxColorManager`.
 *
 * Cada variable está respaldada por `mutableStateOf`, así que leerla dentro de
 * una composición la hace observable: al cambiar de tema la UI se repinta sola,
 * sin reiniciar la ventana.
 *
 * Nombres semánticos:
 * - [fondo]: fondo general de las pantallas.
 * - [div1]: tarjetas y paneles.
 * - [div2]: elementos anidados dentro de una tarjeta.
 * - [primario] / [sobrePrimario]: color de botones, enlaces y acentos.
 * - [primarioSuave]: acentos suaves (fondos de cabeceras, selección…).
 * - [superficie]: superficie elevada (diálogos, menús).
 * - [texto] / [textoSuave]: texto principal y secundario.
 * - [borde]: contornos sutiles que dan definición a las tarjetas.
 * - [exito] / [aviso] / [error] / [desconocido]: estados (salud de discos, avisos…).
 */
object ulxColor {

    /** true si la paleta activa es la oscura. Lo decide `ulxColorManager`. */
    var oscuro by mutableStateOf(false)

    // ===== Colores existentes (nombres y semántica originales) =====
    var fondo by mutableStateOf(Color(0xFFF2F5FA))
    var div1 by mutableStateOf(Color(0xFFE6EBF4))
    var div2 by mutableStateOf(Color(0xFFD8E0ED))

    // ===== Colores nuevos =====
    var primario by mutableStateOf(Color(0xFF2F6FED))
    var sobrePrimario by mutableStateOf(Color(0xFFFFFFFF))
    var primarioSuave by mutableStateOf(Color(0xFFDCE7FD))
    var superficie by mutableStateOf(Color(0xFFFFFFFF))
    var texto by mutableStateOf(Color(0xFF1C2431))
    var textoSuave by mutableStateOf(Color(0xFF5C6779))
    var borde by mutableStateOf(Color(0xFFC9D2E0))
    var exito by mutableStateOf(Color(0xFF2E9E44))
    var aviso by mutableStateOf(Color(0xFFE8A317))
    var error by mutableStateOf(Color(0xFFD64545))
    var desconocido by mutableStateOf(Color(0xFF9BA6B7))
}
