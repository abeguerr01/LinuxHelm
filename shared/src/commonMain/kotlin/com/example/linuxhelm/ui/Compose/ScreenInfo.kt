package com.example.linuxhelm.ui.Compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.linuxhelm.ui.ulxLib.ulxColor
import com.example.linuxhelm.ui.ulxLib.ulxLang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Preview
@Composable
fun ScreenInfo() {
    val wikiUrls = mapOf(
        "apt" to "https://wiki.debian.org/Apt",
        "dnf" to "https://dnf.readthedocs.io/",
        "pacman" to "https://wiki.archlinux.org/title/Pacman",
        "zypper" to "https://en.opensuse.org/SDB:Zypper",
        "emerge" to "https://wiki.gentoo.org/wiki/Package_Management"
    )
    val uriHandler = LocalUriHandler.current
    var disks by remember { mutableStateOf(emptyList<DiskInfo>()) }
    var isExpanded by remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()

    val pm = remember { detectPackageManager() } ?: ulxLang.NoPackageManagerFound

    // Carga los discos fuera del hilo de la UI
    LaunchedEffect(Unit) {
        disks = withContext(Dispatchers.IO) { getDiskInfo() }
    }

    Column(
        modifier = Modifier
            .background(ulxColor.fondo)
            .safeContentPadding()
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Row del gestor de paquetes
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ulxColor.div1)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(ulxLang.PackageManager + ": " + pm)
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = {
                wikiUrls[pm]?.let { uriHandler.openUri(it) }
            }) {
                Text(ulxLang.VerSitio)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Expander: cabecera + contenido dentro del mismo cuadro
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ulxColor.div1)
        ) {
            // Cabecera clicable
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    ulxLang.DetectedStorageDrives,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            }

            // Contenido colapsable con scroll y altura máxima
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(scrollState)
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                ) {
                    disks.forEachIndexed { index, disk ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ulxColor.div2)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(disk.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    disk.mountPoint ?: ulxLang.NoMontado,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                if (disk.usedBytes != null && disk.percentUsed != null) {
                                    Text("${disk.usedStr} / ${disk.totalStr}")
                                    Text("${disk.percentUsed}% ${ulxLang.EnUso}")
                                } else {
                                    Text(disk.totalStr)
                                }
                            }
                        }
                        if (index < disks.lastIndex) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}


// ======================================================
// ===== Funciones para la detección de componentes =====
// ======================================================

/**
 * Función para detectar el gestor de paquetes con ProcessBuilder
 */
fun detectPackageManager(): String? {
    return try {
        val result = ProcessBuilder("sh", "-c", "which apt dnf pacman zypper emerge 2>/dev/null")
            .redirectErrorStream(true)
            .start()
            .inputStream.bufferedReader().readLines()
            .firstOrNull { it.isNotBlank() }

        result?.substringAfterLast('/')
    } catch (e: Exception) {
        null
    }
}

/**
 * Definición de una unidad de almacenamiento (como la ve Dolphin).
 *
 * Si la unidad no está montada, mountPoint, usedBytes y percentUsed son null.
 */
data class DiskInfo(
    val name: String,
    val device: String,
    val mountPoint: String?,
    val totalBytes: Long,
    val usedBytes: Long?,
    val percentUsed: Int?
) {
    val usedStr: String get() = usedBytes?.let { formatBytes(it) } ?: ""
    val totalStr: String get() = formatBytes(totalBytes)
}

/**
 * Función para el formateo de bytes
 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.size - 1) {
        value /= 1024
        unit++
    }
    return "%.1f %s".format(value, units[unit])
}

/**
 * Formatea bytes en GiB, como hace Dolphin para el nombre de las unidades sin etiqueta.
 */
private fun formatGiB(bytes: Long): String {
    return "%.1f GiB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}

/**
 * Convierte una línea de la salida de `lsblk -P` en un mapa clave -> valor.
 * Ejemplo de línea: NAME="sdb6" LABEL="" SIZE="148958863360" ...
 */
private fun parseLsblkLine(line: String): Map<String, String> {
    val regex = Regex("(\\w+)=\"((?:[^\"\\\\]|\\\\.)*)\"")
    val hexEscape = Regex("\\\\x([0-9a-fA-F]{2})")
    return regex.findAll(line).associate { match ->
        val key = match.groupValues[1]
        // lsblk escapa caracteres especiales como \x20 (espacio)
        val value = hexEscape.replace(match.groupValues[2]) {
            it.groupValues[1].toInt(16).toChar().toString()
        }
        key to value
    }
}

/**
 * Función para obtener las unidades de almacenamiento del sistema, al estilo de Dolphin.
 *
 * Usa `lsblk` para listar particiones reales (con etiqueta, montadas o no)
 * en lugar de puntos de montaje. Se apoya de la data class <strong>DiskInfo</strong>
 * y la función <strong>formatBytes</strong>
 */
fun getDiskInfo(): List<DiskInfo> {
    return try {
        val process = ProcessBuilder(
            "lsblk", "-b", "-P", "-e", "7",
            "-o", "NAME,LABEL,SIZE,FSTYPE,MOUNTPOINT,TYPE,PKNAME,RM,PARTTYPE"
        )
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()

        val rows = output.lines()
            .filter { it.isNotBlank() }
            .map { parseLsblkLine(it) }

        // Dispositivos que tienen hijos (un disco con particiones no se muestra, solo sus particiones)
        val parents = rows
            .mapNotNull { it["PKNAME"]?.takeIf { p -> p.isNotBlank() } }
            .toSet()

        val espGuid = "c12a7328-f81f-11d2-ba4b-00a0c93ec93b" // partición EFI (Dolphin la oculta)
        val ignoredFs = setOf("swap", "linux_raid_member", "LVM2_member")

        rows
            .filter { row ->
                val name = row["NAME"].orEmpty()
                val type = row["TYPE"].orEmpty()
                val fs = row["FSTYPE"].orEmpty()
                val mount = row["MOUNTPOINT"].orEmpty()

                type in setOf("part", "disk", "crypt", "lvm") &&
                        name !in parents &&
                        fs.isNotBlank() &&
                        fs !in ignoredFs &&
                        mount != "[SWAP]" &&
                        !mount.startsWith("/boot") &&
                        !row["PARTTYPE"].orEmpty().equals(espGuid, ignoreCase = true)
            }
            .map { row ->
                val dev = row["NAME"].orEmpty()
                val label = row["LABEL"].orEmpty()
                val mount = row["MOUNTPOINT"].orEmpty().ifBlank { null }
                val sizeBytes = row["SIZE"]?.toLongOrNull() ?: 0L
                val removable = row["RM"] == "1"

                val kind = if (removable) "Unidad extraíble" else "Unidad interna"
                val displayName = label.ifBlank { "$kind de ${formatGiB(sizeBytes)} ($dev)" }

                // Si está montada, el uso real sale del sistema de archivos
                val file = mount?.let { File(it) }?.takeIf { it.exists() }
                val total = file?.totalSpace?.takeIf { it > 0 } ?: sizeBytes
                val used = file?.let { total - it.usableSpace }
                val percent = used?.let { if (total > 0) ((it * 100) / total).toInt() else 0 }

                DiskInfo(
                    name = displayName,
                    device = "/dev/$dev",
                    mountPoint = mount,
                    totalBytes = total,
                    usedBytes = used,
                    percentUsed = percent
                )
            }
            .sortedBy { it.device }
    } catch (e: Exception) {
        emptyList()
    }
}