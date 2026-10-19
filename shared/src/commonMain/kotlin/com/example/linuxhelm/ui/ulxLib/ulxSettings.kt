package com.example.linuxhelm.ui.ulxLib

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ajustes globales de la app (idioma y tema), con doble vida:
 *
 * 1. **Caché en memoria**: valores leídos una vez al arrancar; la UI los lee
 *    sin tocar disco (nada de queries SQLite dentro de composiciones).
 * 2. **Persistencia**: la tabla `settings` de `ulxDB` (`~/.config/ulx/LinuxHelm/lh.db`),
 *    para que sobrevivan reinicios.
 *
 * Se eligió la BD en lugar de un JSON porque ya existe (misma fuente de verdad
 * que los scripts), la escritura es atómica de serie y no hay un segundo formato
 * que pueda desincronizarse o corromperse a medias.
 *
 * Flujo:
 * - `main.kt` llama a `loadAndApply()` ANTES de `application {}`, así la primera
 *   ventana ya sale con el idioma y tema guardados (sin parpadeo de defaults).
 * - Al cambiar ajustes en Configuración, la pantalla llama a `saveLang()` /
 *   `saveTheme()`: actualiza caché + aplica + persiste en IO.
 */
object ulxSettings {

    /** Idioma: literal del LanguageSelector ("English", "Español"…). */
    var lang: String? = null
        private set

    /** Tema: clave estable ("light"/"dark"), nunca la etiqueta localizada. */
    var theme: String? = null
        private set

    /**
     * Escala de interfaz (1.0 = 100%, 1.5 = 150%…). Es **observable**
     * (`mutableStateOf`): al cambiarla desde Configuración, `main.kt` y `App.kt`
     * se recomponen y toda la UI (y la ventana) se reescalan en caliente, sin
     * reiniciar.
     *
     * Existe porque Java en Linux NO soporta escalas fraccionadas del sistema
     * (1.5 se ignora, solo 1x/2x vía `sun.java2d.uiScale`): en monitores HiDPI
     * la app se renderizaba diminuta. Resolviéndolo dentro de Compose funciona
     * en X11 y Wayland y no depende de cómo se lance la JVM.
     */
    var escala by mutableStateOf(1.0f)
        private set

    /**
     * Lee los ajustes de la BD y los aplica a los managers.
     *
     * Bloqueante (JDBC): por eso se llama desde main() ANTES de arrancar la UI,
     * no desde un composable. Cualquier fallo (BD ilegible, HOME raro…) deja los
     * valores por defecto en vez de impedir que la app arranque.
     */
    fun loadAndApply() {
        try {
            lang = ulxDB.getSetting("lang")
            theme = ulxDB.getSetting("theme")
            // Valor guardado como texto ("1.25"); cualquier basura vuelve a 1.0.
            escala = ulxDB.getSetting("escala")
                ?.toFloatOrNull()
                ?.coerceIn(0.5f, 4f)
                ?: 1.0f
        } catch (e: Exception) {
            System.err.println("ULX: no se pudieron leer los ajustes: $e")
            lang = null
            theme = null
            escala = 1.0f
        }
        ulxLangManager.applySaved(lang)
        ulxColorManager.applySaved(theme)
    }

    /** Persiste y aplica el idioma. Seguro para la UI: la BD va a IO. */
    suspend fun saveLang(value: String) {
        lang = value
        ulxLangManager.applySaved(value)
        withContext(Dispatchers.IO) {
            try {
                ulxDB.setSetting("lang", value)
            } catch (e: Exception) {
                System.err.println("ULX: no se pudo guardar el idioma: $e")
            }
        }
    }

    /** Persiste y aplica el tema ("light"/"dark"). Seguro para la UI: la BD va a IO. */
    suspend fun saveTheme(value: String) {
        theme = value
        ulxColorManager.applySaved(value)
        withContext(Dispatchers.IO) {
            try {
                ulxDB.setSetting("theme", value)
            } catch (e: Exception) {
                System.err.println("ULX: no se pudo guardar el tema: $e")
            }
        }
    }

    /**
     * Persiste y aplica la escala de interfaz (1.0 = 100%). La escritura es en
     * IO; la caché observable se actualiza ya, así la UI cambia de tamaño en
     * caliente mientras la BD confirma en segundo plano.
     */
    suspend fun saveEscala(value: Float) {
        escala = value
        withContext(Dispatchers.IO) {
            try {
                ulxDB.setSetting("escala", value.toString())
            } catch (e: Exception) {
                System.err.println("ULX: no se pudo guardar la escala: $e")
            }
        }
    }
}
