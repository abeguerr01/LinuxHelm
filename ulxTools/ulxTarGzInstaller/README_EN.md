> ### [Documentacion en español](README.md)

# ulxTarGzInstaller

A Tkinter GUI application to **install `.tar.gz` packages into `/opt`** safely, also creating
a menu launcher for the installed application.

Many Linux applications ship as a "portable" `.tar.gz`: you extract it and it works, but then
it doesn't show up in the menu, has no icon, and everyone extracts it wherever they feel like.
This tool does the whole job: **it analyzes the package, installs it into `/opt` with
administrator rights, picks the executable, finds the icon and writes the `.desktop`** so the
app appears in the menu with its icon.

> Previous folder name: `tar.gz Installer`

---

## What does it do?

1. **Selects** a `.tar.gz` (the `Search` button) and **analyzes it without privileges**
   (`tar tzf`, no root yet).
2. **Decides where it will be installed** and shows it before touching anything:
   `✓ Will install as /opt/myapp-1.0 [directo]`.
3. **Installs with `pkexec`** (root password dialog) using flags that prevent the package
   from inheriting owners or setuid bits.
4. **Detects whether it is already installed** and asks if you want to overwrite it.
5. **Offers to create the launcher**: a dialog with the executable pre-selected, a
   *Run in a terminal* checkbox and `Create launcher` / `Skip` buttons.
6. Afterwards you can **run** the installed app (`Execute`) or **undo** the installation
   (`return`, deletes `/opt/<app>` and its `.desktop`).

---

## How does it work?

### Package analysis (no privileges)

`analizar()` lists the contents with `tar tzf` and classifies the structure:

| Case | Mode | Result |
|---|---|---|
| A single folder at the root (`myapp-1.0/...`) | `directo` | installs into `/opt/myapp-1.0` |
| Several directories or loose files (`bin/`, `lib/`…) | `contenida` | creates one folder named after the `.tar.gz` |
| Tarball named like a system root (`bin.tar.gz`, `usr.tar.gz`…) | `contenida` | `_app` is appended → `/opt/bin_app` (never `/opt/bin`) |
| Empty archive or odd paths | — | `✗` and nothing is installed |

It also checks whether `/opt/<app>` **already exists** (→ `Already installed` warning) and
stores the exact extraction command to run later.

### Safe extraction (the important part)

Extraction runs as **root** (`pkexec tar`), so the package may try to escape. The attack
matrix, verified by the tests (`tests/test_seguridad.py`):

| Attack inside the `.tar.gz` | Result |
|---|---|
| Paths with `../` (write outside the target) | ✗ GNU tar blocks it |
| Absolute paths (`/etc/...`) | ✗ GNU tar blocks it |
| setuid files / inherited owners | ✗ `--no-same-owner --no-same-permissions` |
| Escaping symlinks (relative or absolute) | ✗ GNU tar rejects them (`exit=2`) |
| Tarball named like a system root (`bin.tar.gz`) | ✗ never creates `/opt/bin` (yields `bin_app`) |
| Leaving loose files in `/opt` | ✗ always ends up with **exactly one folder** |

### Picking the executable

Nothing is guessed: `buscar_ejecutables()` walks the installed tree and ranks candidates:

| Priority | Where | Example |
|---|---|---|
| 1st | executable at the **root** of the app | `/opt/myapp/myapp` |
| 2nd | inside `bin/` | `/opt/myapp/bin/helper` |
| last | inside `lib/` | `/opt/myapp/lib/interno` |

`.so` files and files without the execute bit are **excluded**. The launcher dialog lists
the candidates and pre-selects the first one (the root one, the most likely).

### Icon

`logo_del_app()` searches in this order:

1. `share/icons/hicolor/256x256/apps/<app>.png` (the real Linux standard)
2. Other `hicolor` sizes (256 before 16, scalable `svg` before `png`)
3. Logos at the package root (`logo.png`, `<app>.png`)
4. None found → the generic `TarGzLogo.png` is used

### `.desktop` launcher

Written to `~/.local/share/applications/`:

- If the package **ships its own `.desktop`** (in `share/applications/`), its `Name`,
  `Comment`, `Categories` and the **`Exec` arguments** (`miapp %U` → respected) are kept,
  and only `Exec` (absolute path to the chosen binary), `Icon` and `Terminal` are fixed.
- Otherwise a new entry is generated with `Categories=HelmTool;`.
- If the path contains spaces, `Exec=` is quoted.
- On **reinstall**, the `.desktop` is updated (the old one is not left behind).

### A UI that never freezes

Heavy work (`tar`, `pkexec`) runs **in a separate thread**; results come back to the Tk
thread through a queue and the buttons are blocked while an operation runs (no double
`pkexec`, no frozen window). Colors: light gray background, blue for titles, buttons and
accents; messages use green `✓`, red `✗` and amber `⏳`.

---

## Requirements

- Linux with a **graphical session** (X11/Wayland) and `glibc ≥ 2.17` (any distro from the
  last ~12 years; ⚠️ Alpine/musl will not work).
- **`tar`** (GNU tar) and **`pkexec`** (policykit) — present in every Linux desktop.
- To run the binary: **nothing else** (Python and Tk are inside the binary itself).
- Only if you prefer running it from source: `python3` with `tkinter`
  (`sudo apt install python3-tk`).

---

## Usage

### With the binary (recommended for distribution)

```bash
./build.sh                      # builds (PyInstaller, slow the first time)
./dist/ulxTarGzInstaller        # starts the GUI (ONE self-contained file)
```

To hand it to someone, **the executable alone is enough** (Python, Tk and the
logos are embedded in it; no `_internal/` or anything else needed):

```bash
cp dist/ulxTarGzInstaller /anywhere/
```

### From source

```bash
python3 main.py
```

### Buttons

| Button | Effect |
|---|---|
| `Search` | Pick the `.tar.gz`, analyze it and install (asks for the password) |
| `Execute` | Runs the installed application |
| `return` | Uninstalls: deletes `/opt/<app>` and its launcher (asks for the password) |
| `Create launcher` / `Skip` | In the dialog: creates the `.desktop` or skips it |

### Example session

```
[Search]  →  you pick myapp-1.0.tar.gz

⏳ Checking the package...
✓ Will install as /opt/myapp-1.0 [directo]

   (pkexec dialog: administrator password)

⏳ Extracting, please wait...
✓ Extracted in /opt/myapp-1.0

   (launcher dialog: executable myapp, Terminal ☐)

✓ Launcher created: myapp.desktop
```

When the package is not usable:

```
✗ Not a valid .tar.gz: ...
✗ The archive is empty
✗ Cannot tell where to install this archive
```

And if it was already installed:

```
⚠ Overwrite it? (old files may remain)
```

---

## Behaviour and notes

- **Root only at the exact moment**: analyzing, reading messages and opening the dialogs
  need no password; only `pkexec` when installing and when undoing.
- **Overwriting mixes versions**: new files are laid over the old ones, but files that the
  previous version had and the new one doesn't **may remain** (the dialog itself warns:
  *old files may remain*). For a clean reinstall, uninstall first with `return`.
- **The `.desktop` points at absolute paths**: `Exec=` and `Icon=` reference
  `/opt/<app>/...` and the tool's own folder. **Move the tool's folder BEFORE creating
  launchers**; if you move it afterwards, those launchers end up with a broken icon.
- **No icon is fine**: the generic `TarGzLogo.png` is used.
- **The UI is in English** (interface texts), internal analysis messages too.
- **Tests**: `tests/` inside the folder — 63 checks across 4 suites (`test_instalador`,
  `test_seguridad`, `test_dialogo`, `test_no_congelamiento`), no root needed (they use
  temp folders), plus `capturar.py` for UI screenshots:

  ```bash
  cd tests
  python3 test_instalador.py       # 37 ✓
  python3 test_seguridad.py        # 5 ✓
  python3 test_dialogo.py          # 15 ✓
  python3 test_no_congelamiento.py # 6 ✓
  ```

---

## Tips

1. **Move the binary first, install second** (`/opt` or `~/.local/opt`), and only then
   create launchers with the tool.

2. **To distribute the app to a client**: just copy the single executable —
   it is fully self-contained, no Python or anything to install.

3. **Want to change the logo** (window/icon): replace `AppLogo.png` or
   `TarGzLogo.png` in the source and rebuild (`./build.sh`) — with `--onefile`
   the logos are embedded in the binary, no longer loose files you can swap
   without rebuilding.

4. **If a reinstall leaves leftovers**, uninstall with `return` and install again: that's
   the clean way.

5. **Comfortable launch from any terminal** (symlink, verified working):

   ```bash
   ln -s ~/.local/opt/ulxTarGzInstaller/ulxTarGzInstaller ~/.local/bin/
   ```

---

## Tool structure

| File / section | Purpose |
|---|---|
| `main.py` (830 lines) | The whole application |
| ├ `analizar()` | Classifies the `.tar.gz` (directo/contenida), decides target and safe command |
| ├ `buscar_ejecutables()` / `binario()` | Finds the binary to launch (root → `bin/` → `lib/`) |
| ├ `logo_del_app()` | Finds the icon (hicolor → root → generic) |
| ├ `escribir_desktop()` | Writes/updates the `.desktop`, keeping the package's own fields |
| ├ `dialogo_lanzador()` | Dialog: executable, Terminal, `Create launcher` / `Skip` |
| ├ `_trabajo_*()` | Extract/run/undo — they run in a thread (`_en_hilo`) |
| ├ `_drenar_cola()` / `_programar()` | Bring results back to the Tk thread (no freezing) |
| └ `crear_ventana()` | GUI: card, buttons, gray/blue palette |
| `build.sh` (100 lines) | Builds the binary with PyInstaller (onefile, self-contained) using the system Python |
| `tests/` | 63 checks (installer, security, dialog, no-freeze) + `capturar.py` |
| `AppLogo.png` / `TarGzLogo.png` | Window logo and generic logo (embedded in the binary) |
