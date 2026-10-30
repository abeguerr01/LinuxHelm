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
import androidx.compose.material3.Button
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
import com.example.linuxhelm.ui.ulxComponentes.MenuSelector
import com.example.linuxhelm.ui.ulxLib.ResultadoActualizacion
import com.example.linuxhelm.ui.ulxLib.UlxAbout
import com.example.linuxhelm.ui.ulxLib.ulxColor
import com.example.linuxhelm.ui.ulxLib.ulxLang
import com.example.linuxhelm.ui.ulxLib.ulxSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    // "Acerca de": versión embebida en el build + comprobación de actualizaciones
    val versionApp = remember { UlxAbout.versionDeLaApp() }
    var comprobandoActualizaciones by remember { mutableStateOf(false) }
    var resultadoActualizacion by remember { mutableStateOf<ResultadoActualizacion?>(null) }

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
                MenuSelector(
                    etiqueta = ulxLang.Tema,
                    opciones = listOf(ulxLang.Claro, ulxLang.Oscuro),
                    actual = selectedThemeLabel,
                    onSeleccionar = { label ->
                        // Etiqueta del selector -> clave estable para la BD
                        val key = if (label == ulxLang.Oscuro) "dark" else "light"
                        themeKey = key
                        scope.launch { ulxSettings.saveTheme(key) }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Los literales de idioma NUNCA se traducen: son la clave de guardado
                MenuSelector(
                    etiqueta = ulxLang.Idioma,
                    opciones = listOf("English", "Español", "中文 (zhōngwén)", "Português"),
                    actual = selectedLang,
                    onSeleccionar = { lang ->
                        selectedLang = lang
                        scope.launch { ulxSettings.saveLang(lang) }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Escala de interfaz: se guarda como factor (1.5) y la UI se
                // reescala en caliente (observable en ulxSettings.escala).
                // Los porcentajes son universales (no se traducen), así que
                // funcionan de clave y de etiqueta a la vez: "150%" -> 1.5f.
                MenuSelector(
                    etiqueta = ulxLang.Escala,
                    opciones = listOf("100%", "125%", "150%", "175%", "200%"),
                    actual = escalaLabel,
                    onSeleccionar = { label ->
                        escalaLabel = label
                        // "basura" -> 1.0f por defecto
                        val factor = (label.removeSuffix("%").toFloatOrNull()
                            ?: 100f) / 100f
                        scope.launch { ulxSettings.saveEscala(factor) }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tarjeta "Acerca de": versión de la app y comprobación manual de
            // actualizaciones contra los releases de GitHub (ver UlxAbout en
            // ulxLib/ulxAbout.kt). El botón es manual a propósito: cada pulsación
            // es 1 llamada a la API anónima, que tiene rate limit.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ulxColor.div1)
                    .padding(16.dp)
            ) {
                Text(
                    ulxLang.AcercaDe,
                    style = MaterialTheme.typography.titleSmall,
                    color = ulxColor.texto
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "LinuxHelm — ${ulxLang.Version} $versionApp",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ulxColor.texto
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    enabled = !comprobandoActualizaciones,
                    onClick = {
                        comprobandoActualizaciones = true
                        resultadoActualizacion = null
                        scope.launch {
                            val resultado = withContext(Dispatchers.IO) {
                                UlxAbout.consultarUltimaVersion()
                            }
                            resultadoActualizacion = resultado
                            comprobandoActualizaciones = false
                        }
                    }
                ) {
                    Text(
                        if (comprobandoActualizaciones) ulxLang.Comprobando
                        else ulxLang.ComprobarActualizaciones
                    )
                }

                resultadoActualizacion?.let { r ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when {
                            r.error != null -> ulxLang.ErrorAlComprobar
                            r.hayActualizacion ->
                                "${ulxLang.NuevaVersionDisponible}: ${r.versionRemota}"
                            else -> ulxLang.UltimaVersion
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            r.error != null -> ulxColor.error
                            r.hayActualizacion -> ulxColor.aviso
                            else -> ulxColor.exito
                        }
                    )
                }
            }
        }
    }
}
