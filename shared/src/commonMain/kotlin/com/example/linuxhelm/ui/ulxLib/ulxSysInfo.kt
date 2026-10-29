package com.example.linuxhelm.ui.ulxLib

import java.io.File
import kotlin.math.roundToInt

/**
 * Datos del equipo para la tarjeta "Equipo" de la pantalla Información.
 *
 * Todo sale de ficheros de texto del propio sistema (/proc, /etc, /sys): sin
 * lanzar procesos y sin dependencias. Los parsers son funciones puras sobre
 * texto, así que los cubren los tests (UlxSysInfoTest) con muestras reales.
 */
data class EquipoInfo(
    val cpuModelo: String = "",
    val cpuNucleos: Int = 0,
    val cargaMedia: String = "",
    val ramTotalMb: Long = 0,
    val ramUsadaMb: Long = 0,
    val uptimeSegundos: Long = -1,
    val kernel: String = "",
    val distro: String = "",
    val escritorio: String = "",
    val temperaturas: List<Temperatura> = emptyList()
)

/** Una lectura de temperatura ya legible: etiqueta corta + grados Celsius. */
data class Temperatura(val etiqueta: String, val grados: Int)

/** Modelo de CPU y número de núcleos lógicos (de /proc/cpuinfo). */
data class CpuInfo(val modelo: String, val nucleos: Int)

/** Memoria de /proc/meminfo, en kB (las unidades que usa el propio kernel). */
data class MemInfo(val totalKb: Long, val disponibleKb: Long)

/**
 * Parsea /proc/cpuinfo: cuenta las líneas "processor" (núcleos lógicos) y toma
 * el primer modelo. En x86 la clave es "model name"; en ARM, "Model" o
 * "Hardware" — se aceptan las tres.
 */
fun parseCpuinfo(texto: String): CpuInfo {
    var modelo = ""
    var nucleos = 0
    for (linea in texto.lineSequence()) {
        val separador = linea.indexOf(':')
        if (separador < 0) continue
        val clave = linea.substring(0, separador).trim()
        val valor = linea.substring(separador + 1).trim()
        when {
            clave == "processor" -> nucleos++
            modelo.isEmpty() &&
                (clave == "model name" || clave == "Model" || clave == "Hardware") -> modelo = valor
        }
    }
    return CpuInfo(modelo.ifEmpty { "—" }, nucleos)
}

/**
 * Parsea /proc/meminfo: total y disponible en kB ("MemAvailable" existe desde
 * el kernel 3.14 y es la referencia para "cuánta memoria hay de verdad libre").
 */
fun parseMeminfo(texto: String): MemInfo {
    var total = 0L
    var disponible = 0L
    for (linea in texto.lineSequence()) {
        val separador = linea.indexOf(':')
        if (separador < 0) continue
        val clave = linea.substring(0, separador).trim()
        val valor = linea.substring(separador + 1).trim()
            .split(Regex("\\s+")).firstOrNull()?.toLongOrNull()
        when (clave) {
            "MemTotal" -> total = valor ?: 0L
            "MemAvailable" -> disponible = valor ?: 0L
        }
    }
    return MemInfo(total, disponible)
}

/** Parsea /proc/uptime: el primer campo son los segundos desde el arranque. */
fun parseUptime(texto: String): Long =
    texto.trim().split(Regex("\\s+")).firstOrNull()?.toDoubleOrNull()?.toLong() ?: -1L

/** Parsea /proc/loadavg: las tres cifras de carga (1, 5 y 15 minutos). */
fun parseLoadavg(texto: String): String =
    texto.trim().split(Regex("\\s+")).take(3).joinToString("  ")

/** Parsea /etc/os-release (formato KEY="valor" de freedesktop.org). */
fun parseOsRelease(texto: String): Map<String, String> {
    val resultado = mutableMapOf<String, String>()
    for (linea in texto.lineSequence()) {
        val l = linea.trim()
        if (l.isEmpty() || l.startsWith("#") || !l.contains('=')) continue
        val clave = l.substringBefore('=').trim()
        val valor = l.substringAfter('=').trim().removeSurrounding("\"").removeSurrounding("'")
        if (clave.isNotEmpty()) resultado[clave] = valor
    }
    return resultado
}

/**
 * Convierte pares (tipo, miligrados) de /sys/class/thermal en lecturas
 * legibles. Descarta valores fuera de rango plausible (los sensores ACPI
 * muertos suelen reportar 0 o cifras absurdas) y se queda con el máximo por
 * sensor, porque la CPU suele tener varias zonas (package, core, peci…).
 */
fun parseTemperaturas(zonas: List<Pair<String, String>>): List<Temperatura> {
    val porEtiqueta = linkedMapOf<String, Temperatura>()
    for ((tipo, miligrados) in zonas) {
        val bruto = miligrados.trim().toLongOrNull() ?: continue
        if (bruto !in 1_000..150_000) continue
        val grados = (bruto / 1000.0).roundToInt()
        val etiqueta = etiquetaTemperatura(tipo)
        val previa = porEtiqueta[etiqueta]
        if (previa == null || grados > previa.grados) {
            porEtiqueta[etiqueta] = Temperatura(etiqueta, grados)
        }
    }
    return porEtiqueta.values.toList()
}

/** Nombres cortos universales para los sensores ("CPU", "NVMe"… no se traducen). */
fun etiquetaTemperatura(tipo: String): String = when {
    listOf("cpu", "x86_pkg", "coretemp", "k10temp", "pkg")
        .any { tipo.contains(it, ignoreCase = true) } -> "CPU"
    listOf("nvme", "composite", "sensor")
        .any { tipo.contains(it, ignoreCase = true) } -> "NVMe"
    tipo.contains("gpu", ignoreCase = true) -> "GPU"
    else -> tipo
}

/**
 * Uptime en formato compacto y neutro entre idiomas: "2d 14:03:55"
 * (días solo si los hay; el reloj siempre HH:MM:SS).
 */
fun formatUptime(segundos: Long): String {
    if (segundos < 0) return ""
    val dias = segundos / 86_400
    val resto = segundos % 86_400
    val reloj = listOf(resto / 3_600, (resto % 3_600) / 60, resto % 60)
        .joinToString(":") { it.toString().padStart(2, '0') }
    return if (dias > 0) "${dias}d $reloj" else reloj
}

/**
 * Convierte MB a "X.Y GB" (o "N MB" por debajo de 1 GB) sin depender del
 * locale: se redondea a un decimal con aritmética entera.
 */
fun gbDesdeMb(mb: Long): String {
    if (mb < 1024) return "$mb MB"
    val decimas = ((mb * 10.0) / 1024.0).roundToInt()
    return "${decimas / 10.0} GB"
}

/** RAM en "usada / total (pct %)", p. ej. "7.6 GB / 15.5 GB (49 %)". */
fun formatRam(totalMb: Long, usadaMb: Long): String {
    if (totalMb <= 0) return ""
    val pct = (usadaMb * 100.0 / totalMb).roundToInt().coerceIn(0, 100)
    return "${gbDesdeMb(usadaMb)} / ${gbDesdeMb(totalMb)} ($pct %)"
}

/**
 * Lee el equipo real. Bloqueante (lee ficheros) → llamar desde Dispatchers.IO.
 * Cualquier fichero ausente (no Linux, /proc sin montar…) deja ese campo vacío
 * en vez de romper la tarjeta entera.
 */
fun leerEquipo(): EquipoInfo {
    val cpu = try {
        parseCpuinfo(File("/proc/cpuinfo").readText())
    } catch (_: Exception) { CpuInfo("", 0) }
    val mem = try {
        parseMeminfo(File("/proc/meminfo").readText())
    } catch (_: Exception) { MemInfo(0, 0) }
    val uptime = try {
        parseUptime(File("/proc/uptime").readText())
    } catch (_: Exception) { -1L }
    val carga = try {
        parseLoadavg(File("/proc/loadavg").readText())
    } catch (_: Exception) { "" }
    val osRelease = try {
        parseOsRelease(File("/etc/os-release").readText())
    } catch (_: Exception) { emptyMap() }
    val kernel = System.getProperty("os.version") ?: ""
    val escritorio = (System.getenv("XDG_CURRENT_DESKTOP") ?: "").ifBlank {
        System.getenv("DESKTOP_SESSION") ?: ""
    }
    return EquipoInfo(
        cpuModelo = cpu.modelo,
        cpuNucleos = cpu.nucleos,
        cargaMedia = carga,
        ramTotalMb = mem.totalKb / 1024,
        ramUsadaMb = (mem.totalKb - mem.disponibleKb).coerceAtLeast(0) / 1024,
        uptimeSegundos = uptime,
        kernel = kernel,
        distro = osRelease["PRETTY_NAME"] ?: osRelease["NAME"] ?: "",
        escritorio = escritorio,
        temperaturas = leerTemperaturas()
    )
}

/** Zonas térmicas de /sys/class/thermal (si no hay sensor, lista vacía). */
private fun leerTemperaturas(): List<Temperatura> {
    val zonas = try {
        File("/sys/class/thermal").listFiles()
            ?.filter { it.name.startsWith("thermal_zone") }
            ?.mapNotNull { zona ->
                val tipo = try {
                    File(zona, "type").readText().trim()
                } catch (_: Exception) { return@mapNotNull null }
                val temp = try {
                    File(zona, "temp").readText().trim()
                } catch (_: Exception) { return@mapNotNull null }
                tipo to temp
            } ?: emptyList()
    } catch (_: Exception) { emptyList() }
    return parseTemperaturas(zonas)
}
