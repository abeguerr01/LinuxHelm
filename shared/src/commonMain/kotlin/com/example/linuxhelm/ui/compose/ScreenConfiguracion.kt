package com.example.linuxhelm.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.linuxhelm.ui.ulxComponentes.LanguageSelector
import com.example.linuxhelm.ui.ulxComponentes.ThemeSelector
import com.example.linuxhelm.ui.ulxLib.ulxColor
import com.example.linuxhelm.ui.ulxLib.ulxColorManager
import com.example.linuxhelm.ui.ulxLib.ulxLang
import com.example.linuxhelm.ui.ulxLib.ulxLangManager

@Preview
@Composable
fun ScreenConfiguracion(onVolver: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .background(ulxColor.fondo)
            .safeContentPadding()
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        var selectedLang by remember { mutableStateOf("English") }
        var selectedTheme by remember { mutableStateOf(ulxLang.Claro) }

        Column {
            // Volver a la principal (ver BotonVolver en Screen.kt)
            BotonVolver(onVolver)

            Spacer(modifier = Modifier.height(40.dp))

            Text(text = ulxLang.Configuracion)

            Spacer(modifier = Modifier.height(20.dp))

            ThemeSelector(
                currentTheme = selectedTheme,
                onThemeChange = { theme ->
                    selectedTheme = theme
                    when (theme) {
                        ulxLang.Claro -> ulxColorManager.setLight()
                        ulxLang.Oscuro -> ulxColorManager.setDark()
                    }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            LanguageSelector(
                currentLang = selectedLang,
                onLangChange = { lang ->
                    selectedLang = lang
                    when (lang) {
                        "English" -> ulxLangManager.setEnglish()
                        "Español" -> ulxLangManager.setSpanish()
                        "中文 (zhōngwén)" -> ulxLangManager.setChinese()
                        "Português" -> ulxLangManager.setPortuguese()
                    }
                }
            )
        }
    }
}
