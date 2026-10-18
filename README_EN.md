> ### [Documentación en español](README.md)

# LinuxHelm

**LinuxHelm** is a desktop application for Linux built with **Kotlin** and
**Compose Multiplatform**. It works as a system utilities panel: it shows information
about your machine (package manager, disks and their health status), lets you
**add and run scripts** from a graphical interface, and ships with the
`ulxCheckDirtyDrives` tool to fix NTFS partitions left "dirty" by Windows.

- Multilingual: **English**, **Español**, **中文**, **Português** (English by default).
- **Light** and **dark** theme, persisted between sessions.
- Scripts and settings are stored in SQLite (`~/.config/ulx/LinuxHelm/lh.db`), so they
  survive restarts.

---

## Contents

- [Features](#features)
- [Requirements](#requirements)
- [Building and running](#building-and-running)
- [How to use the application](#how-to-use-the-application)
- [Bundled tool: ulxCheckDirtyDrives](#bundled-tool-ulxcheckdirtydrives)
- [Technical section](#technical-section)
- [Project structure](#project-structure)
- [Tests](#tests)

---

## Features

| Screen | What it offers |
|---|---|
| **Home** | Menu with the app logo and access to every screen. |
| **Information** | Detected package manager (with a link to its wiki) and a disk list with size, usage and health status (quick and deep check). |
| **Tools** | Script launcher: add `.sh`, `.jar`, `.py`, `.AppImage`, binaries… and run them in a system terminal. |
| **Settings** | Language and theme (light/dark) selectors, saved instantly. |

Useful details:

- **Scripts are never moved or copied**: only the path you picked is stored.
- Running one opens a **real terminal window**: you can answer prompts, type your
  `sudo` password and watch the output live.
- The terminal **stays open after the script finishes** ("Press any key to close") so
  you can read the result, and reports the exit code if it failed.
- The app detects when a script **no longer exists** or **lacks execute permission
  (+x)** and warns you before it fails.
- Bundled tools **cannot be deleted** from the interface.

---

## Requirements

**To use the app** (Linux):

- Any desktop with a terminal emulator installed
  (`gnome-terminal`, `konsole`, `xfce4-terminal`, `kitty`, `xterm`…; `x-terminal-emulator`,
  the Debian/Ubuntu standard, is tried first).
- `lsblk` (from `util-linux`, preinstalled on most distros) for the Information screen.
- Optional, for the **deep** disk check: `pkexec` (policykit) and the matching
  filesystem tools (`e2fsprogs`, `ntfs-3g`, `dosfstools`, `btrfs-progs`).
- For the bundled `ulxCheckDirtyDrives` tool: the **`ntfs-3g`** package
  (`sudo apt install ntfs-3g`).

**To build from source**:

- JDK 17 or newer (Gradle's toolchain plugin resolves it automatically if missing).
- No need to install Kotlin or Compose: everything comes through Gradle.

---

## Building and running

```bash
# Normal run
./gradlew :desktopApp:run

# Hot reload (reloads on save, ideal for development)
./gradlew :desktopApp:hotRun --auto

# Desktop tests
./gradlew :shared:jvmTest

# Package for the current OS (produces a .deb on Linux)
./gradlew :desktopApp:packageDistributionForCurrentOS
```

Configured packaging formats are `.deb`, `.dmg` and `.msi`
(`desktopApp/build.gradle.kts`). The package is self-contained: it ships its own JRE and
the bundled tools travel **inside the JAR** (see [Technical section](#technical-section)).

---

## How to use the application

### Home screen

On launch you see the menu with three buttons: **Information**, **Tools** and
**Settings**. Every secondary screen has a **Back** button at the top left.

### Information

- At the top is the detected **package manager** (apt, dnf, pacman, zypper or emerge).
  The **Check site** button opens its documentation in your browser.
- Below, the **"Detected storage drives"** expander lists the real partitions of the
  machine (Dolphin-style: EFI, swap, RAID and parent partitions are hidden, only children
  are shown). For each drive you get:
  - Name (the volume label, or "Internal/removable drive of X GiB" if unlabeled).
  - Mount point, total size and used space.
  - A traffic-light status indicator: green (no errors), yellow (warning, e.g. mounted
    read-only), grey (unchecked) or red (errors).
- **Deep check**: runs a **read-only** check with elevated privileges (the `pkexec`
  password dialog will appear). Depending on the filesystem:
  - `ext4/ext3/ext2`: reads the journal state (`dumpe2fs -h`).
  - `ntfs`: `ntfs-3g.probe --readonly` (detects dirty flag and Windows hibernation).
  - `vfat`: `fsck.fat -n` (detects the dirty bit).
  - `btrfs`: `btrfs device stats` (I/O error counters).
- The **quick** check (no root) already runs when the screen opens: it reads the kernel
  error counters in `/sys/fs/...` for ext4/btrfs and detects drives mounted read-only.
  When it cannot verify anything, it reports "Unchecked" (it never claims something is
  fine without having verified it).

### Tools

This is the heart of the app: your script launcher.

1. Click **Add script** and pick the file with the system dialog.
   Supported: `.sh`, `.jar`, `.py`, `.AppImage`, `.run`, `.bin` and extensionless
   binaries (switch the dialog filter to "All files").
2. In the dialog, type the **Name** (pre-filled with the file name) and, optionally, a
   **Website** (e.g. the project's page).
3. In the list, click **Run**: a terminal window opens with the script running. When it
   finishes, the terminal stays open so you can read the output; if the script failed,
   the exit code is shown.
4. **Check site** (only if you provided a URL) opens the page in the browser.
5. The **trash** removes the script from the list. Note: it deletes the record, **not**
   the file on disk; you can add it again anytime.

How each file type is launched:

| File | How it runs | Needs +x? |
|---|---|---|
| `.sh` | `bash file.sh` | No |
| `.jar` | `java -jar file.jar` | No |
| `.py` | `python3 file.py` | No |
| Others (binaries, `.AppImage`…) | Executed directly | **Yes** |

The app sets the **working directory to the script's own folder**, so scripts using
relative paths work exactly as if you launched them by hand.

If a file disappears from disk or lacks the +x permission, its row shows a red warning
and the **Run** button is disabled.

### Settings

Two dropdowns inside a card:

- **Theme**: Light / Dark. The change is **live** (the UI repaints instantly) and
  persisted.
- **Language**: English / Español / 中文 / Português. Applied instantly to every string
  in the interface, also persisted.

Both settings are stored in the database; the next time you open the app they are
already applied (no flash of the default theme/language).

---

## Bundled tool: ulxCheckDirtyDrives

`ulxCheckDirtyDrives` comes **by default** in the Tools tab (it cannot be deleted).
It checks and repairs NTFS partitions flagged as *dirty* or hibernated by Windows
(`hiberfil.sys` / fast startup). It runs in the terminal with administrator rights
(the script itself asks for `sudo`).

Full documentation in
[`ulxTools/ulxCheckDirtyDrives/README_EN.md`](./ulxTools/ulxCheckDirtyDrives/README_EN.md)
([Español](./ulxTools/ulxCheckDirtyDrives/README.md)).

---

## Technical section

### Stack

| Component | Version / choice |
|---|---|
| Kotlin | 2.4.20 |
| Compose Multiplatform | 1.12.1 |
| Material 3 | 1.12.0-alpha03 |
| kotlinx-coroutines | 1.11.0 (with `coroutines-swing` on desktop) |
| SQLite | `org.xerial:sqlite-jdbc` 3.47.1.0 (pure JDBC) |
| Architecture | `shared` module (KMP, JVM-only target) + `desktopApp` (JVM) |

Although the project is Kotlin Multiplatform, only the **JVM/Desktop** target exists
today: `shared` holds the common code and `desktopApp` the entry point.

### Project structure

```
LinuxHelm/
├── desktopApp/            # JVM entry point (main.kt) and native packaging
├── shared/
│   └── src/commonMain/kotlin/com/example/linuxhelm/
│       ├── App.kt                     # Root: theme + animated navigation
│       ├── ui/
│       │   ├── compose/
│       │   │   ├── Screen.kt              # Pantalla enum + BotonVolver
│       │   │   ├── ScreenPrincipal.kt     # Home menu
│       │   │   ├── ScreenInfo.kt          # Disks, packages, health checks
│       │   │   ├── ScreenTools.kt         # Script launcher
│       │   │   ├── ScreenConfiguracion.kt # Language and theme
│       │   │   └── Test.kt                # Test screen (outside the flow)
│       │   ├── ulxComponentes/        # LanguageSelector, ThemeSelector
│       │   └── ulxLib/                # Infrastructure (see below)
│       └── composeResources/          # logo, etc.
├── ulxTools/              # Bundled bash tools (currently: ulxCheckDirtyDrives)
└── gradle/                # Version catalog (libs.versions.toml)
```

### Navigation

The navigation state lives at the root (`App.kt`) as a `mutableStateOf` of the
`Pantalla { Principal, Info, Tools, Configuracion }` enum. `AnimatedContent` transitions
between screens (the new one slides in from the right with a fade, the old one slides
left). The home screen receives `onNavigate`, the secondary ones only `onVolver` (back):
so no secondary screen knows where it returns to, the whole flow is controlled from the
root.

### Persistence: SQLite

All persistent state uses a **single SQLite database** at
`~/.config/ulx/LinuxHelm/lh.db` (XDG config standard), managed by the `ulxDB` object
(pure JDBC, every call is blocking → always from `Dispatchers.IO`).

SQLite was chosen over JSON for **atomic writes** (no half-corrupted file if the app
closes at a bad moment) and for having a single source of truth.

Tables:

```sql
scripts (id, name, description, website, path UNIQUE, deletable)
settings (key PRIMARY KEY, value)
```

- **`scripts`**: user scripts (`deletable = 1`) and bundled tools (`deletable = 0`;
  the UI hides the trash and the `DELETE` itself checks it too, double protection).
  `path` with `UNIQUE` + `ON CONFLICT DO UPDATE` makes "re-adding" the same file serve
  as a way to **edit** name/URL without a separate edit screen.
- **`settings`**: key-value store for preferences (`lang`, `theme`). **Stable keys** are
  saved (`"dark"`, `"Español"`…), never translated labels, so switching language never
  breaks interpretation of the stored value.
- There is a manual **column migration** (`PRAGMA table_info` + `ALTER TABLE`) because
  `CREATE TABLE IF NOT EXISTS` does not add columns to existing tables.

### Bundled tools

`shared/build.gradle.kts` copies the files from `ulxTools/` **into the JAR** as
resources (`jvmProcessResources`). At runtime, `ulxDB.seedBundledTools()`:

1. Extracts each resource to `~/.config/ulx/LinuxHelm/tools/`.
2. Sets the execute permission.
3. Registers it in the database with `deletable = 0` (idempotent, via upsert).

It is re-extracted **on every launch**, so updating the app always leaves the tool at
its latest version. Traveling in the classpath, it works the same with `run`, a `.deb`,
an AppImage or a tar.gz, with no need for the `ulxTools/` folder on disk.

### Script execution

`ulxScripts.kt` builds the command according to the extension (`bash` / `java -jar` /
`python3` / direct execution) and launches it in a **system terminal** with
`ProcessBuilder`. Why a terminal instead of a background process? So the user can
interact: prompts, stdin and the `sudo` password (asked by the terminal itself).

- List of known terminals from most to least likely, with two invocation styles: those
  taking the command as a string after `-e` (gnome-terminal, xfce4-terminal…) and those
  taking separate argv (konsole, xterm, kitty…).
- The `PATH` is scanned without spawning subprocesses; if no terminal is found, the UI
  warns.
- The inner command chains: run → capture `$?` → print the exit code on failure →
  `read -n1` to keep the terminal open. The whole line is passed through `shellQuote`
  (single quotes) so paths with spaces or `$` don't break.
- Working directory = the file's folder.

### Theme and colors

- `ulxColor`: **all** app colors as variables backed by `mutableStateOf`, making them
  observable in composition: switching theme repaints the UI by itself, without
  restarting the window. Screens never create a `Color(...)` by hand.
- `ulxColorManager`: the **single source of truth** for each palette's values
  (light/dark) and translator to a Material 3 `ColorScheme`, so buttons, fields and
  dialogs use the app palette instead of M3's default purple.
- Semantic colors (`exito`/success, `aviso`/warning, `error`, `desconocido`/unknown)
  drive the disk health traffic lights.

### Internationalization

`ulxLang` holds every string as variables defaulting to English; `ulxLangManager`
reassigns them with `setSpanish()`, `setChinese()`, `setPortuguese()`… The **storage
keys** are the fixed selector literals (`"English"`, `"Español"`,
`"中文 (zhōngwén)"`, `"Português"`), which are never translated.

### Startup settings (no flicker)

`main.kt` calls `ulxSettings.loadAndApply()` **before** creating the window: the
database is read once at startup and cached in memory (no SQLite queries inside
compositions). When settings change on the Settings screen, `saveLang`/`saveTheme`
update the cache + apply + persist on IO.

### System information

- `getDiskInfo()` runs `lsblk -b -P` and parses the output (including lsblk's `\x20`
  escapes), filters partition tables (EFI by GUID, swap, RAID, LVM, `/boot`…), and
  computes real usage with `File.totalSpace`/`usableSpace` when the drive is mounted.
- `quickCheck` (no root): kernel error counters via
  `/sys/fs/ext4/<dev>/errors_count` and `/sys/fs/btrfs/<uuid>/devinfo/*/error_stats`,
  plus read-only mount detection (`/proc/self/mounts`).
- `deepCheck` (with `pkexec`, read-only): a filesystem-dependent command, interpreting
  both exit codes and output text (e.g. `ntfs-3g.probe` codes 3/4/5 = damaged /
  hibernated / dirty).

---

## Tests

```bash
./gradlew :shared:jvmTest
```

Sample tests live in `shared/src/commonTest` and `shared/src/jvmTest`
(`SharedCommonTest`, `SharedLogicDesktopTest`).
