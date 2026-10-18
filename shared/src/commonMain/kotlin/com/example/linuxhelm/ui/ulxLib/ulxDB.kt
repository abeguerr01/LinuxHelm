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
 */
data class ScriptMeta(
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val website: String? = null,
    val path: String
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

    private val dbFile: File by lazy {
        val home = System.getProperty("user.home")
        File(home, ".config/ulx/LinuxHelm/lh.db").apply { parentFile?.mkdirs() }
    }

    private var connection: Connection? = null

    /** Abre la conexión (si no estaba abierta) y crea las tablas si no existen. */
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
                    path TEXT NOT NULL UNIQUE
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
     */
    @Synchronized
    fun upsertScript(name: String, website: String?, path: String) {
        connect()
        connection!!.prepareStatement(
            """
            INSERT INTO scripts (name, website, path) VALUES (?, ?, ?)
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

    /** Borra el registro de un script. NO toca el archivo en disco. */
    @Synchronized
    fun deleteScript(id: Long) {
        connect()
        connection!!.prepareStatement("DELETE FROM scripts WHERE id = ?").use { ps ->
            ps.setLong(1, id)
            ps.executeUpdate()
        }
    }

    /** Todos los scripts registrados, ordenados por nombre. */
    @Synchronized
    fun getAllScripts(): List<ScriptMeta> {
        connect()
        connection!!.createStatement().use { st ->
            st.executeQuery("SELECT id, name, description, website, path FROM scripts ORDER BY name")
                .use { rs ->
                    val list = mutableListOf<ScriptMeta>()
                    while (rs.next()) {
                        list += ScriptMeta(
                            id = rs.getLong(1),
                            name = rs.getString(2),
                            description = rs.getString(3),
                            website = rs.getString(4),
                            path = rs.getString(5)
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
