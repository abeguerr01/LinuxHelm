package com.example.linuxhelm.ui.ulxLib

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
        } catch (e: Exception) {
            System.err.println("ULX: no se pudieron leer los ajustes: $e")
            lang = null
            theme = null
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
}
