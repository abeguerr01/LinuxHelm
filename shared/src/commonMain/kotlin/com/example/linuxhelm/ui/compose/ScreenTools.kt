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
import androidx.compose.material3.Checkbox
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
import com.example.linuxhelm.ui.ulxLib.needsExecuteBit
import com.example.linuxhelm.ui.ulxLib.openScriptDirect
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
 * para el archivo que acaba de elegir con el diálogo nativo.
 *
 * [sinTerminal] marca el lanzamiento directo, sin ventana de terminal, para
 * apps gráficas (GUI): se guarda en la columna `terminal` de la BD.
 */
private data class AddDialogState(
    val path: String,
    val name: String,
    val website: String,
    val sinTerminal: Boolean = false
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

    // Archivos que se lanzan directamente (binarios, .AppImage…) pero sin permiso +x:
    // se avisa igual, porque en la terminal saldría "Permission denied".
    var noExecPaths by remember { mutableStateOf(emptySet<String>()) }

    // Diálogos
    var addDialog by remember { mutableStateOf<AddDialogState?>(null) }
    var noTerminal by remember { mutableStateOf(false) }
    // Lanzamiento directo (sin terminal) que ni siquiera arrancó
    var errorAlEjecutar by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    /**
     * Recarga la lista desde la BD y comprueba en disco qué scripts ya no existen
     * o les falta el permiso de ejecución (todo en IO: lee SQLite y disco).
     */
    fun reloadScripts() {
        scope.launch {
            val (loaded, missing, noExec) = withContext(Dispatchers.IO) {
                val all = ulxDB.getAllScripts()
                // Una sola pasada por disco (antes se comprobaba exists() dos veces
                // por script: una para "missing" y otra para "noExec")
                val missing = mutableSetOf<String>()
                val noExec = mutableSetOf<String>()
                for (s in all) {
                    val f = File(s.path)
                    if (!f.exists()) missing += s.path
                    // Solo aplica a los que se lanzan directamente (no .sh/.jar/.py)
                    else if (needsExecuteBit(s.path) && !f.canExecute()) noExec += s.path
                }
                Triple(all, missing, noExec)
            }
            scripts = loaded
            missingPaths = missing
            noExecPaths = noExec
        }
    }

    // Carga inicial al abrir la pantalla
    LaunchedEffect(Unit) { reloadScripts() }

    /**
     * Lanza el script: en terminal del sistema (por defecto) o directamente.
     *
     * La terminal gestiona la interacción del script (prompts, sudo, stdin).
     * Las apps gráficas se marcan "sin terminal" (metadata.json de las
     * herramientas del sistema, o el checkbox al añadir un script): se lanzan
     * directamente con [openScriptDirect] y la UI avisa si ni siquiera arranca.
     */
    fun runScript(meta: ScriptMeta) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                if (meta.terminal) openScriptInTerminal(meta.path)
                else openScriptDirect(meta.path)
            }
            when {
                ok -> {}
                meta.terminal -> noTerminal = true
                else -> errorAlEjecutar = meta.path
            }
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

        Text(
            text = ulxLang.Herramientas,
            style = MaterialTheme.typography.headlineSmall,
            color = ulxColor.texto
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ===== Botón añadir script =====
        Button(onClick = {
            // pickScriptFile prueba primero los diálogos externos (kdialog/zenity)
            // y, si no hay ninguno, el JFileChooser de Swing. Es bloqueante: el
            // clic corre en el EDT y la espera no impide al diálogo responder.
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
                style = MaterialTheme.typography.bodyMedium,
                color = ulxColor.textoSuave,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ulxColor.div1)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
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
                        isNoExec = meta.path in noExecPaths,
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
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = state.sinTerminal,
                            onCheckedChange = { addDialog = state.copy(sinTerminal = it) }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = ulxLang.SinTerminalOpcion,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = state.name.ifBlank { File(state.path).nameWithoutExtension }
                    val website = state.website.trim().ifBlank { null }
                    addDialog = null
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            ulxDB.upsertScript(
                                name = name, website = website, path = state.path,
                                terminal = !state.sinTerminal
                            )
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

    // ===== Diálogo: un lanzamiento directo (sin terminal) ni siquiera arrancó =====
    errorAlEjecutar?.let { ruta ->
        AlertDialog(
            onDismissRequest = { errorAlEjecutar = null },
            title = { Text(ulxLang.ErrorAlEjecutar) },
            text = {
                Text(
                    text = "${File(ruta).name}\n\n" + ulxLang.ErrorAlEjecutarDetalle
                )
            },
            confirmButton = {
                TextButton(onClick = { errorAlEjecutar = null }) {
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
 * - Si es un archivo de ejecución directa sin permiso +x (`isNoExec`, p. ej. un
 *   binario o .AppImage copiado a mano), aviso y "Ejecutar" deshabilitado: en la
 *   terminal saldría "Permission denied". (.sh/.jar/.py no necesitan +x.)
 * - La papelera solo se muestra si `meta.deletable` es true (scripts del usuario).
 *   Las herramientas incluidas de serie no la tienen: no se pueden borrar.
 * - El botón de borrar (papelera) quita el registro de la BD, pero NO borra
 *   el archivo en disco: el usuario puede volver a añadirlo cuando quiera.
 */
@Composable
private fun ScriptItem(
    meta: ScriptMeta,
    isMissing: Boolean,
    isNoExec: Boolean,
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

            // Avisos de archivo (no encontrado / sin +x), estilo "salud" de ScreenInfo
            val warning = when {
                isMissing -> ulxLang.ArchivoNoEncontrado
                isNoExec -> ulxLang.SinPermisoEjecucion
                else -> null
            }
            if (warning != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = warning,
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

        Button(onClick = onRun, enabled = !isMissing && !isNoExec) {
            Text(ulxLang.Ejecutar)
        }

        // La papelera solo aparece para scripts del usuario: las herramientas
        // incluidas de serie (deletable = false) no se pueden borrar.
        if (meta.deletable) {
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
}
