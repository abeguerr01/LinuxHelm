import java.io.File
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.example.linuxhelm.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "com.example.linuxhelm"
            packageVersion = "1.0.0"
        }
    }
}

// .tar.gz portátil: comprime la app-image que genera "createDistributable"
// (launcher + jars + runtime de Java recortado, no requiere Java instalado).
val packageTarGz = tasks.register<Tar>("packageTarGz") {
    group = "compose desktop"
    description = "Crea un .tar.gz portátil de la aplicación"
    dependsOn("createDistributable")

    val arch = System.getProperty("os.arch").let { if (it == "amd64") "x86_64" else it }
    archiveFileName.set("linuxhelm-1.0.0-linux-$arch.tar.gz")
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main/tar"))
    from(layout.buildDirectory.dir("compose/binaries/main/app"))
    compression = Compression.GZIP
}

// ---------- AppImage ----------
val appImageArch = System.getProperty("os.arch").let { if (it == "amd64") "x86_64" else it }

// Localiza appimagetool sin depender del PATH de la terminal (así funciona
// igual desde IntelliJ o CI): 1) variable APPIMAGETOOL, 2) instalación local
// en ~/.local/share/appimagetool, 3) PATH del sistema.
val appImageTool = System.getenv("APPIMAGETOOL") ?: run {
    val local = File(System.getProperty("user.home"), ".local/share/appimagetool/appimagetool")
    if (local.canExecute()) local.absolutePath else "appimagetool"
}

// Limpia el AppDir previo: los ficheros del runtime de jlink llegan de solo
// lectura y Sync no podría sobrescribirlos en builds sucesivos.
val cleanAppImageDir = tasks.register<Delete>("cleanAppImageDir") {
    group = "compose desktop"
    description = "Elimina el AppDir del AppImage"
    delete(layout.buildDirectory.dir("compose/binaries/main/appimage/AppDir"))
}

// 1) Prepara el AppDir: contenido de la app-image + AppRun + .desktop + icono
//    (AppRun y .desktop viven como plantillas en desktopApp/appimage/)
val prepareAppImageDir = tasks.register<Sync>("prepareAppImageDir") {
    group = "compose desktop"
    description = "Prepara el directorio AppDir para el AppImage"
    dependsOn(cleanAppImageDir, "createDistributable")

    from(layout.buildDirectory.dir("compose/binaries/main/app/com.example.linuxhelm"))
    from(layout.projectDirectory.dir("appimage"))
    from(rootProject.file("shared/src/commonMain/composeResources/drawable/logo.png")) {
        rename { "linuxhelm.png" }
    }
    into(layout.buildDirectory.dir("compose/binaries/main/appimage/AppDir"))
}

// 2) Empaqueta el AppDir en un .AppImage con appimagetool.
val packageAppImage = tasks.register<Exec>("packageAppImage") {
    group = "compose desktop"
    description = "Crea un .AppImage portátil de la aplicación"
    dependsOn(prepareAppImageDir)

    val appDir = layout.buildDirectory.dir("compose/binaries/main/appimage/AppDir")
    val outputFile = layout.buildDirectory.file(
        "compose/binaries/main/appimage/linuxhelm-1.0.0-linux-$appImageArch.AppImage"
    )
    inputs.dir(appDir)
    outputs.file(outputFile)
    environment("ARCH", appImageArch)

    // AppRun debe ser ejecutable dentro del AppImage (el repo no guarda ese bit)
    doFirst {
        appDir.get().asFile.resolve("AppRun").setExecutable(true)
    }

    commandLine(appImageTool.split(' '))
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(appDir.get().asFile.absolutePath, outputFile.get().asFile.absolutePath)
    })
}