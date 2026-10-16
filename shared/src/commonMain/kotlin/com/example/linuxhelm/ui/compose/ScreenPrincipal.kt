package com.example.linuxhelm.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.linuxhelm.ui.ulxLib.ulxColor
import com.example.linuxhelm.ui.ulxLib.ulxLang

@Preview
@Composable
fun ScreenPrincipal(onNavigate: (Pantalla) -> Unit = {}) {
    Column(
        modifier = Modifier
            .background(ulxColor.fondo)
            .safeContentPadding()
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    )
    {
        Text(text = "LINUX HELM", style = MaterialTheme.typography.headlineLargeEmphasized)

        Spacer(modifier = Modifier.height(40.dp))

        Button(onClick = { onNavigate(Pantalla.Info) }) {
            Text(text = ulxLang.Informacion)
        }
        Button(onClick = { onNavigate(Pantalla.Tools) }) {
            Text(text = ulxLang.Herramientas)
        }
        Button(onClick = { onNavigate(Pantalla.Configuracion) }) {
            Text(text = ulxLang.Configuracion)
        }
    }
}