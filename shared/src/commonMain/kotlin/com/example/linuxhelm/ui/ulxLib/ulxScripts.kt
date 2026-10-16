package com.example.linuxhelm.ui.ulxLib

import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Construye el comando a ejecutar según el tipo de archivo.
 *
 * - `.sh`   → `bash fichero` (así no hace falta que el archivo tenga +x)
 * - `.jar`  → `java -jar fichero`
 * - otro    → se ejecuta directamente (binarios; necesita permisos de ejecución)
 */
fun buildCommand(path: String, args: List<String> = emptyList()): List<String> {
    val base = when {
        path.endsWith(".sh") -> listOf("bash", path)
        path.endsWith(".jar") -> listOf("java", "-jar", path)
        else -> listOf(path)
    }
    return base + args
}

/**
 * Escapa un argumento para incrustarlo en una línea de comandos de bash
 * (entrecomillado simple: no interpreta `$`, espacios, comillas…).
 */
private fun shellQuote(arg: String): String = "'" + arg.replace("'", "'\\''") + "'"

/**
 * Comando bash que lanza el script y deja la terminal abierta al terminar.
 *
 * Sin este "mantener abierto", las terminales que se cierran solas al acabar el
 * comando (p. ej. gnome-terminal con un script breve) harían desaparecer la
 * salida antes de poder leerla. Al final se muestra el código de salida si
 * hubo error y se espera a una tecla.
 *
 * Los mensajes se localizan con ulxLang en el momento de construir el comando.
 */
private fun buildTerminalInnerCommand(path: String, args: List<String>): String {
    val launch = buildCommand(path, args).joinToString(" ") { shellQuote(it) }
    return listOf(
        launch,
        "ec=\$?",
        "echo",
        "if [ \$ec -ne 0 ]; then echo \"[ULX] ${ulxLang.TerminalSalioCon} \$ec\"; fi",
        "read -n1 -s -p \"[ULX] ${ulxLang.TerminalPulsaTecla}\" _"
    ).joinToString("; ")
}

/**
 * Estilos de invocación de las terminales: algunas reciben el comando como una
 * sola cadena tras `-e` (gnome-terminal, xfce4-terminal…), otras reciben el
 * comando y sus argumentos como argv separado (konsole, xterm, kitty…).
 */
private enum class TermStyle { STRING_E, ARGV_E }

/** Terminales candidatas, de más a menos probable en un escritorio Linux. */
private val knownTerminals = listOf(
    // Estándar Debian/Ubuntu: apunta a la terminal por defecto del sistema
    "x-terminal-emulator" to TermStyle.STRING_E,
    "gnome-terminal" to TermStyle.STRING_E,
    "konsole" to TermStyle.ARGV_E,
    "xfce4-terminal" to TermStyle.STRING_E,
    "mate-terminal" to TermStyle.STRING_E,
    "terminator" to TermStyle.STRING_E,
    "lxterminal" to TermStyle.STRING_E,
    "kitty" to TermStyle.ARGV_E,
    "alacritty" to TermStyle.ARGV_E,
    "foot" to TermStyle.ARGV_E,
    "xterm" to TermStyle.ARGV_E,
    "urxvt" to TermStyle.ARGV_E,
)

/** ¿Existe el ejecutable en el PATH? (sin lanzar subprocess, solo mira los directorios) */
private fun isOnPath(exe: String): Boolean {
    val path = System.getenv("PATH") ?: return false
    return path.split(File.pathSeparator).any { File(it, exe).canExecute() }
}

/**
 * Ejecuta el script en una ventana de terminal del sistema.
 *
 * A diferencia de lanzarlo en segundo plano con ProcessBuilder, el usuario ve la
 * salida en tiempo real y puede interactuar: responder prompts, escribir la
 * contraseña de sudo (que la pide la propia terminal), introducir parámetros…
 *
 * El directorio de trabajo se pone en la carpeta del script, para que los
 * scripts que usen rutas relativas funcionen igual que si se lanzaran a mano.
 *
 * Devuelve false si no hay ninguna terminal instalada o si el lanzamiento falla
 * (la UI debe avisar al usuario en ese caso).
 *
 * Llamar desde `Dispatchers.IO`: abre un proceso.
 */
fun openScriptInTerminal(path: String, args: List<String> = emptyList()): Boolean {
    val (exe, style) = knownTerminals.firstOrNull { isOnPath(it.first) }
        ?: return false

    val inner = buildTerminalInnerCommand(path, args)
    val command = when (style) {
        // La terminal recibe una orden para el shell: "bash -c '<comando>'"
        TermStyle.STRING_E -> listOf(exe, "-e", "bash -c " + shellQuote(inner))
        // La terminal recibe el shell y su código como argumentos separados
        TermStyle.ARGV_E -> listOf(exe, "-e", "bash", "-c", inner)
    }

    return try {
        ProcessBuilder(command)
            .directory(File(path).absoluteFile.parentFile)
            .start()
        true
    } catch (e: Exception) {
        false
    }
}

/**
 * Abre un diálogo nativo para elegir UN archivo de script (.sh, .jar o binario).
 *
 * Usa `JFileChooser` (del propio JDK, sin dependencias extra).
 * El filtro predeterminado muestra .sh y .jar, pero el usuario puede cambiar a
 * "Todos los archivos" desde el propio diálogo para añadir un binario sin extensión.
 *
 * Devuelve la ruta absoluta elegida, o null si el usuario canceló.
 *
 * NOTA: es bloqueante, pero NO va en `Dispatchers.IO`: los diálogos Swing se tienen
 * que mostrar en el EDT. Un diálogo modal bombea sus propios eventos mientras está
 * abierto, así que la UI no se congela aunque el hilo espere a la respuesta. El
 * `onClick` de Compose Desktop ya corre en el EDT, así que se llama directamente.
 */
fun pickScriptFile(): String? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Selecciona el script"
        fileSelectionMode = JFileChooser.FILES_ONLY
        fileFilter = FileNameExtensionFilter("Scripts (*.sh, *.jar)", "sh", "jar")
        // Arrancar en la carpeta del usuario, no en el directorio de trabajo del proceso
        currentDirectory = File(System.getProperty("user.home"))
    }

    val result = chooser.showOpenDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else null
}
