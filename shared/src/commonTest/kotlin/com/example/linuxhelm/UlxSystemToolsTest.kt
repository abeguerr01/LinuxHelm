package com.example.linuxhelm

import com.example.linuxhelm.ui.ulxLib.discoverTools
import com.example.linuxhelm.ui.ulxLib.jsonStringField
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests de la lógica de ulxTools (ulxSystemTools.kt): descubrimiento recursivo,
 * metadatos y fallback. Todo sobre disco temporal, sin tocar la BD.
 */
class UlxSystemToolsTest {

    private val tmp: File = Files.createTempDirectory("ulxtools").toFile()

    @AfterTest
    fun cleanup() {
        tmp.deleteRecursively()
    }

    private fun dir(name: String): File = File(tmp, name).apply { mkdirs() }

    @Test
    fun `descubre herramientas con metadata y hace fallback sin ella`() {
        // Carpeta CON metadata.json
        val a = dir("con-meta")
        File(a, "con-meta.sh").writeText("#!/bin/sh\n")
        File(a, "metadata.json").writeText(
            """{"name":"Nombre Bonito","description":"Desc","website":"https://x"}"""
        )

        // Carpeta SIN metadata.json -> el nombre por defecto es el de la carpeta
        val b = dir("sin-meta")
        File(b, "sin-meta.sh").writeText("#!/bin/sh\n")

        // Carpeta SIN .sh -> no es herramienta, se ignora
        val c = dir("vacia")
        File(c, "notas.txt").writeText("x")

        val tools = discoverTools(tmp).associateBy { File(it.scriptPath).parentFile!!.name }

        assertEquals(2, tools.size)
        assertEquals("Nombre Bonito", tools.getValue("con-meta").name)
        assertEquals("Desc", tools.getValue("con-meta").description)
        assertEquals("https://x", tools.getValue("con-meta").website)
        assertEquals("sin-meta", tools.getValue("sin-meta").name)
        assertNull(tools.getValue("sin-meta").description)
        assertNull(tools.getValue("sin-meta").website)
    }

    @Test
    fun `escanea recursivamente subcarpetas anidadas`() {
        val nested = File(tmp, "grupo/herramienta").apply { mkdirs() }
        File(nested, "herramienta.sh").writeText("#!/bin/sh\n")

        val tools = discoverTools(tmp)
        assertEquals(1, tools.size)
        assertEquals("herramienta", tools[0].name)
        assertTrue(tools[0].scriptPath.endsWith("herramienta.sh"))
    }

    @Test
    fun `elige el sh que coincide con la carpeta como ejecutable principal`() {
        val d = dir("doble")
        File(d, "aux.sh").writeText("#!/bin/sh\n")
        File(d, "doble.sh").writeText("#!/bin/sh\n")

        val tools = discoverTools(tmp)
        assertEquals(1, tools.size)
        assertTrue(tools[0].scriptPath.endsWith("doble.sh"))
    }

    @Test
    fun `raiz inexistente o vacia devuelve lista vacia`() {
        assertTrue(discoverTools(File(tmp, "no-existe")).isEmpty())
        assertTrue(discoverTools(tmp).isEmpty())
    }

    @Test
    fun `parsea escapes de comillas, barra y salto de linea`() {
        val json = """{"name":"Comillas \"y\" \\ final\nl\u0041"}"""
        assertEquals("Comillas \"y\" \\ final\nlA", jsonStringField(json, "name"))
    }

    @Test
    fun `campo ausente o no-string devuelve null`() {
        assertNull(jsonStringField("""{"otro":"x"}""", "name"))
        assertNull(jsonStringField("""{"name":42}""", "name"))
        assertNull(jsonStringField("{}", "website"))
    }

    @Test
    fun `detecta todos los tipos de ejecutable admitidos`() {
        // Una carpeta por tipo, cada una con un solo ejecutable
        val byFolder = mapOf(
            "t-sh" to "script.sh",
            "t-jar" to "app.jar",
            "t-py" to "script.py",
            "t-run" to "instalador.run",
            "t-bin" to "util.bin",
            "t-appimage" to "app.AppImage",
        )
        byFolder.forEach { (folder, file) ->
            File(dir(folder), file).writeBytes(byteArrayOf(0x7F, 0x45, 0x4C, 0x46))
        }
        val names = discoverTools(tmp).map { it.name }.toSet()
        assertEquals(byFolder.keys, names)
    }

    @Test
    fun `detecta binario sin extension aunque no tenga +x`() {
        val d = dir("binario")
        // ELF + byte NUL: isProbablyBinary -> true (aunque no sea +x)
        File(d, "otro-nombre").writeBytes(byteArrayOf(0x7F, 0x45, 0x4C, 0x46, 0x00))
        File(d, "README.md").writeText("documento")

        val tools = discoverTools(tmp)
        assertEquals(1, tools.size)
        assertTrue(tools[0].scriptPath.endsWith("otro-nombre"))
    }

    @Test
    fun `prefiere el ejecutable que coincide con el nombre de la carpeta`() {
        val d = dir("fzf")
        File(d, "fzf").writeBytes(byteArrayOf(0x7F, 0x45, 0x4C, 0x46, 0x00)) // binario principal
        File(d, "ayuda.bin").writeBytes(byteArrayOf(0x7F, 0x45, 0x4C, 0x46, 0x00))

        val tools = discoverTools(tmp)
        assertEquals(1, tools.size)
        assertTrue(tools[0].scriptPath.endsWith("fzf"))
    }

    @Test
    fun `no detecta carpeta solo con ficheros de texto`() {
        val d = dir("doc")
        File(d, "LICENSE").writeText("MIT License") // sin extensión, texto, sin +x
        File(d, "metadata.json").writeText("{}")
        File(d, "README.md").writeText("hola")

        assertTrue(discoverTools(tmp).isEmpty())
    }
}
