# ulx-sys-overview

Herramienta de ejemplo de la estructura **ULX Tools**.

Muestra un resumen **rápido y de solo lectura** del sistema: kernel y distro,
uptime, CPU, memoria y el uso de las particiones montadas. **No modifica nada**
del equipo.

## Requisitos

- Ninguno extra: usa herramientas estándar (`uname`, `free`, `df`, `/proc`).

## Uso

```bash
./ulx-sys-overview.sh
```

## Estructura (plantilla para añadir tus propias herramientas)

```
ulx-sys-overview/
├── ulx-sys-overview.sh   # ejecutable principal (.sh)
├── metadata.json          # nombre, descripción y web (opcional)
├── README.es.md           # documentación (opcional)
└── README.en.md           # documentación (opcional)
```

Al arrancar, LinuxHelm escanea recursivamente `ULX Tools/`, detecta esta
carpeta (porque contiene un `.sh`), la registra en la base de datos como
**herramienta del sistema** y la muestra en la sección *Herramientas*. Por ser
herramienta del sistema **no se puede borrar** desde la interfaz.
