> ### [Documentation in English](README_EN.md)

# LinuxHelm

**LinuxHelm** es una aplicación de escritorio para Linux construida con **Kotlin** y
**Compose Multiplatform**. Funciona como un panel de utilidades del sistema: muestra
información del equipo (gestor de paquetes, discos y su estado de salud), permite
**añadir y ejecutar scripts** desde una interfaz gráfica e incluye de serie la
herramienta `ulxCheckDirtyDrives` para reparar particiones NTFS "dirty" dejadas por
Windows.

- Multiidioma: **English**, **Español**, **中文**, **Português** (inglés por defecto).
- Tema **claro** y **oscuro**, con persistencia entre sesiones.
- Los scripts y ajustes se guardan en SQLite (`~/.config/ulx/LinuxHelm/lh.db`), así que
  sobreviven a reinicios.

---

## Índice

- [Características](#características)
- [Requisitos](#requisitos)
- [Compilar y ejecutar](#compilar-y-ejecutar)
- [Cómo usar la aplicación](#cómo-usar-la-aplicación)
- [Herramienta incluida: ulxCheckDirtyDrives](#herramienta-incluida-ulxcheckdirtydrives)
- [Apartado técnico](#apartado-técnico)
- [Estructura del proyecto](#estructura-del-proyecto)
- [Tests](#tests)

---

## Características

| Pantalla | Qué ofrece |
|---|---|
| **Principal** | Menú de acceso con el logo de la app. |
| **Información** | Gestor de paquetes detectado (con enlace a su wiki) y lista de discos con tamaño, uso y estado de salud (comprobación rápida y profunda). |
| **Herramientas** | Lanzador de scripts: añade `.sh`, `.jar`, `.py`, `.AppImage`, binarios… y ejecútalos en una terminal del sistema. |
| **Configuración** | Selector de idioma y tema (claro/oscuro), guardados al instante. |

Detalles útiles:

- **Los scripts no se mueven ni se copian**: solo se guarda la ruta que elegiste.
- Al ejecutar se abre una **terminal real** del escritorio: puedes responder prompts,
  escribir la contraseña de `sudo` y ver la salida en vivo.
- La terminal **se queda abierta al terminar** ("Pulsa una tecla para cerrar") para que
  puedas leer el resultado, y avisa si el script salió con error.
- La app detecta si un script **ya no existe** o **no tiene permiso de ejecución (+x)**
  y te avisa antes de que falle.
- Las herramientas incluidas de serie **no se pueden borrar** desde la interfaz.

---

## Requisitos

**Para usar la app** (Linux):

- Cualquier escritorio con un emulador de terminal instalado
  (`gnome-terminal`, `konsole`, `xfce4-terminal`, `kitty`, `xterm`…; se prueba primero
  `x-terminal-emulator`, el estándar de Debian/Ubuntu).
- `lsblk` (de `util-linux`, ya viene en casi todas las distros) para la pantalla de
  Información.
- Opcional, para la comprobación **profunda** de discos: `pkexec` (policykit) y las
  herramientas del sistema de archivos correspondientes (`e2fsprogs`, `ntfs-3g`,
  `dosfstools`, `btrfs-progs`).
- Para la herramienta incluida `ulxCheckDirtyDrives`: paquete **`ntfs-3g`**
  (`sudo apt install ntfs-3g`).

**Para compilar desde el código**:

- JDK 17 o superior (el plugin de toolchains de Gradle lo resuelve automáticamente si
  falta).
- No hace falta instalar Kotlin ni Compose: todo viene por Gradle.

---

## Compilar y ejecutar

```bash
# Ejecución normal
./gradlew :desktopApp:run

# Hot reload (recarga al guardar, ideal para desarrollo)
./gradlew :desktopApp:hotRun --auto

# Tests de escritorio
./gradlew :shared:jvmTest

# Empaquetar para el sistema actual (en Linux genera un .deb)
./gradlew :desktopApp:packageDistributionForCurrentOS
```

Los formatos de empaquetado configurados son `.deb`, `.dmg` y `.msi`
(`desktopApp/build.gradle.kts`). El paquete es autocontenido: trae su propio JRE y las
herramientas incluidas viajan **dentro del JAR** (ver [Apartado técnico](#apartado-técnico)).

---

## Cómo usar la aplicación

### Pantalla principal

Al abrir la app ves el menú con tres botones: **Información**, **Herramientas** y
**Configuración**. Todas las pantallas secundarias tienen un botón **Volver** arriba a
la izquierda.

### Información

- Arriba se muestra el **gestor de paquetes** detectado (apt, dnf, pacman, zypper o
  emerge). El botón **Ver sitio** abre su documentación en el navegador.
- Debajo, el expander **"Unidades de almacenamiento detectadas"** lista las particiones
  reales del equipo (estilo Dolphin: oculta la partición EFI, swap, RAID y las
  particiones padre, mostrando solo las hijas). Para cada una verás:
  - Nombre (la etiqueta del volumen, o "Unidad interna/extraíble de X GiB" si no tiene).
  - Punto de montaje, tamaño total y espacio usado.
  - Un indicador de estado con semáforo: verde (sin errores), amarillo (aviso, p. ej.
    montada en solo lectura), gris (sin comprobar) o rojo (errores).
- **Comprobar a fondo**: lanza una comprobación **de solo lectura** con privilegios
  (aparecerá el diálogo gráfico de contraseña de `pkexec`). Según el sistema de archivos:
  - `ext4/ext3/ext2`: lee el estado del journal (`dumpe2fs -h`).
  - `ntfs`: `ntfs-3g.probe --readonly` (detecta dirty e hibernación de Windows).
  - `vfat`: `fsck.fat -n` (detecta el dirty bit).
  - `btrfs`: `btrfs device stats` (contadores de errores de E/S).
- La comprobación **rápida** (sin root) ya se hace al abrir la pantalla: mira los
  contadores de errores del kernel en `/sys/fs/...` para ext4/btrfs y si alguna unidad
  está montada en solo lectura. Si no puede verificar nada, lo pone como "Sin comprobar"
  (nunca marca como correcto algo que no pudo comprobar).

### Herramientas

Esta es la pantalla central de la app: tu lanzador de scripts.

1. Pulsa **Añadir script** y elige el archivo con el diálogo del sistema.
   Se admiten `.sh`, `.jar`, `.py`, `.AppImage`, `.run`, `.bin` y binarios sin extensión
   (cambia el filtro del diálogo a "Todos los archivos").
2. En el diálogo escribe el **Nombre** (viene rellenado con el del archivo) y, si
   quieres, un **Sitio web** opcional (p. ej. la página del proyecto).
3. En la lista pulsas **Ejecutar**: se abre una ventana de terminal con el script
   funcionando. Cuando termine, la terminal se queda abierta para que leas la salida;
   si el script falló, muestra el código de salida.
4. **Ver sitio** (solo si pusiste web) abre la página en el navegador.
5. La **papelera** quita el script de la lista. Ojo: borra el registro, **no** el archivo
   en disco; puedes volver a añadirlo cuando quieras.

Cómo se lanza cada tipo de archivo:

| Archivo | Cómo se ejecuta | ¿Necesita +x? |
|---|---|---|
| `.sh` | `bash archivo.sh` | No |
| `.jar` | `java -jar archivo.jar` | No |
| `.py` | `python3 archivo.py` | No |
| Otros (binarios, `.AppImage`…) | Se lanzan directamente | **Sí** |

La app trabaja con el **directorio de trabajo del propio script** (su carpeta), así que
los scripts que usan rutas relativas funcionan igual que si los lanzaras a mano.

Si un archivo desaparece de disco o le falta el permiso +x, la fila muestra un aviso en
rojo y el botón **Ejecutar** queda deshabilitado.

### Configuración

Dos desplegables dentro de una tarjeta:

- **Tema**: Claro / Oscuro. El cambio es **en vivo** (la UI se repinta al instante) y
  persiste.
- **Idioma**: English / Español / 中文 / Português. Se aplica al instante en todos los
  textos de la interfaz y también persiste.

Ambos ajustes se guardan en la base de datos; la próxima vez que abras la app ya saldrán
aplicados (sin parpadeo del tema/idioma por defecto).

---

## Herramienta incluida: ulxCheckDirtyDrives

`ulxCheckDirtyDrives` viene **de serie** en la pestaña Herramientas (no se puede borrar).
Comprueba y repara particiones NTFS marcadas como *dirty* o hibernadas por Windows
(`hiberfil.sys` / arranque rápido). Se ejecuta en la terminal con permisos de
administrador (el propio script pide `sudo`).

Documentación completa en
[`ulxTools/ulxCheckDirtyDrives/README.md`](./ulxTools/ulxCheckDirtyDrives/README.md)
([English](./ulxTools/ulxCheckDirtyDrives/README_EN.md)).

---

## Apartado técnico

### Stack

| Componente | Versión / elección |
|---|---|
| Kotlin | 2.4.20 |
| Compose Multiplatform | 1.12.1 |
| Material 3 | 1.12.0-alpha03 |
| kotlinx-coroutines | 1.11.0 (con `coroutines-swing` en desktop) |
| SQLite | `org.xerial:sqlite-jdbc` 3.47.1.0 (JDBC puro) |
| Arquitectura | Módulo `shared` (KMP, solo target JVM) + `desktopApp` (JVM) |

Aunque el proyecto es Kotlin Multiplatform, hoy solo existe el target **JVM/Desktop**:
`shared` contiene el código común y `desktopApp` el punto de entrada.

### Estructura del proyecto

```
LinuxHelm/
├── desktopApp/            # Punto de entrada JVM (main.kt) y empaquetado nativo
├── shared/
│   └── src/commonMain/kotlin/com/example/linuxhelm/
│       ├── App.kt                     # Raíz: tema + navegación animada
│       ├── ui/
│       │   ├── compose/
│       │   │   ├── Screen.kt              # enum Pantalla + BotonVolver
│       │   │   ├── ScreenPrincipal.kt     # Menú principal
│       │   │   ├── ScreenInfo.kt          # Discos, paquetes, chequeos
│       │   │   ├── ScreenTools.kt         # Lanzador de scripts
│       │   │   ├── ScreenConfiguracion.kt # Idioma y tema
│       │   │   └── Test.kt                # Pantalla de pruebas (fuera del flujo)
│       │   ├── ulxComponentes/        # LanguageSelector, ThemeSelector
│       │   └── ulxLib/                # Infraestructura (ver abajo)
│       └── composeResources/          # logo, etc.
├── ulxTools/              # Herramientas bash incluidas (hoy: ulxCheckDirtyDrives)
└── gradle/                # Version catalog (libs.versions.toml)
```

### Navegación

El estado de navegación vive en la raíz (`App.kt`) como un `mutableStateOf` del enum
`Pantalla { Principal, Info, Tools, Configuracion }`. `AnimatedContent` transiciona
entre pantallas (entra desde la derecha con fade, sale hacia la izquierda). La pantalla
principal recibe `onNavigate`, las secundarias solo `onVolver`: así ninguna pantalla
secundaria sabe a qué pantalla se devuelve, el flujo se controla todo desde la raíz.

### Persistencia: SQLite

Todo el estado persistente usa **una única BD SQLite** en
`~/.config/ulx/LinuxHelm/lh.db` (estándar XDG para configuración), gestionada por el
objeto `ulxDB` (JDBC puro, todas las llamadas bloqueantes → siempre desde `Dispatchers.IO`).

Se eligió SQLite en lugar de JSON por la **escritura atómica** (no puede quedarse un
fichero corrupto a mitad si la app se cierra en mal momento) y por tener una única
fuente de verdad.

Tablas:

```sql
scripts (id, name, description, website, path UNIQUE, deletable)
settings (key PRIMARY KEY, value)
```

- **`scripts`**: los scripts del usuario (`deletable = 1`) y las herramientas de serie
  (`deletable = 0`, la UI no muestra papelera y el `DELETE` propio también lo comprueba,
  doble protección). `path` con `UNIQUE` + `ON CONFLICT DO UPDATE` hace que "re-añadir"
  el mismo archivo sirva para **editar** nombre/web sin pantalla de edición aparte.
- **`settings`**: clave-valor con ajustes (`lang`, `theme`). Se guardan **claves
  estables** (`"dark"`, `"Español"`…), nunca etiquetas traducidas, para que cambiar de
  idioma no rompa la interpretación del valor guardado.
- Hay **migración manual** de columnas (`PRAGMA table_info` + `ALTER TABLE`) porque
  `CREATE TABLE IF NOT EXISTS` no añade columnas a tablas existentes.

### Herramientas incluidas (bundled tools)

`shared/build.gradle.kts` copia los ficheros de `ulxTools/` **dentro del JAR** como
recursos (`jvmProcessResources`). En runtime, `ulxDB.seedBundledTools()`:

1. Extrae cada recurso a `~/.config/ulx/LinuxHelm/tools/`.
2. Le da permiso de ejecución.
3. Lo registra en la BD con `deletable = 0` (idempotente, con upsert).

Se re-extrae **en cada arranque**, así que al actualizar la app la herramienta queda
siempre en su última versión. Al viajar en el classpath, funciona igual con `run`, `.deb`,
AppImage o tar.gz, sin necesitar la carpeta `ulxTools/` en disco.

### Ejecución de scripts

`ulxScripts.kt` construye el comando según la extensión (`bash` / `java -jar` /
`python3` / ejecución directa) y lo lanza en una **terminal del sistema** con
`ProcessBuilder`. ¿Por qué terminal y no un proceso en segundo plano? Para que el
usuario pueda interactuar: prompts, stdin y la contraseña de `sudo` (que pide la propia
terminal).

- Lista de terminales conocidas de mayor a menor probabilidad, con dos estilos de
  invocación: quienes reciben el comando como cadena tras `-e` (gnome-terminal,
  xfce4-terminal…) y quienes reciben argv separado (konsole, xterm, kitty…).
- Se busca en el `PATH` sin lanzar subprocess; si no hay ninguna, la UI avisa.
- El comando interior encadena: lanzar → capturar `$?` → mostrar código de salida si
  hubo error → `read -n1` para mantener la terminal abierta. Toda la línea va con
  `shellQuote` (comillas simples) para que rutas con espacios o `$` no se rompan.
- Directorio de trabajo = carpeta del archivo.

### Tema y colores

- `ulxColor`: **todos** los colores de la app como variables respaldadas por
  `mutableStateOf`, de modo que son observables en composición: al cambiar de tema la UI
  se repinta sola, sin reiniciar la ventana. Las pantallas nunca crean un `Color(...)` a
  mano.
- `ulxColorManager`: la **única fuente de verdad** de los valores de cada paleta
  (claro/oscuro) y traductor a `ColorScheme` de Material 3, para que botones, campos y
  diálogos usen la paleta de la app en vez del morado por defecto de M3.
- Los colores semánticos (`exito`, `aviso`, `error`, `desconocido`) alimentan los
  semáforos de salud de los discos.

### Internacionalización

`ulxLang` contiene todas las cadenas como variables con defecto en inglés;
`ulxLangManager` las reasigna con `setSpanish()`, `setChinese()`, `setPortuguese()`…
Las **claves de guardado** son los literales fijos del selector
(`"English"`, `"Español"`, `"中文 (zhōngwén)"`, `"Português"`), que nunca se traducen.

### Ajustes al arranque (sin parpadeo)

`main.kt` llama a `ulxSettings.loadAndApply()` **antes** de crear la ventana: la BD se
lee una vez al arrancar y queda en caché en memoria (ninguna query SQLite dentro de una
composición). Al cambiar ajustes en Configuración, `saveLang`/`saveTheme` actualizan
caché + aplican + persisten en IO.

### Información del sistema

- `getDiskInfo()` ejecuta `lsblk -b -P` y parsea la salida (incluyendo los escapes
  `\x20` de lsblk), filtra partition tables (EFI por GUID, swap, RAID, LVM, `/boot`…)
  y calcula el uso real con `File.totalSpace`/`usableSpace` cuando la unidad está
  montada.
- `quickCheck` (sin root): contadores de errores del kernel vía
  `/sys/fs/ext4/<dev>/errors_count` y `/sys/fs/btrfs/<uuid>/devinfo/*/error_stats`, más
  detección de montaje en solo lectura (`/proc/self/mounts`).
- `deepCheck` (con `pkexec`, solo lectura): comando según el FS, interpretando códigos
  de salida y texto (p. ej. los códigos 3/4/5 de `ntfs-3g.probe` = dañado / hibernado /
  dirty).

---

## Tests

```bash
./gradlew :shared:jvmTest
```

Hay tests de ejemplo en `shared/src/commonTest` y `shared/src/jvmTest`
(`SharedCommonTest`, `SharedLogicDesktopTest`).
