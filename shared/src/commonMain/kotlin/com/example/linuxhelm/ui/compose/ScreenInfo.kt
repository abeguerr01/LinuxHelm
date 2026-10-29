package com.example.linuxhelm.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.linuxhelm.ui.ulxLib.EquipoInfo
import com.example.linuxhelm.ui.ulxLib.formatRam
import com.example.linuxhelm.ui.ulxLib.formatUptime
import com.example.linuxhelm.ui.ulxLib.leerEquipo
import com.example.linuxhelm.ui.ulxLib.ulxColor
import com.example.linuxhelm.ui.ulxLib.ulxLang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Preview
@Composable
fun ScreenInfo(onVolver: () -> Unit = {}) {
    val wikiUrls = mapOf(
        "apt" to "https://wiki.debian.org/Apt",
        "dnf" to "https://dnf.readthedocs.io/",
        "pacman" to "https://wiki.archlinux.org/title/Pacman",
        "zypper" to "https://en.opensuse.org/SDB:Zypper",
        "emerge" to "https://wiki.gentoo.org/wiki/Package_Management"
    )
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var disks by remember { mutableStateOf(emptyList<DiskInfo>()) }
    var checking by remember { mutableStateOf(emptySet<String>()) }
    var isExpanded by remember { mutableStateOf(true) }
    var equipo by remember { mutableStateOf<EquipoInfo?>(null) }
    val scrollState = rememberScrollState()
    // Scroll de la columna entera: la tarjeta Equipo añade alto y en ventanas
    // bajas se cortaría sin él (la lista de discos ya tiene su propio scroll)
    val scrollPantalla = rememberScrollState()

    val pm = remember { detectPackageManager() } ?: ulxLang.NoPackageManagerFound

    // Carga los discos fuera del hilo de la UI
    LaunchedEffect(Unit) {
        disks = withContext(Dispatchers.IO) { getDiskInfo() }
    }

    // Igual con los datos del equipo (lecturas de /proc, /etc y /sys, ver
    // leerEquipo() en ulxLib/ulxSysInfo.kt): son ficheros, van a IO
    LaunchedEffect(Unit) {
        equipo = withContext(Dispatchers.IO) { leerEquipo() }
    }

    Column(
        modifier = Modifier
            .background(ulxColor.fondo)
            .safeContentPadding()
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(scrollPantalla),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Volver a la principal (ver BotonVolver en Screen.kt)
        Row(modifier = Modifier.fillMaxWidth()) {
            BotonVolver(onVolver)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = ulxLang.Informacion,
            style = MaterialTheme.typography.headlineSmall,
            color = ulxColor.texto
        )

        Spacer(modifier = Modifier.height(12.dp))

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

        // Tarjeta "Equipo": CPU, carga, RAM, temperaturas, uptime, kernel,
        // sistema y escritorio. Los datos los lee ulxLib.leerEquipo() leyendo
        // ficheros de /proc, /etc y /sys (sin procesos ni dependencias), en el
        // LaunchedEffect de arriba.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ulxColor.div1)
                .padding(12.dp)
        ) {
            Text(
                ulxLang.Equipo,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))

            val eq = equipo
            if (eq == null) {
                Text(ulxLang.Cargando, style = MaterialTheme.typography.bodySmall)
            } else {
                FilaEquipo(
                    ulxLang.Cpu,
                    buildString {
                        if (eq.cpuModelo.isNotBlank()) append(eq.cpuModelo)
                        if (eq.cpuNucleos > 0) {
                            if (isNotEmpty()) append("  ·  ")
                            append("${eq.cpuNucleos} ${ulxLang.Nucleos}")
                        }
                    }
                )
                FilaEquipo(ulxLang.Carga, eq.cargaMedia)
                FilaEquipo(ulxLang.Ram, formatRam(eq.ramTotalMb, eq.ramUsadaMb))
                FilaEquipo(
                    ulxLang.Temperaturas,
                    eq.temperaturas.joinToString("   ") { "${it.etiqueta} ${it.grados} °C" }
                )
                FilaEquipo(ulxLang.TiempoEncendido, formatUptime(eq.uptimeSegundos))
                FilaEquipo(ulxLang.Kernel, eq.kernel)
                FilaEquipo(ulxLang.Sistema, eq.distro)
                FilaEquipo(ulxLang.Escritorio, eq.escritorio)
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
                        val isChecking = disk.device in checking

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
                                    disk.mountPoint ?: "No montado",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                // Indicador de estado
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(disk.health.color())
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        disk.healthDetail,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                TextButton(
                                    enabled = !isChecking,
                                    onClick = {
                                        scope.launch {
                                            checking = checking + disk.device
                                            val (health, detail) = withContext(Dispatchers.IO) {
                                                deepCheck(disk)
                                            }
                                            disks = disks.map {
                                                if (it.device == disk.device)
                                                    it.copy(health = health, healthDetail = detail)
                                                else it
                                            }
                                            checking = checking - disk.device
                                        }
                                    }
                                ) {
                                    Text(if (isChecking) "Comprobando…" else "Comprobar a fondo")
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                if (disk.usedBytes != null && disk.percentUsed != null) {
                                    Text("${disk.usedStr} / ${disk.totalStr}")
                                    Text("${disk.percentUsed}% usado")
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


/**
 * Fila "etiqueta — valor" de la tarjeta Equipo. Los valores vacíos no se
 * pintan (así un sensor o campo inexistente simplemente no aparece).
 */
@Composable
private fun FilaEquipo(etiqueta: String, valor: String) {
    if (valor.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            etiqueta,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(valor, style = MaterialTheme.typography.bodyMedium)
    }
}


// ======================================================
// ===== Funciones para la detección de componentes =====
// ======================================================

/**
 * Detecta el gestor de paquetes instalado, en orden de preferencia.
 *
 * Escanea el PATH en memoria (como `isOnPath` de ulxScripts) en vez de lanzar
 * `sh -c which ...`: mismo resultado, sin crear un proceso. Se llama dentro de
 * `remember {}` al componer, así que debe ser barata.
 *
 * Devuelve el nombre del primer candidato encontrado, o null si no hay ninguno.
 */
fun detectPackageManager(): String? {
    val path = System.getenv("PATH") ?: return null
    val dirs = path.split(File.pathSeparator).filter { it.isNotBlank() }
    return listOf("apt", "dnf", "pacman", "zypper", "emerge")
        .firstOrNull { exe -> dirs.any { dir -> File(dir, exe).canExecute() } }
}

/**
 * Estado de salud de una unidad.
 */
enum class DiskHealth { OK, WARNING, ERROR, UNKNOWN }

private fun DiskHealth.color(): Color = when (this) {
    DiskHealth.OK -> ulxColor.exito
    DiskHealth.WARNING -> ulxColor.aviso
    DiskHealth.ERROR -> ulxColor.error
    DiskHealth.UNKNOWN -> ulxColor.desconocido
}

/**
 * Definición de una unidad de almacenamiento (como la ve Dolphin).
 *
 * Si la unidad no está montada, mountPoint, usedBytes y percentUsed son null.
 */
data class DiskInfo(
    val name: String,
    val device: String,
    val fsType: String,
    val uuid: String?,
    val mountPoint: String?,
    val totalBytes: Long,
    val usedBytes: Long?,
    val percentUsed: Int?,
    val health: DiskHealth = DiskHealth.UNKNOWN,
    val healthDetail: String = "Sin comprobar"
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
 * Lee /proc/self/mounts y devuelve punto de montaje -> opciones de montaje.
 */
private fun readMountOptions(): Map<String, Set<String>> {
    val octal = Regex("\\\\([0-7]{3})")
    return try {
        File("/proc/self/mounts").readLines().mapNotNull { line ->
            val p = line.split(" ")
            if (p.size < 4) return@mapNotNull null
            // /proc/mounts escapa los espacios como \040
            val mountPoint = octal.replace(p[1]) { it.groupValues[1].toInt(8).toChar().toString() }
            mountPoint to p[3].split(",").toSet()
        }.toMap()
    } catch (e: Exception) {
        emptyMap()
    }
}

/**
 * Comprobación rápida y SIN root.
 *
 * - ext4: contador de errores del kernel (/sys/fs/ext4/<dev>/errors_count)
 * - btrfs: contadores de errores de E/S (/sys/fs/btrfs/<uuid>/devinfo/<id>/error_stats)
 * - Cualquier sistema: montada en solo lectura
 *
 * Estos ficheros solo existen en kernels recientes. Si no existen, el resultado es
 * "Sin comprobar" (nunca se marca como correcto sin haber podido verificarlo).
 */
private fun quickCheck(
    dev: String,
    fs: String,
    uuid: String?,
    mountPoint: String?,
    mountOptions: Map<String, Set<String>>
): Pair<DiskHealth, String> {
    if (mountPoint == null) return DiskHealth.UNKNOWN to "No montada, sin comprobar"

    var verified = false
    val errors = mutableListOf<String>()

    when (fs) {
        "ext2", "ext3", "ext4" -> {
            val f = File("/sys/fs/ext4/$dev/errors_count")
            if (f.canRead()) {
                verified = true
                val n = f.readText().trim().toLongOrNull() ?: 0L
                if (n > 0) errors += "$n errores registrados por el kernel"
            }
        }

        "btrfs" -> {
            val devinfo = uuid?.let { File("/sys/fs/btrfs/$it/devinfo").listFiles() }
            devinfo?.forEach { d ->
                val f = File(d, "error_stats")
                if (f.canRead()) {
                    verified = true
                    f.readLines().forEach { line ->
                        val p = line.trim().split(Regex("\\s+"))
                        val n = p.getOrNull(1)?.toLongOrNull() ?: 0L
                        if (n > 0) errors += "${p[0]}: $n"
                    }
                }
            }
        }
    }

    val readOnly = mountOptions[mountPoint]?.contains("ro") == true

    return when {
        errors.isNotEmpty() -> DiskHealth.ERROR to errors.joinToString(", ")
        readOnly -> DiskHealth.WARNING to "Montada en solo lectura"
        verified -> DiskHealth.OK to "Sin errores registrados"
        else -> DiskHealth.UNKNOWN to "Sin comprobar"
    }
}

/**
 * Comprobación a fondo, con root (pkexec muestra el diálogo gráfico de contraseña).
 * Solo lectura: no repara nada.
 *
 * Herramientas necesarias (en Arch): e2fsprogs, ntfs-3g, dosfstools, btrfs-progs, polkit.
 * Es más fiable con la unidad DESMONTADA; si está montada, el resultado es orientativo.
 */
fun deepCheck(disk: DiskInfo): Pair<DiskHealth, String> {
    val cmd: List<String> = when (disk.fsType) {
        "ext2", "ext3", "ext4" -> listOf("dumpe2fs", "-h", disk.device)
        "ntfs", "ntfs3" -> listOf("ntfs-3g.probe", "--readonly", disk.device)
        "vfat" -> listOf("fsck.fat", "-n", disk.device)
        "btrfs" -> {
            val mp = disk.mountPoint
                ?: return DiskHealth.UNKNOWN to "Monta la unidad para comprobar Btrfs"
            listOf("btrfs", "device", "stats", mp)
        }

        else -> return DiskHealth.UNKNOWN to "Comprobación no soportada (${disk.fsType})"
    }

    val (code, out) = try {
        val p = ProcessBuilder(listOf("pkexec") + cmd)
            .redirectErrorStream(true)
            .start()
        val text = p.inputStream.bufferedReader().readText()
        p.waitFor() to text
    } catch (e: Exception) {
        return DiskHealth.UNKNOWN to "No se pudo ejecutar pkexec"
    }

    // 126/127: autorización cancelada o denegada, o comando no encontrado
    if (code == 126 || code == 127) {
        return DiskHealth.UNKNOWN to "Sin permisos o herramienta no instalada"
    }

    val mountedNote = if (disk.mountPoint != null) " (montada, orientativo)" else ""

    return when (disk.fsType) {
        "ext2", "ext3", "ext4" -> {
            val lines = out.lines()
            val state = lines.firstOrNull { it.startsWith("Filesystem state:") }
                ?.substringAfter(":")?.trim()?.lowercase().orEmpty()
            val features = lines.firstOrNull { it.startsWith("Filesystem features:") }.orEmpty()

            when {
                "errors" in state ->
                    DiskHealth.ERROR to "El sistema de archivos tiene errores"
                disk.mountPoint == null && "needs_recovery" in features ->
                    DiskHealth.WARNING to "Cierre incorrecto (journal pendiente de recuperar)"
                disk.mountPoint == null && "not clean" in state ->
                    DiskHealth.WARNING to "No limpio (dirty), se recomienda fsck"
                state.isNotEmpty() ->
                    DiskHealth.OK to "Sin problemas detectados"
                else ->
                    DiskHealth.UNKNOWN to "No se pudo leer el estado"
            }
        }

        "ntfs", "ntfs3" -> when (code) {
            0 -> DiskHealth.OK to "Sin problemas detectados$mountedNote"
            3 -> DiskHealth.ERROR to "Volumen NTFS dañado"
            4 -> DiskHealth.WARNING to "Hibernado por Windows (inicio rápido)"
            5 -> DiskHealth.WARNING to "Cierre incorrecto (dirty)"
            else -> DiskHealth.UNKNOWN to "Resultado no concluyente (código $code)"
        }

        "vfat" -> when {
            "dirty bit is set" in out.lowercase() ->
                DiskHealth.WARNING to "Dirty bit activo: cierre incorrecto"
            code == 0 -> DiskHealth.OK to "Sin problemas detectados$mountedNote"
            else -> DiskHealth.ERROR to "Inconsistencias detectadas$mountedNote"
        }

        "btrfs" -> {
            val bad = out.lines().map { it.trim() }.filter { line ->
                val n = line.split(Regex("\\s+")).lastOrNull()?.toLongOrNull()
                n != null && n > 0
            }
            if (bad.isEmpty()) DiskHealth.OK to "Sin errores de E/S registrados"
            else DiskHealth.ERROR to "${bad.size} contador(es) con errores de E/S"
        }

        else -> DiskHealth.UNKNOWN to "Comprobación no soportada"
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
            "-o", "NAME,LABEL,UUID,SIZE,FSTYPE,MOUNTPOINT,TYPE,PKNAME,RM,PARTTYPE"
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
        val mountOptions = readMountOptions()

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
                val fs = row["FSTYPE"].orEmpty()
                val uuid = row["UUID"].orEmpty().ifBlank { null }
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

                val (health, detail) = quickCheck(dev, fs, uuid, mount, mountOptions)

                DiskInfo(
                    name = displayName,
                    device = "/dev/$dev",
                    fsType = fs,
                    uuid = uuid,
                    mountPoint = mount,
                    totalBytes = total,
                    usedBytes = used,
                    percentUsed = percent,
                    health = health,
                    healthDetail = detail
                )
            }
            .sortedBy { it.device }
    } catch (e: Exception) {
        emptyList()
    }
}