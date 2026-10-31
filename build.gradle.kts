plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

// ============================================================================
// Versión de EMPAQUETADO (.deb/.rpm): sale del ÚLTIMO TAG GIT (v1, v2, v3…),
// que es cómo se rotulan los releases (rama vN -> squash merge -> tag vX). Es
// solo metadato del paquete: la app NO muestra ninguna versión en pantalla.
// Para forzar una versión puntual: ./gradlew … -PAPP_VERSION=3
// ============================================================================
val versionCruda: String =
    (findProperty("APP_VERSION") as String?)?.trim()?.takeIf { it.isNotEmpty() }
        ?: git("describe", "--tags", "--always")
        ?: "dev"

// Normalizada para empaquetar: sin "v" inicial ni el sufijo "-N-gHASH" (dpkg
// exige que la versión del .deb empiece por dígito). Sin tags => 0.0.0.
val version: String = versionCruda.removePrefix("v").substringBefore('-')
    .let { if (it.any(Char::isDigit)) it else "0.0.0" }

extra["linuxHelmVersion"] = version

/** Ejecuta `git <args…>` en la raíz del repo vía [GitCommand] (null si falla). */
fun git(vararg args: String): String? =
    providers.of(GitCommand::class.java) {
        parameters.workingDir.set(rootDir)
        parameters.args.set(args.toList())
    }.orNull?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Ejecuta un comando git como ValueSource de Gradle: con la configuration
 * cache el valor consultado se registra como ENTRADA del build, así que crear
 * un tag (o avanzar de commit) invalida la caché y la siguiente compilación ya
 * ve la versión nueva (si fuera un simple exec en la configuration, la caché
 * lo congelaría).
 */
abstract class GitCommand : ValueSource<String, GitCommand.Params> {
    interface Params : ValueSourceParameters {
        val workingDir: DirectoryProperty
        val args: ListProperty<String>
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String? = try {
        val buffer = java.io.ByteArrayOutputStream()
        val resultado = execOperations.exec {
            commandLine(listOf("git") + parameters.args.get())
            workingDir = parameters.workingDir.get().asFile
            standardOutput = buffer
            isIgnoreExitValue = true
        }
        if (resultado.exitValue != 0) null
        else buffer.toString(Charsets.UTF_8.name()).trim().takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null // Fuera de un repo git (tarball) o sin git instalado
    }
}
