package com.example.linuxhelm.ui.Compose

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
fun ScreenPrincipal(){
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

        Button(onClick = {  }) {
            Text(text = ulxLang.Informacion)
        }
        Button(onClick = {  }) {
            Text(text = ulxLang.Herramientas)
        }
        Button(onClick = {  }) {
            Text(text = ulxLang.Configuracion)
        }
    }
}