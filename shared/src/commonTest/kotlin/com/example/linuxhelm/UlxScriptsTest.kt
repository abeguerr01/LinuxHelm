package com.example.linuxhelm

import com.example.linuxhelm.ui.ulxLib.buildCommand
import com.example.linuxhelm.ui.ulxLib.needsExecuteBit
import com.example.linuxhelm.ui.ulxLib.openScriptDirect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests de `ulxScripts.kt`: construcción de comando, bit de ejecución y
 * lanzamiento directo (sin terminal) de apps gráficas.
 *
 * El caso "sigue vivo" es una regresión: `Process.exitValue()` revienta con
 * IllegalThreadStateException si el proceso aún corre, así que la comprobación
 * tiene que mirar `isAlive` ANTES (una app GUI sigue viva a los 400 ms y, con
 * el orden antiguo, el lanzamiento moría en silencio y no pasaba nada).
 */
class UlxScriptsTest {

    @Test
    fun `buildCommand segun la extension`() {
        assertEquals(listOf("bash", "/x/a.sh"), buildCommand("/x/a.sh"))
        assertEquals(listOf("java", "-jar", "/x/a.jar"), buildCommand("/x/a.jar"))
        assertEquals(listOf("python3", "/x/a.py"), buildCommand("/x/a.py"))
        // Sin extensión conocida: se lanza directo (binario ELF, AppImage…)
        assertEquals(listOf("/x/appimage"), buildCommand("/x/appimage"))
    }

    @Test
    fun `needsExecuteBit solo para lo que se lanza directo`() {
        assertFalse(needsExecuteBit("/x/a.sh"))
        assertFalse(needsExecuteBit("/x/a.jar"))
        assertFalse(needsExecuteBit("/x/a.py"))
        assertTrue(needsExecuteBit("/x/binario"))
        assertTrue(needsExecuteBit("/x/app.AppImage"))
    }

    @Test
    fun `directo, proceso que termina bien devuelve true`() {
        assertTrue(openScriptDirect("/bin/true"))
    }

    @Test
    fun `directo, proceso que falla al instante devuelve false`() {
        assertFalse(openScriptDirect("/bin/false"))
    }

    @Test
    fun `directo, proceso que sigue vivo NO revienta (regresion exitValue)`() {
        // Una app gráfica sigue viva a los 400 ms: con el orden antiguo
        // (exitValue() primero) esto lanzaba IllegalThreadStateException.
        assertTrue(openScriptDirect("/bin/sleep", listOf("5")))
    }

    @Test
    fun `directo, fichero inexistente devuelve false`() {
        assertFalse(openScriptDirect("/no/existe/nada"))
    }
}
