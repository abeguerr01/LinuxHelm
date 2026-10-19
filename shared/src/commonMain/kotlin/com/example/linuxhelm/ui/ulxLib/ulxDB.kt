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
 */
data class ScriptMeta(
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val website: String? = null,
    val path: String,
    val deletable: Boolean = true
)

/**
 * Herramienta que se incluye de serie con la app.
 *
 * [resource] es la ruta del recurso dentro del JAR (empaquetada en el build
 * desde `ulxTools/`). [targetName] es el nombre del fichero al extraerlo a
 * `~/.config/ulx/LinuxHelm/tools/`.
 */
private data class BundledTool(
    val resource: String,
    val targetName: String,
    val name: String,
    val description: String,
    val website: String
)

private val bundledTools = listOf(
    BundledTool(
        resource = "/ulxTools/ulxCheckDirtyDrives.sh",
        targetName = "ulxCheckDirtyDrives.sh",
        name = "ulxCheckDirtyDrives",
        description = "Comprueba y repara particiones NTFS \"dirty\" o hibernadas por Windows",
        website = "https://github.com/abeguerr01/LinuxHelm/tree/master/ulxTools/ulxCheckDirtyDrives"
    )
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

    /** Carpeta donde se extraen las herramientas incluidas de serie. */
    private val toolsDir: File by lazy {
        File(configDir, "tools")
    }

    /** Carpeta donde se extrae y escanea el árbol de herramientas "ULX Tools". */
    private val systemToolsDir: File by lazy {
        File(configDir, "ULX Tools")
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

        // Después de tener las tablas listas, registra (o re-registra) las
        // herramientas incluidas de serie. Es idempotente.
        seedBundledTools()
        // Árbol genérico ULX Tools: escaneo recursivo + registro como no borrable.
        seedSystemTools()
    }

    /**
     * Migración de BDs creadas antes de existir la columna `deletable`.
     *
     * `CREATE TABLE IF NOT EXISTS` no añade columnas a una tabla que ya existe,
     * así que se comprueba con `PRAGMA table_info` y se agrega si falta.
     * Los registros existentes quedan como `deletable = 1` (editables por el usuario),
     * que es el comportamiento por defecto de siempre.
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
    }

    /**
     * Extrae las herramientas incluidas (empaquetadas dentro del JAR) a
     * `~/.config/ulx/LinuxHelm/tools/` y las registra en la BD como no borrables.
     *
     * Se extraen en cada arranque para que, al actualizar la app, la herramienta
     * quede siempre actualizada (el recurso del JAR es la nueva versión).
     * `ON CONFLICT(path) DO UPDATE` evita duplicados si ya existía la entrada.
     */
    private fun seedBundledTools() {
        for (tool in bundledTools) {
            val extracted = extractResource(tool) ?: continue
            connection!!.prepareStatement(
                """
                INSERT INTO scripts (name, description, website, path, deletable)
                VALUES (?, ?, ?, ?, 0)
                ON CONFLICT(path) DO UPDATE SET
                    name = excluded.name,
                    description = excluded.description,
                    website = excluded.website,
                    deletable = 0
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, tool.name)
                ps.setString(2, tool.description)
                ps.setString(3, tool.website)
                ps.setString(4, extracted.absolutePath)
                ps.executeUpdate()
            }
        }
    }

    /** Copia un recurso del JAR a disco y le da permiso de ejecución. Devuelve el fichero, o null si no está. */
    private fun extractResource(tool: BundledTool): File? {
        val input = ulxDB::class.java.getResourceAsStream(tool.resource) ?: return null
        val target = File(toolsDir, tool.targetName)
        return try {
            target.parentFile?.mkdirs()
            input.use { ins ->
                target.outputStream().use { out -> ins.copyTo(out) }
            }
            target.setExecutable(true, false)
            target
        } catch (e: Exception) {
            System.err.println("ULX: no se pudo extraer ${tool.targetName}: $e")
            null
        } finally {
            input.close()
        }
    }

    // ==================================================
    // ===== ULX Tools (árbol genérico) ================
    // ==================================================

    /**
     * Sincroniza las herramientas del árbol `ULX Tools/` (empaquetadas en el JAR
     * y extraídas a `~/.config/ulx/LinuxHelm/ULX Tools/`).
     *
     * 1. Extrae el árbol del JAR a disco (idempotente: al actualizar la app
     *    quedan siempre las últimas versiones).
     * 2. Escanea recursivamente buscando carpetas con un `.sh` y registra cada
     *    una en la BD con `deletable = 0` (la UI no muestra la papelera).
     * 3. Poda los registros obsoletos: los que estaban bajo `ULX Tools/` pero ya
     *    no existen en el árbol (carpeta renombrada o borrada entre versiones).
     *
     * NO toca las herramientas añadidas por el usuario (`deletable = 1`) ni la
     * herramienta "bundled" clásica (`ulxCheckDirtyDrives`, que vive en `tools/`,
     * otra ruta distinta). Ambos mecanismos conviven sin solaparse.
     */
    @Synchronized
    fun seedSystemTools() {
        try {
            extractResourceTree("ULXTools", systemToolsDir)
        } catch (e: Exception) {
            System.err.println("ULX: no se pudo extraer ULX Tools: $e")
        }

        val tools = try {
            discoverTools(systemToolsDir)
        } catch (e: Exception) {
            System.err.println("ULX: no se pudo escanear ULX Tools: $e")
            emptyList()
        }

        for (t in tools) upsertSystemTool(t)
        pruneSystemTools(tools.mapTo(mutableSetOf()) { it.scriptPath })
    }

    /** Registra (o actualiza) una herramienta ULX como NO borrable. */
    private fun upsertSystemTool(tool: DiscoveredTool) {
        connection!!.prepareStatement(
            """
            INSERT INTO scripts (name, description, website, path, deletable)
            VALUES (?, ?, ?, ?, 0)
            ON CONFLICT(path) DO UPDATE SET
                name = excluded.name,
                description = excluded.description,
                website = excluded.website,
                deletable = 0
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, tool.name)
            ps.setString(2, tool.description)
            ps.setString(3, tool.website)
            ps.setString(4, tool.scriptPath)
            ps.executeUpdate()
        }
    }

    /**
     * Borra los registros de herramientas ULX cuyo archivo ya no está en el
     * árbol extraído. Solo afecta a rutas bajo `ULX Tools/` con `deletable = 0`,
     * así que nunca toca herramientas del usuario ni la bundled clásica.
     */
    private fun pruneSystemTools(currentPaths: Set<String>) {
        val prefix = systemToolsDir.absolutePath + File.separator
        val staleIds = mutableListOf<Long>()
        connection!!.createStatement().use { st ->
            st.executeQuery("SELECT id, path FROM scripts WHERE deletable = 0").use { rs ->
                while (rs.next()) {
                    val id = rs.getLong(1)
                    val path = rs.getString(2)
                    if (path.startsWith(prefix) && path !in currentPaths) staleIds += id
                }
            }
            for (id in staleIds) {
                st.executeUpdate("DELETE FROM scripts WHERE id = $id")
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
     * entrada se actualizan nombre y web. Así "re-añadir" el mismo archivo sirve
     * para editarlo, sin necesidad de pantalla de edición aparte.
     *
     * Por defecto `deletable = true` (script del usuario). Las herramientas
     * incluidas de serie se registran con `false` vía `seedBundledTools()`.
     */
    @Synchronized
    fun upsertScript(name: String, website: String?, path: String) {
        connect()
        connection!!.prepareStatement(
            """
            INSERT INTO scripts (name, website, path, deletable) VALUES (?, ?, ?, 1)
            ON CONFLICT(path) DO UPDATE SET
                name = excluded.name,
                website = excluded.website
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, name)
            ps.setString(2, website)
            ps.setString(3, path)
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
                "SELECT id, name, description, website, path, deletable FROM scripts ORDER BY name"
            ).use { rs ->
                val list = mutableListOf<ScriptMeta>()
                while (rs.next()) {
                    list += ScriptMeta(
                        id = rs.getLong(1),
                        name = rs.getString(2),
                        description = rs.getString(3),
                        website = rs.getString(4),
                        path = rs.getString(5),
                        deletable = rs.getBoolean(6)
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
