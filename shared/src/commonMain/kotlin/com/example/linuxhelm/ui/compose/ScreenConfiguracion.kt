package com.example.linuxhelm.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.linuxhelm.ui.ulxComponentes.EscalaSelector
import com.example.linuxhelm.ui.ulxComponentes.LanguageSelector
import com.example.linuxhelm.ui.ulxComponentes.ThemeSelector
import com.example.linuxhelm.ui.ulxLib.ulxColor
import com.example.linuxhelm.ui.ulxLib.ulxLang
import com.example.linuxhelm.ui.ulxLib.ulxSettings
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Preview
@Composable
fun ScreenConfiguracion(onVolver: () -> Unit = {}) {
    val scope = rememberCoroutineScope()

    var selectedLang by remember { mutableStateOf(ulxSettings.lang ?: "English") }
    var themeKey by remember { mutableStateOf(ulxSettings.theme ?: "light") }
    // Etiqueta visible de la escala ("150%"), derivada del factor guardado.
    var escalaLabel by remember {
        mutableStateOf("${(ulxSettings.escala * 100).roundToInt()}%")
    }

    val selectedThemeLabel = if (themeKey == "dark") ulxLang.Oscuro else ulxLang.Claro

    Column(
        modifier = Modifier
            .background(ulxColor.fondo)
            .safeContentPadding()
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Volver a la principal (ver BotonVolver en Screen.kt)
            BotonVolver(onVolver)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = ulxLang.Configuracion,
                style = MaterialTheme.typography.headlineSmall,
                color = ulxColor.texto
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Tarjeta que agrupa los ajustes
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ulxColor.div1)
                    .padding(16.dp)
            ) {
                ThemeSelector(
                    currentTheme = selectedThemeLabel,
                    onThemeChange = { label ->
                        // Etiqueta del selector -> clave estable para la BD
                        val key = if (label == ulxLang.Oscuro) "dark" else "light"
                        themeKey = key
                        scope.launch { ulxSettings.saveTheme(key) }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                LanguageSelector(
                    currentLang = selectedLang,
                    onLangChange = { lang ->
                        // Literal del selector (nunca se traduce) -> clave de guardado
                        selectedLang = lang
                        scope.launch { ulxSettings.saveLang(lang) }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Escala de interfaz: se guarda como factor (1.5) y la UI se
                // reescala en caliente (observable en ulxSettings.escala).
                EscalaSelector(
                    currentEscala = escalaLabel,
                    onEscalaChange = { label ->
                        escalaLabel = label
                        // "150%" -> 1.5f ("basura" -> 1.0f por defecto)
                        val factor = (label.removeSuffix("%").toFloatOrNull()
                            ?: 100f) / 100f
                        scope.launch { ulxSettings.saveEscala(factor) }
                    }
                )
            }
        }
    }
}
