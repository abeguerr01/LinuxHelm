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

// Empaqueta DENTRO del JAR la carpeta ulxTools/ (cada subcarpeta = una
// herramienta: ejecutable + metadata.json + docs) con el prefijo "ULXTools/".
// En runtime, ulxDB.seedSystemTools() extrae el árbol a una carpeta temporal,
// despliega SOLO el ejecutable en ~/.config/ulx/LinuxHelm/tools/ (+x) y lee el
// metadata.json para registrar la herramienta como no borrable. Al viajar en el
// classpath, funciona igual con run, .deb, AppImage o tar.gz.
tasks.named<Copy>("jvmProcessResources") {
    from(rootProject.file("ulxTools")) {
        into("ULXTools")
    }
}