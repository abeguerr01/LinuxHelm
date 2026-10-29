package com.example.linuxhelm

import com.example.linuxhelm.ui.ulxLib.UlxAbout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests de la lógica pura de [UlxAbout]: lectura del recurso de versión y
 * comparación de versiones que decide si hay actualización. La consulta HTTP
 * en sí no se testea (requiere red); solo su parser, que es [UlxAbout].
 */
class UlxAboutTest {

    @Test
    fun `versionDesdePropiedades lee el campo version`() {
        val props = "# generada por Gradle\notra=clave\nversion=1.2.3\n"
        assertEquals("1.2.3", UlxAbout.versionDesdePropiedades(props))
    }

    @Test
    fun `versionDesdePropiedades sin campo o vacio devuelve dev`() {
        assertEquals("dev", UlxAbout.versionDesdePropiedades("otra=clave"))
        assertEquals("dev", UlxAbout.versionDesdePropiedades("version=\n"))
    }

    @Test
    fun `compararVersiones ordena por segmentos numericos`() {
        assertTrue(UlxAbout.compararVersiones("1.0.1", "1.0.0") > 0)
        // 1.10 es mayor que 1.9 aunque "10" < "9" como texto
        assertTrue(UlxAbout.compararVersiones("1.10.0", "1.9.0") > 0)
        assertTrue(UlxAbout.compararVersiones("1.9.9", "2.0") < 0)
        // La "v" inicial de los tags de GitHub no cuenta
        assertEquals(0, UlxAbout.compararVersiones("v1.2.3", "1.2.3"))
        // Los segmentos ausentes valen 0
        assertEquals(0, UlxAbout.compararVersiones("1.2", "1.2.0"))
    }

    @Test
    fun `compararVersiones trata el sufijo como pre-release`() {
        assertTrue(UlxAbout.compararVersiones("1.0.0", "1.0.0-rc1") > 0)
        assertEquals(0, UlxAbout.compararVersiones("1.0.0-rc1", "1.0.0-rc2"))
    }

    @Test
    fun `esVersionMasNueva solo con releases posteriores`() {
        assertTrue(UlxAbout.esVersionMasNueva("v1.1.0", "1.0.0"))
        assertFalse(UlxAbout.esVersionMasNueva("1.0.0", "1.0.0"))
        assertFalse(UlxAbout.esVersionMasNueva("1.0.0", "2.0.0"))
    }

    @Test
    fun `versionDeLaApp nunca esta vacia`() {
        // Con el recurso embebido devuelve APP_VERSION (p. ej. "1.0.0"); sin él,
        // el fallback "dev". Ambos casos son no vacíos y sin excepciones.
        assertTrue(UlxAbout.versionDeLaApp().isNotBlank())
    }
}
