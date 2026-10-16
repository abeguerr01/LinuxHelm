package com.example.linuxhelm

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.example.linuxhelm.ui.compose.ScreenInfo
import com.example.linuxhelm.ui.compose.ScreenPrincipal

@Composable
@Preview
fun App() {
    MaterialTheme {
        ScreenPrincipal()
    }
}