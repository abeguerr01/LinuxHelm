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
 * Abre un diálogo para elegir UN archivo ejecutable. Devuelve la ruta absoluta
 * elegida, o null si el usuario canceló.
 *
 * Estrategia en tres capas, de más a menos nativa:
 *
 * 1. **kdialog** (KDE): el diálogo Qt nativo del escritorio — el mismo "Abrir"
 *    que usa Dolphin, con aspecto idéntico al de Windows.
 * 2. **zenity** (GNOME, XFCE y otros): el diálogo GTK nativo.
 * 3. **`JFileChooser`** del propio JDK si el equipo no trae ninguno de los dos
 *    (escritorios mínimos), con aspecto **GTK** y **tamaño relativo** a la
 *    pantalla (~70%): el tamaño de fábrica (~640x480 fijo) se veía enorme
 *    junto a la ventana de la app.
 *
 * kdialog y zenity son procesos aparte: el resultado vuelve por su **stdout**
 * (la ruta elegida) y su **código de salida** (0 = aceptado, 1 = cancelado).
 * Verificado en la máquina real: sin D-Bus ni señales que puedan perderse.
 *
 * (Antes se usaba el XDG Desktop Portal vía `gdbus` + `dbus-monitor`: quedó
 * descartado tras comprobar en el bus real que la señal `Request.Response` se
 * entrega en *unicast* al proceso `gdbus` llamador —que muere al devolver la
 * ruta del objeto—, de modo que ningún oyente externo la recibe jamás y la app
 * se quedaba esperando eternamente. Con CLI puro, esa vía es insalvable.)
 *
 * Un **cancelar nunca abre el siguiente diálogo**: si el usuario cerró el
 * nativo, devolvemos null y ya.
 *
 * NOTA: es bloqueante, pero NO va en `Dispatchers.IO`: los diálogos Swing se tienen
 * que mostrar en el EDT. Un diálogo modal bombea sus propios eventos mientras está
 * abierto, así que la UI no se congela aunque el hilo espere a la respuesta. El
 * `onClick` de Compose Desktop ya corre en el EDT, así que se llama directamente.
 */
fun pickScriptFile(): String? {
    // 1) kdialog: diálogo Qt nativo de KDE (donde vive el usuario de la app).
    val kdialog = pickScriptFileExterno(
        "kdialog", "--title", "Selecciona el script",
        "--getopenfilename", System.getProperty("user.home")
    )
    if (kdialog !is DialogoNoInstalado) return (kdialog as? DialogoConRuta)?.ruta

    // 2) zenity: diálogo GTK nativo de GNOME y compañía.
    val zenity = pickScriptFileExterno(
        "zenity", "--file-selection",
        "--title=Selecciona el script", "--filename=~/"
    )
    if (zenity !is DialogoNoInstalado) return (zenity as? DialogoConRuta)?.ruta

    // 3) Respaldo: JFileChooser del JDK (aspecto GTK + tamaño relativo).
    return pickScriptFileSwing()
}

/** Qué pudo pasar con un diálogo externo (kdialog/zenity). */
private sealed interface ResultadoDialogoExterno

/** El usuario eligió un fichero. */
private data class DialogoConRuta(val ruta: String) : ResultadoDialogoExterno

/** El usuario canceló o cerró el diálogo: NO se debe abrir ningún respaldo. */
private object DialogoCancelado : ResultadoDialogoExterno

/** El programa no está instalado en este equipo: probar la siguiente capa. */
private object DialogoNoInstalado : ResultadoDialogoExterno

/**
 * Lanza un diálogo de fichero nativo con un programa externo (`comando` es
 * `kdialog` o `zenity`, con sus argumentos) y traduce su resultado:
 *
 * - **aceptar** → exit 0 y la ruta por stdout → [DialogoConRuta];
 * - **cancelar** → exit 1 y stdout vacío → [DialogoCancelado];
 * - **no instalado** → `ProcessBuilder` lanza IOException → [DialogoNoInstalado].
 *
 * Verificado en la máquina real: `kdialog --getopenfilename` cancelado da
 * exit 1 + stdout vacío, y `zenity --file-selection` aceptado devuelve la ruta
 * por stdout con exit 0. Un fallo raro (crash del diálogo) cae en el mismo caso
 * que cancelar: nunca cuelga la app ni abre dos diálogos.
 */
private fun pickScriptFileExterno(vararg comando: String): ResultadoDialogoExterno {
    val proceso = try {
        ProcessBuilder(*comando)
            // Los mensajes de cancelar van a la consola de la app; no interesan.
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    } catch (e: Exception) {
        return DialogoNoInstalado
    }
    val ruta = try {
        // Hasta EOF: el diálogo externo escribe la ruta y termina (o cancela
        // y no escribe nada). readText() no puede quedarse colgado porque el
        // proceso cierra su stdout al salir.
        proceso.inputStream.bufferedReader().readText().trim()
    } catch (e: Exception) {
        ""
    }
    val codigo = proceso.waitFor()
    return if (codigo == 0 && ruta.isNotEmpty()) DialogoConRuta(ruta) else DialogoCancelado
}

/**
 * Respaldo: `JFileChooser` del JDK, con aspecto GTK en Linux y tamaño
 * proporcional a la pantalla. Ver la capa 2 de [pickScriptFile].
 */
private fun pickScriptFileSwing(): String? {
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
