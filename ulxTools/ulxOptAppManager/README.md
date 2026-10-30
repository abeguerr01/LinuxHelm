# ulxOptAppManager

[English version available here](README_EN.md)

**ulxOptAppManager** es un gestor gráfico de aplicaciones instaladas en `/opt` diseñado para Linux con una interfaz limpia construida en `tkinter`. Permite inspeccionar, lanzar, crear accesos directos y desinstalar de forma limpia cualquier software alojado en la partición o directorio `/opt` (instalado ya sea mediante `ulxTarGzInstaller` o manualmente con archivos `.tar.gz`).

<p align="center">
  <img src="https://images.unsplash.com/photo-1629654297299-c8506221ca97?auto=format&fit=crop&w=900&q=80" alt="ulxOptAppManager Banner" width="700"/>
</p>

---

## ✨ Características Principales

- **Inspección Profunda:** Escanea la ruta `/opt` mostrando carpetas, ejecutables detectados, archivos `.desktop` asociados, iconos de sistema/tema y el tamaño total ocupado en disco.
- **Lanzamiento Directo:** Ejecuta aplicaciones de forma segura como usuario normal con un solo clic.
- **Generador de Lanzadores:** Detecta automáticamente aplicaciones sin acceso directo en el menú del sistema y ofrece la opción de crearlos al instante en `~/.local/share/applications`.
- **Desinstalación Limpia (`pkexec`):** Elimina por completo la carpeta de la aplicación utilizando privilegios elevados mediante una única y segura solicitud de contraseña (`rm -rf`), limpiando automáticamente los accesos directos huérfanos del menú.
- **Multihilo (Non-blocking):** Las operaciones pesadas como el escaneo de directorios y el cálculo de pesos en disco corren en segundo plano, manteniendo la interfaz siempre fluida y receptiva.

---

## 🛠️ Requisitos del Sistema

- **Sistema Operativo:** Linux (probado en entornos X11/Wayland con administradores de ventanas compatibles con `tkinter`).
- **Dependencias:**
  - Python 3.10 o superior.
  - Librería estándar de Python (`tkinter`, `subprocess`, `threading`, etc.).
  - Utilidad `pkexec` (habitual en la mayoría de distribuciones Linux para la gestión de permisos administrativos en desinstalaciones).

---

## 🚀 Uso Rápido

Clona el repositorio o descarga el script principal en tu equipo y ejecútalo directamente desde la terminal:

```bash
python3 main.py