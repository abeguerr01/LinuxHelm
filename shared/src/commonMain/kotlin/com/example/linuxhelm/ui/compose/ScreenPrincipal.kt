package com.example.linuxhelm.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.linuxhelm.ui.ulxLib.ulxColor
import com.example.linuxhelm.ui.ulxLib.ulxLang
import linuxhelm.shared.generated.resources.Res
import linuxhelm.shared.generated.resources.logo
import org.jetbrains.compose.resources.painterResource

@Preview
@Composable
fun ScreenPrincipal(onNavigate: (Pantalla) -> Unit = {}) {
    Column(
        modifier = Modifier
            .background(ulxColor.fondo)
            .safeContentPadding()
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        // Contenido centrado en la ventana, no pegado arriba
        verticalArrangement = Arrangement.Center,
    ) {
        // Logo de la aplicación (recurso: composeResources/drawable/logo.*)
        // Encima de la barrita de acento, centrado con ella.
        Image(
            painter = painterResource(Res.drawable.logo),
            contentDescription = "LINUX HELM",
            modifier = Modifier
                .size(180.dp)
                .clip(RoundedCornerShape(16.dp))
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Barrita de acento sobre el título
        Spacer(
            modifier = Modifier
                .width(56.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(ulxColor.primario)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "LINUX HELM",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 6.sp,
            color = ulxColor.primario,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { onNavigate(Pantalla.Info) },
            modifier = Modifier
                .width(280.dp)
                .height(46.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = ulxLang.Informacion, style = MaterialTheme.typography.titleSmall)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Los dos secundarios en estilo menos "pesado" que el principal
        Button(
            onClick = { onNavigate(Pantalla.Tools) },
            modifier = Modifier
                .width(280.dp)
                .height(46.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = ulxLang.Herramientas, style = MaterialTheme.typography.titleSmall)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { onNavigate(Pantalla.Configuracion) },
            modifier = Modifier
                .width(280.dp)
                .height(46.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = ulxLang.Configuracion,
                style = MaterialTheme.typography.titleSmall,
                color = ulxColor.texto
            )
        }
    }
}
