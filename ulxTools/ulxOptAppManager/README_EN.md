# ulxOptAppManager

[Versión en español disponible aquí](README.md)

**ulxOptAppManager** is a clean, modern graphical application manager built with `tkinter` for Linux systems, designed to manage software installed in `/opt`. It inspects folders (populated via `ulxTarGzInstaller` or manual `.tar.gz` extractions) to list executables, associated `.desktop` launchers, icons, and precise folder sizes.

<p align="center">
  <img src="https://images.unsplash.com/photo-1629654297299-c8506221ca97?auto=format&fit=crop&w=900&q=80" alt="ulxOptAppManager Banner" width="700"/>
</p>

---

## ✨ Key Features

- **Deep Inspection:** Scans `/opt` to display folders, identified binaries, matching `.desktop` files, theme icons, and total disk footprint.
- **Direct Execution:** Launch selected applications securely as a standard user with a single click.
- **Missing Launcher Generator:** Automatically detects apps missing menu entries and offers to create them instantly in `~/.local/share/applications`.
- **Clean Uninstallation (`pkexec`):** Permanently removes application directories with root privileges through a single secure password prompt (`rm -rf`), automatically cleaning up orphaned menu shortcuts.
- **Multi-threaded Architecture:** Heavy operations like directory scanning and disk usage calculation run in background threads to keep the UI completely responsive.

---

## 🛠️ System Requirements

- **Operating System:** Linux (tested on X11/Wayland environments supporting `tkinter`).
- **Dependencies:**
  - Python 3.10 or higher.
  - Python Standard Library (`tkinter`, `subprocess`, `threading`, etc.).
  - `pkexec` utility (commonly available in Linux distributions for administrative permission prompts during uninstallation).

---

## 🚀 Quick Start

Clone the repository or download the main script to your machine and run it directly from your terminal:

```bash
python3 main.py
```