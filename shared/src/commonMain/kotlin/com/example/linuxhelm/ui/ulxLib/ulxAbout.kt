package com.example.linuxhelm.ui.ulxLib

import java.net.HttpURLConnection
import java.net.URL

/**
 * Resultado de comprobar actualizaciones en GitHub.
 *
 * Si [error] no es null, la consulta falló (sin red, HTTP != 200…) y el resto
 * se ignora. Si fue buena: [versionRemota] es el tag del último release y
 * [hayActualizacion] indica si es más nueva que la instalada.
 */
data class ResultadoActualizacion(
    val versionRemota: String?,
    val hayActualizacion: Boolean,
    val error: String? = null
)

/**
 * "Acerca de": versión de la app y comprobación de actualizaciones.
 *
 * La versión vive en UNA sola fuente: `APP_VERSION` de gradle.properties.
 * Gradle la embebe en el classpath como `ulxVersion.properties` (ver
 * shared/build.gradle.kts), así que la misma cifra que empaqueta (.deb, .AppImage…)
 * es la que se ve en la app. La consulta a GitHub es manual (botón) para no
 * agotar el rate limit anónimo de la API.
 */
object UlxAbout {

    /** Último release público del proyecto (API de GitHub, sin autenticación). */
    const val URL_ACTUALIZACIONES =
        "https://api.github.com/repos/abeguerr01/LinuxHelm/releases/latest"

    private const val RECURSO_VERSION = "/ulxVersion.properties"

    /**
     * Versión embebida en el build. Si el recurso no está (p. ej. ejecutando
     * desde el IDE sin pasar por processResources) devuelve "dev".
     */
    fun versionDeLaApp(): String {
        val recurso = UlxAbout::class.java.getResourceAsStream(RECURSO_VERSION)
            ?: return "dev"
        val texto = recurso.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return versionDesdePropiedades(texto)
    }

    /** Extrae `version=1.2.3` de un .properties (puro, testeable). */
    fun versionDesdePropiedades(texto: String): String {
        for (linea in texto.lineSequence()) {
            val l = linea.trim()
            if (l.startsWith("version=")) {
                return l.substringAfter('=').trim().ifEmpty { "dev" }
            }
        }
        return "dev"
    }

    /**
     * Compara versiones tipo "1.2.3" numéricamente por segmentos (1.10 > 1.9,
     * "v" inicial opcional, segmentos ausentes = 0). Los sufijos "-rc1" no
     * cuentan como número: a igual base, la versión SIN sufijo (release puro)
     * gana.
     */
    fun compararVersiones(a: String, b: String): Int {
        val baseA = a.trim().removePrefix("v").substringBefore('-')
        val baseB = b.trim().removePrefix("v").substringBefore('-')
        val pa = baseA.split('.')
        val pb = baseB.split('.')
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val va = pa.getOrNull(i)?.trim()?.toIntOrNull() ?: 0
            val vb = pb.getOrNull(i)?.trim()?.toIntOrNull() ?: 0
            if (va != vb) return va.compareTo(vb)
        }
        val conSufijoA = a.trim().contains('-')
        val conSufijoB = b.trim().contains('-')
        return when {
            conSufijoA == conSufijoB -> 0
            conSufijoA -> -1 // "1.0.0-rc1" < "1.0.0"
            else -> 1
        }
    }

    /** ¿La versión [remota] es más nueva que la [actual]? */
    fun esVersionMasNueva(remota: String, actual: String): Boolean =
        compararVersiones(remota, actual) > 0

    /**
     * Consulta el último release en GitHub y lo compara con la versión
     * instalada. **Bloqueante** (red): llamar desde Dispatchers.IO. El tag se
     * extrae con el parser JSON mínimo de ulxSystemTools (sin dependencias).
     */
    fun consultarUltimaVersion(actual: String = versionDeLaApp()): ResultadoActualizacion {
        return try {
            val conn = URL(URL_ACTUALIZACIONES).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            // La API de GitHub exige un User-Agent y prefiere el Accept explícito
            conn.setRequestProperty("User-Agent", "LinuxHelm")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val codigo = conn.responseCode
            if (codigo != 200) {
                ResultadoActualizacion(null, false, "HTTP $codigo")
            } else {
                val cuerpo = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val tag = jsonStringField(cuerpo, "tag_name")
                if (tag.isNullOrBlank()) {
                    ResultadoActualizacion(null, false, "tag_name ausente")
                } else {
                    ResultadoActualizacion(tag, esVersionMasNueva(tag, actual))
                }
            }
        } catch (e: Exception) {
            ResultadoActualizacion(null, false, e::class.simpleName ?: "error")
        }
    }
}
