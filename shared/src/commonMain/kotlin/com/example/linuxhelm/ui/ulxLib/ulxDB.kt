package com.example.linuxhelm.ui.ulxLib

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Metadatos de un script registrado en la base de datos.
 *
 * [path] es la ruta absoluta del archivo tal y como lo eligió el usuario.
 * La app nunca mueve el archivo: solo guarda la referencia, así no hay
 * problemas de permisos ni se rompen rutas relativas del script.
 *
 * [deletable] controla si el usuario puede borrar el registro:
 * - true  → script añadido por el usuario (la papelera aparece)
 * - false → herramienta incluida de serie con la app (no se puede borrar)
 *
 * [terminal] controla cómo se lanza:
 * - true  → en una terminal del sistema (por defecto: prompts, sudo, stdin)
 * - false → directamente, sin terminal (apps GUI: no abre una ventana inútil)
 */
data class ScriptMeta(
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val website: String? = null,
    val path: String,
    val deletable: Boolean = true,
    val terminal: Boolean = true
)

/**
 * Almacén SQLite de la aplicación, en `~/.config/ulx/LinuxHelm/lh.db` (se crea al primer arranque).
 *
 * `~/.config` es la ubicación XDG estándar para configuración de apps en Linux.
 * Los scripts añadidos se guardan aquí, así que sobreviven a reinicios de la app.
 *
 * Se eligió SQLite en lugar de JSON por la escritura atómica (sin riesgo de dejar el
 * fichero corrupto a medias si la app se cierra en mal momento) y por poder hacer
 * consultas directas.
 *
 * Todas las operaciones son bloqueantes (JDBC puro): llámalas desde `Dispatchers.IO`,
 * nunca desde el hilo de la UI.
 */
object ulxDB {

    private val configDir: File by lazy {
        val home = System.getProperty("user.home")
        File(home, ".config/ulx/LinuxHelm")
    }

    private val dbFile: File by lazy {
        File(configDir, "lh.db").apply { parentFile?.mkdirs() }
    }

    /** ÚNICA carpeta destino de las herramientas del sistema: SOLO ejecutables (+x). */
    private val toolsDir: File by lazy {
        File(configDir, "tools")
    }

    /** Carpeta temporal de staging: se extrae el árbol ULXTools y se limpia al terminar. */
    private val stagingDir: File by lazy {
        File(configDir, ".ulx-staging")
    }

    private var connection: Connection? = null

    /** Abre la conexión (si no estaba abierta), crea las tablas y siembra las herramientas incluidas. */
    @Synchronized
    fun connect() {
        if (connection?.isClosed == false) return

        // El driver se registra vía ServiceLoader, pero forzar la carga es más
        // fiable con el proguard/reflection del binario empaquetado.
        Class.forName("org.sqlite.JDBC")
        connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")

        connection!!.createStatement().use { st ->
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS scripts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    description TEXT,
                    website TEXT,
                    path TEXT NOT NULL UNIQUE,
                    deletable INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS settings (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
                """.trimIndent()
            )
            migrateScriptsTable(st)
        }

        // Después de tener las tablas listas, despliega y registra las
        // herramientas del sistema (ulxTools). Es idempotente.
        seedSystemTools()
    }

    /**
     * Migración de BDs creadas antes de existir la columna `deletable` — o
     * antes de `terminal` (cómo se lanza: terminal del sistema o directo).
     *
     * `CREATE TABLE IF NOT EXISTS` no añade columnas a una tabla que ya existe,
     * así que se comprueba con `PRAGMA table_info` y se agrega si falta.
     * Los registros existentes quedan como `deletable = 1` (editables por el
     * usuario) y `terminal = 1` (se lanzan en terminal), que es el
     * comportamiento por defecto de siempre.
     */
    private fun migrateScriptsTable(st: java.sql.Statement) {
        val columns = mutableSetOf<String>()
        st.executeQuery("PRAGMA table_info(scripts)").use { rs ->
            while (rs.next()) {
                columns.add(rs.getString("name").lowercase())
            }
        }
        if ("deletable" !in columns) {
            st.executeUpdate(
                "ALTER TABLE scripts ADD COLUMN deletable INTEGER NOT NULL DEFAULT 1"
            )
        }
        if ("terminal" !in columns) {
            st.executeUpdate(
                "ALTER TABLE scripts ADD COLUMN terminal INTEGER NOT NULL DEFAULT 1"
            )
        }
    }

    // ==================================================
    // ===== Herramientas del sistema (ulxTools) ========
    // ==================================================

    /**
     * Sincroniza las herramientas del sistema empaquetadas en el JAR (fuente:
     * `ulxTools/` en el proyecto, recurso `ULXTools/`).
     *
     * Flujo:
     * 1. Extrae el árbol `ULXTools/` a una carpeta temporal de staging.
     * 2. Escanea el staging: cada subcarpeta con un ejecutable y su `metadata.json`
     *    es una herramienta (ver `discoverTools`).
     * 3. Copia SOLO el ejecutable a `tools/` (plano, +x) y la registra en la BD con
     *    `deletable = 0` (la UI no muestra papelera).
     * 4. Elimina el staging y poda los registros/ficheros obsoletos en `tools/`.
     *
     * Los README NO se despliegan: `tools/` queda SOLO con ejecutables. Las
     * herramientas del usuario (`deletable = 1`, en cualquier ruta) no se tocan.
     */
    @Synchronized
    fun seedSystemTools() {
        // 1) Staging limpio de cero
        try { stagingDir.deleteRecursively() } catch (e: Exception) {}

        // 2) Extraer el árbol empaquetado. Si no hay recurso (app sin herramientas
        //    incluidas), NO hacemos nada ni tocamos lo que ya existiera.
        val extracted = try {
            extractResourceTree("ULXTools", stagingDir)
        } catch (e: Exception) {
            System.err.println("ULX: no se pudo extraer ulxTools: $e")
            false
        }
        if (!extracted) return

        // 3) Descubrir herramientas (ejecutable + metadata.json por subcarpeta)
        val tools = try {
            discoverTools(stagingDir)
        } catch (e: Exception) {
            System.err.println("ULX: no se pudo escanear ulxTools: $e")
            emptyList()
        }

        // 4) Desplegar SOLO el ejecutable a tools/ (plano, +x) y registrar en BD
        toolsDir.mkdirs()
        val current = mutableSetOf<String>()
        for (t in tools) {
            val dest = File(toolsDir, File(t.scriptPath).name)
            try {
                File(t.scriptPath).copyTo(dest, overwrite = true)
                dest.setExecutable(true, false)
                current += dest.absolutePath
                upsertSystemTool(t.copy(scriptPath = dest.absolutePath))
            } catch (e: Exception) {
                System.err.println("ULX: no se pudo desplegar ${t.name}: $e")
            }
        }

        // 5) Quitar staging
        try { stagingDir.deleteRecursively() } catch (e: Exception) {}

        // 6) Poda de obsoletos (registros BD + ficheros) y limpieza del legacy
        pruneSystemTools(current)
        cleanOrphanExecutables(current)
        try { File(configDir, "ULX Tools").deleteRecursively() } catch (e: Exception) {}
    }

    /** Registra (o actualiza) una herramienta del sistema como NO borrable. */
    private fun upsertSystemTool(tool: DiscoveredTool) {
        connection!!.prepareStatement(
            """
            INSERT INTO scripts (name, description, website, path, deletable, terminal)
            VALUES (?, ?, ?, ?, 0, ?)
            ON CONFLICT(path) DO UPDATE SET
                name = excluded.name,
                description = excluded.description,
                website = excluded.website,
                deletable = 0,
                terminal = excluded.terminal
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, tool.name)
            ps.setString(2, tool.description)
            ps.setString(3, tool.website)
            ps.setString(4, tool.scriptPath)
            ps.setBoolean(5, tool.terminal)
            ps.executeUpdate()
        }
    }

    /**
     * Borra los registros (`deletable = 0`) cuya ruta está bajo `tools/` — o bajo
     * la carpeta legacy `ULX Tools/` de una versión anterior — y que ya no
     * corresponden a ninguna herramienta actual. No toca herramientas del usuario.
     */
    private fun pruneSystemTools(current: Set<String>) {
        val prefixes = listOf(
            toolsDir.absolutePath + File.separator,
            File(configDir, "ULX Tools").absolutePath + File.separator // legacy
        )
        val staleIds = mutableListOf<Long>()
        connection!!.createStatement().use { st ->
            st.executeQuery("SELECT id, path FROM scripts WHERE deletable = 0").use { rs ->
                while (rs.next()) {
                    val id = rs.getLong(1)
                    val path = rs.getString(2)
                    if (prefixes.any { path.startsWith(it) } && path !in current) staleIds += id
                }
            }
            for (id in staleIds) st.executeUpdate("DELETE FROM scripts WHERE id = $id")
        }
    }

    /** Elimina de `tools/` los ficheros que ya no son el ejecutable de ninguna herramienta actual. */
    private fun cleanOrphanExecutables(current: Set<String>) {
        toolsDir.listFiles { f -> f.isFile }?.forEach { f ->
            if (f.absolutePath !in current) {
                try { f.delete() } catch (e: Exception) {}
            }
        }
    }

    // ==================================================
    // ===== Scripts (tabla scripts) ====================
    // ==================================================

    /**
     * Añade un script a la lista.
     *
     * `path` tiene UNIQUE: si el archivo ya estaba añadido, en vez de duplicar la
     * entrada se actualizan nombre, web y el modo de lanzamiento. Así
     * "re-añadir" el mismo archivo sirve para editarlo, sin necesidad de
     * pantalla de edición aparte.
     *
     * Por defecto `deletable = true` (script del usuario) y `terminal = true`
     * (se lanza en terminal, como siempre). Las herramientas incluidas de serie
     * se registran con `deletable = false` vía `seedSystemTools()`.
     */
    @Synchronized
    fun upsertScript(name: String, website: String?, path: String, terminal: Boolean = true) {
        connect()
        connection!!.prepareStatement(
            """
            INSERT INTO scripts (name, website, path, deletable, terminal)
            VALUES (?, ?, ?, 1, ?)
            ON CONFLICT(path) DO UPDATE SET
                name = excluded.name,
                website = excluded.website,
                terminal = excluded.terminal
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, name)
            ps.setString(2, website)
            ps.setString(3, path)
            ps.setBoolean(4, terminal)
            ps.executeUpdate()
        }
    }

    /**
     * Borra el registro de un script. NO toca el archivo en disco.
     *
     * La condición `deletable = 1` protege las herramientas incluidas de serie
     * incluso si la UI fallara: no se pueden borrar por esta vía.
     */
    @Synchronized
    fun deleteScript(id: Long) {
        connect()
        connection!!.prepareStatement(
            "DELETE FROM scripts WHERE id = ? AND deletable = 1"
        ).use { ps ->
            ps.setLong(1, id)
            ps.executeUpdate()
        }
    }

    /** Todos los scripts registrados, ordenados por nombre. */
    @Synchronized
    fun getAllScripts(): List<ScriptMeta> {
        connect()
        connection!!.createStatement().use { st ->
            st.executeQuery(
                "SELECT id, name, description, website, path, deletable, terminal" +
                    " FROM scripts ORDER BY name"
            ).use { rs ->
                val list = mutableListOf<ScriptMeta>()
                while (rs.next()) {
                    list += ScriptMeta(
                        id = rs.getLong(1),
                        name = rs.getString(2),
                        description = rs.getString(3),
                        website = rs.getString(4),
                        path = rs.getString(5),
                        deletable = rs.getBoolean(6),
                        terminal = rs.getBoolean(7)
                    )
                }
                return list
            }
        }
    }

    // ==================================================
    // ========= Ajustes (tabla settings) ===============
    // ==================================================

    /**
     * Lee un ajuste persistido (idioma, tema…), o null si no existe todavía —
     * en ese caso la app usa su valor por defecto (inglés / tema claro).
     */
    @Synchronized
    fun getSetting(key: String): String? {
        connect()
        connection!!.prepareStatement("SELECT value FROM settings WHERE key = ?").use { ps ->
            ps.setString(1, key)
            ps.executeQuery().use { rs ->
                return if (rs.next()) rs.getString(1) else null
            }
        }
    }

    /** Guarda (o reemplaza) un ajuste. */
    @Synchronized
    fun setSetting(key: String, value: String) {
        connect()
        connection!!.prepareStatement(
            """
            INSERT INTO settings (key, value) VALUES (?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, key)
            ps.setString(2, value)
            ps.executeUpdate()
        }
    }
}
