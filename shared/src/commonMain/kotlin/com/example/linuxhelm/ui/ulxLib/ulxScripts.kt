package com.example.linuxhelm.ui.ulxLib

import java.awt.Dimension
import java.awt.Toolkit
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Construye el comando a ejecutar según el tipo de archivo.
 *
 * - `.sh`   → `bash fichero` (así no hace falta que el archivo tenga +x)
 * - `.jar`  → `java -jar fichero`
 * - `.py`   → `python3 fichero` (tampoco necesita +x)
 * - otro    → se ejecuta directamente: binarios ELF, `.AppImage`, `.run`,
 *             `.bin`, o scripts con shebang que sí tengan +x.
 *
 * La comprobación de extensión ignore mayúsculas, así `.AppImage` y `.appimage`
 * se tratan igual.
 */
fun buildCommand(path: String, args: List<String> = emptyList()): List<String> {
    val lower = path.lowercase()
    val base = when {
        lower.endsWith(".sh") -> listOf("bash", path)
        lower.endsWith(".jar") -> listOf("java", "-jar", path)
        lower.endsWith(".py") -> listOf("python3", path)
        else -> listOf(path)
    }
    return base + args
}

/**
 * ¿Este archivo necesita permiso de ejecución (+x) para lanzarse?
 *
 * `.sh`, `.jar` y `.py` no (bash/java/python3 los interpretan ellos), pero el
 * resto se lanzan directamente y sin +x la terminal devolvería "Permission denied".
 *
 * La UI usa esto para avisar y deshabilitar "Ejecutar" antes de que falle.
 */
fun needsExecuteBit(path: String): Boolean {
    val lower = path.lowercase()
    return !(lower.endsWith(".sh") || lower.endsWith(".jar") || lower.endsWith(".py"))
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
 * Abre un diálogo para elegir UN archivo ejecutable.
 *
 * Usa `JFileChooser` (del propio JDK, sin dependencias extra), pero con dos
 * retoques importantes:
 *
 * - **Aspecto GTK**: en Linux se aplica el L&F GTK del propio JDK mientras
 *   dura el diálogo, así que el chooser pinta como el resto de apps de GNOME
 *   en vez del aspecto Metal genérico. Si GTK no está disponible (u otro SO),
 *   se ignora en silencio y se usa el L&F actual. El L&F anterior se restaura
 *   al cerrar.
 * - **Tamaño relativo a la pantalla** (~70%), con suelos y techos razonables:
 *   el tamaño de fábrica (~640x480 fijo) se veía ENORME comparado con la
 *   ventana de la app en pantallas grandes, y diminuto en pantallas pequeñas.
 *
 * El filtro predeterminado muestra los tipos reconocidos (.sh, .jar, .py,
 * .AppImage, .run, .bin), pero el usuario puede cambiar a "Todos los archivos"
 * desde el propio diálogo para elegir un binario sin extensión.
 *
 * Devuelve la ruta absoluta elegida, o null si el usuario canceló.
 *
 * NOTA: es bloqueante, pero NO va en `Dispatchers.IO`: los diálogos Swing se tienen
 * que mostrar en el EDT. Un diálogo modal bombea sus propios eventos mientras está
 * abierto, así que la UI no se congela aunque el hilo espere a la respuesta. El
 * `onClick` de Compose Desktop ya corre en el EDT, así que se llama directamente.
 */
fun pickScriptFile(): String? {
    // L&F GTK solo mientras dure el diálogo; se restaura el anterior después.
    val lafAnterior = UIManager.getLookAndFeel()
    try {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel")
    } catch (e: Exception) {
        // Sin GTK (u otro SO): nos quedamos con el L&F actual.
    }

    val chooser = JFileChooser().apply {
        dialogTitle = "Selecciona el script"
        fileSelectionMode = JFileChooser.FILES_ONLY
        fileFilter = FileNameExtensionFilter(
            "Scripts y ejecutables (*.sh, *.jar, *.py, *.AppImage, *.run, *.bin)",
            "sh", "jar", "py", "AppImage", "run", "bin"
        )
        // Arrancar en la carpeta del usuario, no en el directorio de trabajo del proceso
        currentDirectory = File(System.getProperty("user.home"))
        // Tamaño proporcional a la pantalla (con suelos/techos): ni el fijo de
        // fábrica ni un gigante en pantallas grandes.
        val pantalla = Toolkit.getDefaultToolkit().screenSize
        preferredSize = Dimension(
            (pantalla.width * 0.7).toInt().coerceIn(640, 1100),
            (pantalla.height * 0.72).toInt().coerceIn(480, 760)
        )
    }

    val result = try {
        chooser.showOpenDialog(null)
    } finally {
        try {
            UIManager.setLookAndFeel(lafAnterior)
        } catch (e: Exception) {
            // Restaurar el L&F es cosmético: nunca debe romper la selección.
        }
    }
    return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else null
}
