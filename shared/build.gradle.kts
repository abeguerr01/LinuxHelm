import org.gradle.api.tasks.Copy

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()


    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
            implementation(libs.sqlite.jdbc)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// Incluye las herramientas de ulxTools/ como recurso del target JVM para que
// viajen DENTRO del JAR. Así están disponibles en runtime aunque la app se
// empaquete como .deb, .rpm, .AppImage, tar.gz o binario (el recurso viaja en
// el classpath, no necesita la carpeta ulxTools/ en disco).
//
// Hay una sola copia en disco (ulxTools/), el build la empaqueta. En runtime
// se extrae a ~/.config/ulx/LinuxHelm/tools/ y se registra en la BD.
tasks.named<Copy>("jvmProcessResources") {
    from(rootProject.file("ulxTools/ulxCheckDirtyDrives/ulxCheckDirtyDrives.sh")) {
        into("ulxTools")
    }
}