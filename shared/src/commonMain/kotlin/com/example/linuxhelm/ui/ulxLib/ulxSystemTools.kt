package com.example.linuxhelm.ui.ulxLib

import java.io.File
import java.net.URI
import java.util.jar.JarFile

/**
 * Herramienta "ULX Tools" descubierta al escanear el árbol `ULX Tools/`.
 *
 * El sistema es equivalente al de las herramientas "bundled" clásicas
 * (`seedBundledTools`), pero **genérico**: en vez de una lista hardcodeada se
 * escanea recursivamente una carpeta. Cada subcarpeta que contiene un `.sh`
 * se convierte en una herramienta registrada en la BD como NO borrable
 * (`deletable = 0`), igual que las incluidas de serie: la UI no muestra papelera.
 *
 * [scriptPath] es la ruta absoluta del ejecutable principal.
 * [name] / [description] / [website] salen del `metadata.json` (o `info.json`)
 * de la carpeta; si no existe el JSON, [name] es el nombre de la carpeta.
 */
data class DiscoveredTool(
    val scriptPath: String,
    val name: String,
    val description: String?,
    val website: String?
)

/** Metadatos leídos de `metadata.json` / `info.json` (todos opcionales). */
private data class ToolMetadata(
    val name: String?,
    val description: String?,
    val website: String?
)

/**
 * Extensiones que la app sabe lanzar (con o sin intérprete). Deben coincidir
 * con las que soporta `ulxScripts.kt` (`buildCommand` / `needsExecuteBit` / el
 * diálogo de añadir script): .sh, .jar, .py, .run, .bin y .AppImage.
 */
private val EXECUTABLE_EXTENSIONS = setOf("sh", "jar", "py", "run", "bin", "appimage")

/** Extensión en minúsculas de un nombre ("" si el archivo no tiene extensión). */
private fun execExtension(name: String): String =
    if ('.' in name) name.substringAfterLast('.').lowercase() else ""

/** ¿Tiene una extensión que la app lanza directamente o con su intérprete? */
private fun hasExecutableExtension(name: String): Boolean =
    execExtension(name) in EXECUTABLE_EXTENSIONS

/**
 * ¿Parece un fichero binario? Heurística clásica: si entre los primeros bytes
 * hay un NUL, no es texto. Sirve para distinguir un binario sin extensión
 * (ELF/PE…) de un `LICENSE`/`AUTHORS` de texto, que también va sin extensión.
 */
private fun isProbablyBinary(f: File): Boolean = try {
    f.inputStream().use { ins ->
        val buf = ByteArray(512)
        val n = ins.read(buf)
        (0 until n).any { buf[it] == 0.toByte() }
    }
} catch (e: Exception) {
    false
}

/**
 * ¿Es este archivo un candidato a ejecutable principal de la carpeta?
 *
 * - Extensión conocida (.sh/.jar/.py/.run/.bin/.AppImage) → sí, siempre.
 * - Sin extensión (binario) → sí si además es ejecutable (+x) o su contenido
 *   es binario. Así no se confunde con ficheros de texto sin extensión.
 * - Cualquier otra extensión (.md, .json, .txt…) → no.
 */
private fun isCandidateExecutable(f: File): Boolean {
    if (!f.isFile) return false
    if (hasExecutableExtension(f.name)) return true
    if (execExtension(f.name).isNotEmpty()) return false // extensión desconocida
    return f.canExecute() || isProbablyBinary(f)
}

/** Marca +x, tras extraer, a lo que debe ser ejecutable (extensión conocida o binario). */
private fun shouldBeExecutable(f: File): Boolean =
    hasExecutableExtension(f.name) || isProbablyBinary(f)

/**
 * Escanea recursivamente [root] buscando carpetas de herramienta.
 *
 * Regla: una carpeta (a cualquier profundidad) que contiene al menos un
 * ejecutable — `.sh`, `.jar`, `.py`, `.run`, `.bin`, `.AppImage` o un binario
 * sin extensión — es una herramienta. Vale tanto con subcarpetas directas
 * (`ULX Tools/ulx-tool/`) como con anidadas (`ULX Tools/grupo/ulx-tool/`).
 *
 * Devuelve las herramientas ordenadas por nombre. Es una función pura sobre
 * disco (sin BD), así que es fácil de testear.
 */
fun discoverTools(root: File): List<DiscoveredTool> {
    if (!root.isDirectory) return emptyList()
    return root.walkTopDown()
        .filter { it.isDirectory && it != root }
        .mapNotNull { dir -> toolFromDir(dir) }
        .sortedBy { it.name.lowercase() }
        .toList()
}

/** Construye la herramienta de una carpeta, o null si no contiene ningún ejecutable. */
private fun toolFromDir(dir: File): DiscoveredTool? {
    val main = findMainExecutable(dir) ?: return null
    val meta = readMetadata(dir)
    return DiscoveredTool(
        scriptPath = main.absolutePath,
        name = meta?.name?.takeIf { it.isNotBlank() } ?: dir.name,
        description = meta?.description,
        website = meta?.website
    )
}

/**
 * Elige el ejecutable principal de una carpeta.
 *
 * Prioridad:
 * 1. El fichero cuyo nombre (sin extensión) coincide con el de la carpeta: la
 *    señal más fuerte de intención, y vale para cualquier tipo (p. ej. carpeta
 *    `fzf/` con el binario `fzf` sin extensión).
 * 2. Si no coincide ninguno, uno con extensión conocida y, en su defecto, uno
 *    sin extensión; todo determinista (extensión conocida primero, luego
 *    orden alfabético).
 */
private fun findMainExecutable(dir: File): File? {
    val candidates = dir.listFiles { f -> isCandidateExecutable(f) } ?: return null
    if (candidates.isEmpty()) return null

    // 1) Coincidencia con el nombre de la carpeta
    candidates.firstOrNull {
        it.nameWithoutExtension.equals(dir.name, ignoreCase = true)
    }?.let { return it }

    // 2) Orden determinista: extensión conocida primero, luego alfabético
    return candidates.sortedWith(
        compareBy<File> { if (hasExecutableExtension(it.name)) 0 else 1 }
            .thenBy { it.name.lowercase() }
    ).first()
}

/** Lee `metadata.json` (o `info.json` como alternativa) de la carpeta de una herramienta. */
private fun readMetadata(dir: File): ToolMetadata? {
    val f = File(dir, "metadata.json").takeIf { it.isFile }
        ?: File(dir, "info.json").takeIf { it.isFile }
        ?: return null
    val text = try {
        f.readText()
    } catch (e: Exception) {
        return null
    }
    return ToolMetadata(
        name = jsonStringField(text, "name"),
        description = jsonStringField(text, "description"),
        website = jsonStringField(text, "website")
    )
}

/**
 * Extrae el valor de un campo string ("llave": "valor") de un objeto JSON plano.
 *
 * Es un parser mínimo, SIN dependencias externas (coherente con la filosofía del
 * proyecto, que usa JDBC puro). Está pensado para el `metadata.json` controlado
 * de cada herramienta (campos de nivel superior, todos string): maneja comillas
 * escapadas, `\\`, saltos y `\uXXXX`. No interpreta anidamiento.
 *
 * Devuelve null si el campo no está o no es un string válido.
 */
fun jsonStringField(json: String, key: String): String? {
    val needle = "\"$key\""
    val keyIdx = json.indexOf(needle)
    if (keyIdx < 0) return null
    var i = json.indexOf(':', keyIdx + needle.length)
    if (i < 0) return null
    i++ // salta ':'
    while (i < json.length && json[i].isWhitespace()) i++
    if (i >= json.length || json[i] != '"') return null
    i++ // comilla de apertura

    val sb = StringBuilder()
    while (i < json.length) {
        val c = json[i]
        when {
            c == '\\' && i + 1 < json.length -> {
                when (val n = json[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'u' -> {
                        val hex = json.substring(i + 2, minOf(i + 6, json.length))
                        val cp = hex.toIntOrNull(16)?.toChar()
                        sb.append(cp ?: 'u')
                        i += 4 // additional a los 2 del final: \uXXXX = 6 en total
                    }
                    else -> sb.append(n)
                }
                i += 2
            }
            c == '"' -> return sb.toString()
            else -> {
                sb.append(c)
                i++
            }
        }
    }
    return sb.toString()
}

/**
 * Extrae recursivamente los recursos bajo [resourcePrefix] (empaquetados en el
 * build) dentro de [targetDir], creando directorios y marcando `+x` los `.sh`.
 *
 * El recurso puede venir de dos sitios según se ejecute la app, y se detecta por
 * el protocolo de la URL:
 * - `file:`  → recursos en disco (durante `./gradlew run` o los tests): se copia
 *              el árbol de forma directa.
 * - `jar:`/`zip:` → recursos dentro del JAR empaquetado (`.deb`, AppImage…): se
 *              abren las entradas del JAR con el prefijo dado y se extraen una a una.
 *
 * Devuelve false si no encontró el recurso (p. ej. carpeta `ULX Tools/` vacía).
 */
fun extractResourceTree(resourcePrefix: String, targetDir: File): Boolean {
    val cl = DiscoveredTool::class.java.classLoader
    val dirUrl = cl.getResource(resourcePrefix)
        ?: cl.getResource("$resourcePrefix/")
        ?: return false
    return when (dirUrl.protocol) {
        "file" -> extractFromFileUrl(dirUrl, resourcePrefix, targetDir)
        "jar", "zip" -> extractFromJarUrl(dirUrl, resourcePrefix, targetDir)
        else -> false
    }
}

/** Copia el árbol de recursos cuando están en disco (protocolo `file:`). */
private fun extractFromFileUrl(dirUrl: java.net.URL, prefix: String, targetDir: File): Boolean {
    // La URL apunta a la carpeta del prefijo dentro de build/resources/main
    val srcDir = try {
        File(dirUrl.toURI())
    } catch (e: Exception) {
        return false
    }
    if (!srcDir.isDirectory) return false
    srcDir.walkTopDown().filter { it.isFile }.forEach { src ->
        val rel = src.relativeTo(srcDir).invariantSeparatorsPath
        val dst = File(targetDir, rel)
        dst.parentFile?.mkdirs()
        src.copyTo(dst, overwrite = true)
        if (shouldBeExecutable(dst)) dst.setExecutable(true, false)
    }
    return true
}

/** Extrae el árbol de recursos desde dentro de un JAR (protocolo `jar:`/`zip:`). */
private fun extractFromJarUrl(dirUrl: java.net.URL, prefix: String, targetDir: File): Boolean {
    // dirUrl.toString() -> jar:file:/ruta/app.jar!/ULXTools
    val inner = dirUrl.toString().removePrefix("jar:").substringBefore("!/") // file:/ruta/app.jar
    val jar = try {
        File(URI(inner))
    } catch (e: Exception) {
        return false
    }
    if (!jar.isFile) return false

    JarFile(jar).use { jf ->
        val entries = jf.entries()
        while (entries.hasMoreElements()) {
            val e = entries.nextElement()
            if (e.isDirectory) continue
            val name = e.name
            if (!name.startsWith("$prefix/")) continue
            val rel = name.removePrefix("$prefix/")
            if (rel.isEmpty()) continue
            val dst = File(targetDir, rel)
            dst.parentFile?.mkdirs()
            jf.getInputStream(e).use { ins ->
                dst.outputStream().use { out -> ins.copyTo(out) }
            }
            if (shouldBeExecutable(dst)) dst.setExecutable(true, false)
        }
    }
    return true
}
