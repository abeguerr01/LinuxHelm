package com.example.linuxhelm.ui.ulxComponentes

// ulxComponentes/EscalaSelector.kt

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.linuxhelm.ui.ulxLib.ulxLang

/**
 * Selector de escala de interfaz (100%…200%), con el mismo patrón visual que
 * [ThemeSelector] y [LanguageSelector].
 *
 * Los porcentajes son universales (no se traducen), así que funcionan de clave
 * y de etiqueta a la vez: "150%" -> factor 1.5.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EscalaSelector(
    currentEscala: String,
    onEscalaChange: (String) -> Unit
) {
    val escalas = listOf("100%", "125%", "150%", "175%", "200%")
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            readOnly = true,
            value = currentEscala,
            onValueChange = {},
            label = { Text(ulxLang.Escala) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            escalas.forEach { escala ->
                DropdownMenuItem(
                    text = { Text(escala) },
                    onClick = {
                        onEscalaChange(escala)
                        expanded = false
                    }
                )
            }
        }
    }
}
