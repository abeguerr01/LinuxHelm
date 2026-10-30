package com.example.linuxhelm

import com.example.linuxhelm.ui.ulxLib.CpuInfo
import com.example.linuxhelm.ui.ulxLib.MemInfo
import com.example.linuxhelm.ui.ulxLib.formatRam
import com.example.linuxhelm.ui.ulxLib.formatUptime
import com.example.linuxhelm.ui.ulxLib.gbDesdeMb
import com.example.linuxhelm.ui.ulxLib.leerEquipo
import com.example.linuxhelm.ui.ulxLib.parseCpuinfo
import com.example.linuxhelm.ui.ulxLib.parseLoadavg
import com.example.linuxhelm.ui.ulxLib.parseMeminfo
import com.example.linuxhelm.ui.ulxLib.parseOsRelease
import com.example.linuxhelm.ui.ulxLib.parseTemperaturas
import com.example.linuxhelm.ui.ulxLib.parseUptime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests de los parsers de [ulxSysInfo][com.example.linuxhelm.ui.ulxLib] con
 * muestras reales de /proc, /etc y /sys: la tarjeta "Equipo" de Información
 * solo pinta texto ya parseado por estas funciones puras.
 */
class UlxSysInfoTest {

    private val cpuinfoMuestra = """
        processor	: 0
        model name	: AMD Ryzen 5 3600 6-Core Processor
        cpu MHz		: 2199.987
        processor	: 1
        model name	: AMD Ryzen 5 3600 6-Core Processor
        processor	: 2
        model name	: AMD Ryzen 5 3600 6-Core Processor
    """.trimIndent()

    @Test
    fun `parseCpuinfo cuenta nucleos logicos y toma el modelo`() {
        val cpu = parseCpuinfo(cpuinfoMuestra)
        assertEquals(3, cpu.nucleos)
        assertEquals("AMD Ryzen 5 3600 6-Core Processor", cpu.modelo)
    }

    @Test
    fun `parseCpuinfo con texto vacio devuelve guion y cero`() {
        assertEquals(CpuInfo("—", 0), parseCpuinfo(""))
    }

    @Test
    fun `parseCpuinfo en ARM lee la clave Model`() {
        val arm = """
            processor	: 0
            Model		: Raspberry Pi 4 Model B Rev 1.1
            processor	: 1
            Model		: Raspberry Pi 4 Model B Rev 1.1
        """.trimIndent()
        val cpu = parseCpuinfo(arm)
        assertEquals(2, cpu.nucleos)
        assertEquals("Raspberry Pi 4 Model B Rev 1.1", cpu.modelo)
    }

    @Test
    fun `parseMeminfo lee total y disponible`() {
        val muestra = """
            MemTotal:       16323456 kB
            MemFree:         1234567 kB
            MemAvailable:    8123456 kB
            Buffers:          123456 kB
        """.trimIndent()
        assertEquals(MemInfo(16323456, 8123456), parseMeminfo(muestra))
    }

    @Test
    fun `parseMeminfo sin MemAvailable devuelve cero`() {
        val muestra = "MemTotal:       1024 kB\nMemFree:         512 kB"
        assertEquals(MemInfo(1024, 0), parseMeminfo(muestra))
    }

    @Test
    fun `parseUptime lee el primer campo en segundos`() {
        assertEquals(12345L, parseUptime("12345.67 23456.89 0 1"))
        assertEquals(-1L, parseUptime("basura"))
    }

    @Test
    fun `parseLoadavg devuelve las tres cifras`() {
        assertEquals("0.52  0.58  0.59", parseLoadavg("0.52 0.58 0.59 2/1234 56789"))
    }

    @Test
    fun `parseOsRelease limpia comillas y comentarios`() {
        val muestra = """
            NAME="Ubuntu"
            VERSION="24.04.1 LTS (Noble Numbat)"
            # comentario que se ignora
            ID=ubuntu
            PRETTY_NAME="Ubuntu 24.04.1 LTS"
        """.trimIndent()
        val m = parseOsRelease(muestra)
        assertEquals("Ubuntu", m["NAME"])
        assertEquals("Ubuntu 24.04.1 LTS", m["PRETTY_NAME"])
        assertEquals("ubuntu", m["ID"])
        assertTrue("comentario" !in m)
    }

    @Test
    fun `parseTemperaturas descarta fuera de rango y deduplica por sensor`() {
        val zonas = listOf(
            "x86_pkg_temp" to "45600",   // CPU a 45,6 °C
            "x86_pkg_temp" to "52100",   // otra zona de la CPU, más caliente
            "acpitz" to "27800",
            "virt0" to "0",              // sensor muerto
            "nonsense" to "999999",      // lectura absurda
            "nvme0" to "38250"
        )
        val temps = parseTemperaturas(zonas)
        assertEquals(3, temps.size)
        assertEquals(52, temps.first { it.etiqueta == "CPU" }.grados)
        assertEquals(38, temps.first { it.etiqueta == "NVMe" }.grados)
        assertTrue(temps.any { it.etiqueta == "acpitz" && it.grados == 28 })
    }

    @Test
    fun `formatUptime pinta dias y reloj`() {
        assertEquals("1d 01:01:01", formatUptime(90061))
        assertEquals("01:01:01", formatUptime(3661))
        assertEquals("", formatUptime(-1))
    }

    @Test
    fun `gbDesdeMb convierte sin depender del locale`() {
        assertEquals("512 MB", gbDesdeMb(512))
        assertEquals("7.6 GB", gbDesdeMb(7782))
        assertEquals("16.0 GB", gbDesdeMb(16384))
    }

    @Test
    fun `formatRam pinta usada sobre total con porcentaje`() {
        assertEquals("8.0 GB / 16.0 GB (50 %)", formatRam(16384, 8192))
        assertEquals("", formatRam(0, 0))
    }

    @Test
    fun `leerEquipo no lanza excepciones`() {
        // En Linux lee /proc y /etc de verdad; en cualquier otro caso los campos
        // quedan vacíos pero la llamada NUNCA debe romperse.
        val equipo = leerEquipo()
        assertTrue(equipo.ramTotalMb >= 0)
    }
}
