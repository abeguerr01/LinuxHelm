> ### [Documentation in english](README_EN.md)

# ulxTarGzInstaller

Aplicación gráfica (Tkinter) para **instalar paquetes `.tar.gz` en `/opt`** de forma segura,
creando además un lanzador de menú para la aplicación instalada.

Muchas aplicaciones para Linux se distribuyen como `.tar.gz` "portable": lo descomprimes y
funciona, pero luego no aparece en el menú, no tiene icono y cada uno lo descomprime donde
le da la gana. Esta herramienta hace el trabajo completo: **analiza el paquete, lo instala en
`/opt` con permisos de administrador, elige el ejecutable, busca el icono y escribe el
`.desktop`** para que la app salga en el menú con su icono.

> Nombre anterior de la carpeta: `tar.gz Installer`

---

## ¿Qué hace?

1. **Selecciona** un `.tar.gz` (botón `Search`) y lo **analiza sin privilegios**
   (`tar tzf`, nada de root todavía).
2. **Decide dónde se instalará** y te lo enseña antes de tocar nada:
   `✓ Will install as /opt/myapp-1.0 [directo]`.
3. **Instala con `pkexec`** (diálogo de contraseña de root) usando flags que impiden que el
   paquete herede propietarios ni setuid.
4. **Detecta si ya está instalado** y pregunta si quieres sobrescribirlo.
5. **Ofrece crear el lanzador**: diálogo con el ejecutable preseleccionado, casilla
   *Run in a terminal* y botones `Create launcher` / `Skip`.
6. Después puedes **ejecutar** la app instalada (`Execute`) o **deshacer** la instalación
   (`return`, borra `/opt/<app>` y su `.desktop`).

---

## ¿Cómo funciona?

### Análisis del paquete (sin privilegios)

`analizar()` lista el contenido con `tar tzf` y clasifica la estructura:

| Caso | Modo | Resultado |
|---|---|---|
| Una sola carpeta en la raíz (`myapp-1.0/...`) | `directo` | se instala en `/opt/myapp-1.0` |
| Varios directorios o ficheros sueltos (`bin/`, `lib/`…) | `contenida` | se crea una sola carpeta con el nombre del `.tar.gz` |
| Nombre del tarball = raíz de sistema (`bin.tar.gz`, `usr.tar.gz`…) | `contenida` | se le añade `_app` → `/opt/bin_app` (nunca `/opt/bin`) |
| Archivo vacío o rutas raras | — | `✗` y no se instala |

Además comprueba si `/opt/<app>` **ya existe** (→ aviso `Already installed`) y guarda el
comando exacto de extracción que ejecutará después.

### Extracción segura (la parte importante)

La extracción va como **root** (`pkexec tar`), así que el paquete puede intentar escaparse.
La matriz de seguridad, verificada con tests (`tests/test_seguridad.py`):

| Ataque del `.tar.gz` | Resultado |
|---|---|
| Rutas con `../` (escribir fuera del destino) | ✗ GNU tar lo bloquea |
| Rutas absolutas (`/etc/...`) | ✗ GNU tar lo bloquea |
| Ficheros setuid / propietarios heredados | ✗ `--no-same-owner --no-same-permissions` |
| Symlinks que escapan (relativos o absolutos) | ✗ GNU tar los rechaza (`exit=2`) |
| Tarball llamado como raíz del sistema (`bin.tar.gz`) | ✗ nunca crea `/opt/bin` (sale `bin_app`) |
| Quedarse en `/opt` con ficheros sueltos | ✗ siempre acaba con **una sola carpeta** |

### Elección del ejecutable

No se "adivina": `buscar_ejecutables()` recorre lo instalado y ordena por prioridad:

| Prioridad | Dónde | Ejemplo |
|---|---|---|
| 1ª | ejecutable en la **raíz** de la app | `/opt/myapp/myapp` |
| 2ª | dentro de `bin/` | `/opt/myapp/bin/helper` |
| última | dentro de `lib/` | `/opt/myapp/lib/interno` |

Se **excluyen** las `.so` y los ficheros sin bit de ejecución. El diálogo de lanzador
muestra la lista y preselecciona el primero (el de la raíz, el más probable).

### Icono

`logo_del_app()` busca, por este orden:

1. `share/icons/hicolor/256x256/apps/<app>.png` (el estándar real de Linux)
2. Otros tamaños de `hicolor` (256 antes que 16, `svg` escalable antes que `png`)
3. Logos en la raíz del paquete (`logo.png`, `<app>.png`)
4. Si no hay ninguno → se usa el genérico `TarGzLogo.png`

### Lanzador `.desktop`

Se escribe en `~/.local/share/applications/`:

- Si el paquete **trae su propio `.desktop`** (en `share/applications/`), se conservan sus
  `Name`, `Comment`, `Categories` y **los argumentos de `Exec`** (`miapp %U` → se respeta),
  y solo se arreglan `Exec` (ruta absoluta al binario elegido), `Icon` y `Terminal`.
- Si no trae ninguno, se genera uno propio con `Categories=HelmTool;`.
- Si la ruta lleva espacios, `Exec=` se entrecomilla.
- Al **reinstalar**, el `.desktop` se actualiza (no se queda el antiguo).

### Interfaz que no se congela

Los trabajos pesados (`tar`, `pkexec`) corren **en un hilo aparte**; la cola de resultados
vuelve al hilo de Tk y los botones se bloquean mientras dura la operación (no hay dobles
`pkexec` ni ventana congelada). Los colores: fondo gris claro, azul para títulos, botones y
acentos; mensajes con `✓` verde, `✗` rojo y `⏳` ámbar.

---

## Requisitos

- Linux con **entorno gráfico** (X11/Wayland) y `glibc ≥ 2.17` (cualquier distro de los
  últimos ~12 años; ⚠️ no vale Alpine/musl).
- **`tar`** (GNU tar) y **`pkexec`** (policykit) — vienen en todo escritorio Linux.
- Para ejecutar el binario: **nada más** (Python y Tk van dentro del propio binario).
- Solo si prefieres lanzarlo con Python: `python3` con `tkinter`
  (`sudo apt install python3-tk`).

---

## Uso

### Con el binario (recomendado para repartir)

```bash
./build.sh                      # compila (PyInstaller, solo la 1ª vez tarda)
./dist/ulxTarGzInstaller        # arranca la GUI (UN solo fichero, autocontenido)
```

Para dárselo a alguien, basta con **el ejecutable solo** (Python, Tk y los logos
van embebidos dentro; no hace falta `_internal/` ni nada más):

```bash
cp dist/ulxTarGzInstaller / donde-quieras/
```

### Con el código fuente

```bash
python3 main.py
```

### Botones

| Botón | Efecto |
|---|---|
| `Search` | Elige el `.tar.gz`, lo analiza e instala (pide contraseña) |
| `Execute` | Ejecuta la aplicación instalada |
| `return` | Desinstala: borra `/opt/<app>` y su lanzador (pide contraseña) |
| `Create launcher` / `Skip` | En el diálogo: crea el `.desktop` o lo omite |

### Ejemplo de sesión

```
[Search]  →  seleccionas myapp-1.0.tar.gz

⏳ Checking the package...
✓ Will install as /opt/myapp-1.0 [directo]

   (diálogo de pkexec: contraseña de administrador)

⏳ Extracting, please wait...
✓ Extracted in /opt/myapp-1.0

   (diálogo de lanzador: ejecutable myapp, Terminal ☐)

✓ Launcher created: myapp.desktop
```

Cuando el paquete no es utilizable:

```
✗ Not a valid .tar.gz: ...
✗ The archive is empty
✗ Cannot tell where to install this archive
```

Y si ya estaba instalado:

```
⚠ Overwrite it? (old files may remain)
```

---

## Comportamiento y notas

- **Root solo en el momento exacto**: analizar, ver mensajes y abrir el diálogo no piden
  contraseña; solo `pkexec` al instalar y al deshacer.
- **Sobrescribir mezcla versiones**: los ficheros nuevos se superponen a los viejos, pero
  los que la versión anterior tenía de más **pueden quedar** (el propio diálogo lo advierte:
  *old files may remain*). Si quieres una reinstalación limpia, desinstala antes con
  `return`.
- **El `.desktop` apunta a rutas absolutas**: `Exec=` y `Icon=` referencian
  `/opt/<app>/...` y la carpeta de la herramienta. **Mueve la carpeta de la herramienta
  ANTES de crear lanzadores**; si la mueves después, esos lanzadores quedan con el icono
  roto.
- **Sin icono no hay problema**: se usa el genérico `TarGzLogo.png`.
- **La GUI está en inglés** (textos de la interfaz), los mensajes internos del análisis
  también.
- **Tests**: `tests/` dentro de la carpeta — 63 comprobaciones en 4 suites
  (`test_instalador`, `test_seguridad`, `test_dialogo`, `test_no_congelamiento`), sin root
  (usan temporales), más `capturar.py` para capturas de la interfaz:

  ```bash
  cd tests
  python3 test_instalador.py       # 37 ✓
  python3 test_seguridad.py        # 5 ✓
  python3 test_dialogo.py          # 15 ✓
  python3 test_no_congelamiento.py # 6 ✓
  ```

---

## Consejos

1. **Mueve primero la carpeta, instala después** (`/opt` o `~/.local/opt`), y solo entonces
   crea lanzadores con la herramienta.

2. **Para repartir la app a un cliente**: `build.sh` ya te imprime la orden exacta
   (`tar czf …`) — el cliente solo descomprime y ejecuta, sin instalar Python ni nada.

3. **Quieres cambiar el logo** de la ventana/icono: sustituye `AppLogo.png` o
   `TarGzLogo.png` en el fuente y recompila (`./build.sh`) — con `--onefile` los
   logos van embebidos en el binario, ya no son ficheros sueltos que se puedan
   cambiar sin recompilar.

4. **Si una reinstalación deja restos**, desinstala con `return` y vuelve a instalar: es la
   forma limpia.

5. **Lanzamiento cómodo desde terminal** (symlink, probado que funciona):

   ```bash
   ln -s ~/.local/opt/ulxTarGzInstaller/ulxTarGzInstaller ~/.local/bin/
   ```

---

## Estructura de la herramienta

| Archivo / sección | Función |
|---|---|
| `main.py` (830 líneas) | Toda la aplicación |
| ├ `analizar()` | Clasifica el `.tar.gz` (directo/contenida), decide destino y comando seguro |
| ├ `buscar_ejecutables()` / `binario()` | Encuentra el binario a lanzar (raíz → `bin/` → `lib/`) |
| ├ `logo_del_app()` | Busca el icono (hicolor → raíz → genérico) |
| ├ `escribir_desktop()` | Escribe/actualiza el `.desktop` conservando lo del paquete |
| ├ `dialogo_lanzador()` | Diálogo: ejecutable, Terminal, `Create launcher` / `Skip` |
| ├ `_trabajo_*()` | Extracción/abrir/deshacer — van en hilo (`_en_hilo`) |
| ├ `_drenar_cola()` / `_programar()` | Devuelven los resultados al hilo de Tk (no congela) |
| └ `crear_ventana()` | GUI: tarjeta, botones, paleta gris/azul |
| `build.sh` (100 líneas) | Compila el binario con PyInstaller (onefile, autocontenido) usando el Python del sistema |
| `tests/` | 63 comprobaciones (instalador, seguridad, diálogo, no-congelamiento) + `capturar.py` |
| `AppLogo.png` / `TarGzLogo.png` | Logo de la ventana y logo genérico (van embebidos en el binario) |
