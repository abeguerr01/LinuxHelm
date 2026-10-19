package com.example.linuxhelm.ui.ulxComponentes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
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

/**
 * Desplegable genérico de una sola línea para ajustes (tema, idioma, escala…).
 *
 * Antes había tres selectores casi idénticos (ThemeSelector, LanguageSelector y
 * EscalaSelector) copiados a mano; con esta única implementación un cambio o
 * arreglo (p. ej. la API del ancla del menú) se hace en un solo sitio.
 *
 * El campo es de solo lectura: el valor se elige siempre entre [opciones].
 *
 * @param etiqueta etiqueta flotante del campo (ulxLang.Tema, ulxLang.Idioma…).
 * @param opciones valores visibles en la lista.
 * @param actual   valor mostrado ahora mismo.
 * @param onSeleccionar avisa a la pantalla con la opción elegida.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSelector(
    etiqueta: String,
    opciones: List<String>,
    actual: String,
    onSeleccionar: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            readOnly = true,
            value = actual,
            onValueChange = {},
            label = { Text(etiqueta) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            // PrimaryNotEditable: ancla del menú en un campo no editable
            // (la firma sin argumentos está obsoleta desde material3 1.1).
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            opciones.forEach { opcion ->
                DropdownMenuItem(
                    text = { Text(opcion) },
                    onClick = {
                        onSeleccionar(opcion)
                        expanded = false
                    }
                )
            }
        }
    }
}
