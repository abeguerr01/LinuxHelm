package com.example.linuxhelm.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.linuxhelm.ui.ulxLib.ScriptMeta
import com.example.linuxhelm.ui.ulxLib.openScriptInTerminal
import com.example.linuxhelm.ui.ulxLib.pickScriptFile
import com.example.linuxhelm.ui.ulxLib.ulxDB
import com.example.linuxhelm.ui.ulxLib.ulxColor
import com.example.linuxhelm.ui.ulxLib.ulxLang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Estado del diálogo "Añadir script": nombre + web que el usuario rellena
 * para el archivo que acaba de elegir con el JFileChooser.
 */
private data class AddDialogState(
    val path: String,
    val name: String,
    val website: String
)

@Preview
@Composable
fun ScreenTools(onVolver: () -> Unit = {}) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    // Scripts registrados en la BD (la fuente de verdad: sobreviven a reinicios)
    var scripts by remember { mutableStateOf(emptyList<ScriptMeta>()) }

    // Se comprueba al abrir la pantalla: si el script no está, aviso y sin "Ejecutar".
    var missingPaths by remember { mutableStateOf(emptySet<String>()) }

    // Diálogos
    var addDialog by remember { mutableStateOf<AddDialogState?>(null) }
    var noTerminal by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    /**
     * Recarga la lista desde la BD y comprueba en disco qué scripts ya no existen
     * (se ejecuta todo en IO porque lee SQLite y el sistema de archivos).
     */
    fun reloadScripts() {
        scope.launch {
            val (loaded, missing) = withContext(Dispatchers.IO) {
                val all = ulxDB.getAllScripts()
                all to all.map { it.path }.filter { !File(it).exists() }.toSet()
            }
            scripts = loaded
            missingPaths = missing
        }
    }

    // Carga inicial al abrir la pantalla
    LaunchedEffect(Unit) { reloadScripts() }

    /**
     * Lanza el script en una ventana de terminal del sistema.
     *
     * La terminal gestiona la interacción del script (prompts, sudo, stdin):
     * aquí solo se abre el proceso y se avisa si no hay terminal instalada.
     */
    fun runScript(meta: ScriptMeta) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { openScriptInTerminal(meta.path) }
            if (!ok) noTerminal = true
        }
    }

    Column(
        modifier = Modifier
            .background(ulxColor.fondo)
            .safeContentPadding()
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Volver a la principal (ver BotonVolver en Screen.kt)
        Row(modifier = Modifier.fillMaxWidth()) {
            BotonVolver(onVolver)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ===== Botón añadir script =====
        Button(onClick = {
            // pickScriptFile es un diálogo Swing modal: bloquea el EDT, pero
            // bombea sus eventos, así que la UI no se congela.
            val path = pickScriptFile() ?: return@Button
            addDialog = AddDialogState(
                path = path,
                // Rellena el nombre con el del archivo como ayuda inicial
                name = File(path).nameWithoutExtension,
                website = ""
            )
        }) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(ulxLang.AniadirScript)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ===== Lista de scripts =====
        when {
            // Sin scripts registrados todavía
            scripts.isEmpty() -> Text(
                text = ulxLang.SinScripts,
                style = MaterialTheme.typography.bodyMedium
            )

            // Lista con scroll y altura máxima, como el expander de ScreenInfo
            else -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(scrollState)
            ) {
                scripts.forEachIndexed { index, meta ->
                    ScriptItem(
                        meta = meta,
                        isMissing = meta.path in missingPaths,
                        onRun = { runScript(meta) },
                        onRemove = {
                            scope.launch {
                                withContext(Dispatchers.IO) { ulxDB.deleteScript(meta.id) }
                                reloadScripts()
                            }
                        },
                        onOpenWeb = {
                            val url = if (meta.website!!.startsWith("http"))
                                meta.website else "https://" + meta.website
                            uriHandler.openUri(url)
                        }
                    )
                    if (index < scripts.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    // ===== Diálogo "Añadir script": nombre + web =====
    addDialog?.let { state ->
        AlertDialog(
            onDismissRequest = { addDialog = null },
            title = { Text(ulxLang.AniadirScript) },
            text = {
                Column {
                    Text(
                        text = state.path,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = { addDialog = state.copy(name = it) },
                        label = { Text(ulxLang.Nombre) },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.website,
                        onValueChange = { addDialog = state.copy(website = it) },
                        label = { Text(ulxLang.SitioWeb) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = state.name.ifBlank { File(state.path).nameWithoutExtension }
                    val website = state.website.trim().ifBlank { null }
                    addDialog = null
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            ulxDB.upsertScript(name = name, website = website, path = state.path)
                        }
                        // Recarga la lista desde la BD
                        reloadScripts()
                    }
                }) {
                    Text(ulxLang.Aniadir)
                }
            },
            dismissButton = {
                TextButton(onClick = { addDialog = null }) {
                    Text(ulxLang.Cerrar)
                }
            }
        )
    }

    // ===== Diálogo: no hay terminal instalada =====
    if (noTerminal) {
        AlertDialog(
            onDismissRequest = { noTerminal = false },
            title = { Text(ulxLang.SinTerminal) },
            text = { Text(ulxLang.SinTerminalDetalle) },
            confirmButton = {
                TextButton(onClick = { noTerminal = false }) {
                    Text(ulxLang.Cerrar)
                }
            }
        )
    }
}

/**
 * Fila de un script: nombre + descripción a la izquierda, acciones a la derecha.
 *
 * - "Ver sitio" solo aparece si el script tiene `website` en la BD.
 * - Si el archivo ya no existe en disco (`isMissing`), se muestra un aviso en rojo
 *   y se deshabilita "Ejecutar": no tiene sentido lanzar algo que ya no está.
 * - El botón de borrar (papelera) quita el registro de la BD, pero NO borra
 *   el archivo en disco: el usuario puede volver a añadirlo cuando quiera.
 */
@Composable
private fun ScriptItem(
    meta: ScriptMeta,
    isMissing: Boolean,
    onRun: () -> Unit,
    onRemove: () -> Unit,
    onOpenWeb: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ulxColor.div1)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(meta.name, style = MaterialTheme.typography.titleSmall)
            if (!meta.description.isNullOrBlank()) {
                Text(
                    meta.description,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Aviso de archivo ausente, con el mismo estilo de "salud" de ScreenInfo
            if (isMissing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = ulxLang.ArchivoNoEncontrado,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (!meta.website.isNullOrBlank()) {
            Button(onClick = onOpenWeb) {
                Text(ulxLang.VerSitio)
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Button(onClick = onRun, enabled = !isMissing) {
            Text(ulxLang.Ejecutar)
        }

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = ulxLang.Quitar,
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
