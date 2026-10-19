# ULX Tools

Carpeta de **herramientas del sistema** de LinuxHelm. Todo lo que hay aquí se
empaqueta dentro del JAR, se extrae al arrancar en
`~/.config/ulx/LinuxHelm/ULX Tools/` y se registra **automáticamente** en la app
como herramienta fija (**no borrable** desde la interfaz).

> Las herramientas que el usuario añade con el botón *Añadir script* de la app
> son otra cosa distinta: sí se pueden borrar. Este sistema y el de scripts del
> usuario conviven sin tocarse.

## Estructura

Cada herramienta vive en su propia subcarpeta. Una subcarpeta se considera
herramienta **si contiene al menos un archivo `.sh`** (el escaneo es recursivo,
así que también vale anidar en subcarpetas).

```
ULX Tools/
├── ulx-sys-overview/              # ← una herramienta
│   ├── ulx-sys-overview.sh        # ejecutable principal
│   ├── metadata.json              # metadatos (opcional)
│   ├── README.es.md               # docs (opcional)
│   └── README.en.md               # docs (opcional)
└── mi-otra-herramienta/           # ← otra herramienta
    └── mi-otra-herramienta.sh
```

### `metadata.json` (o `info.json`)

```json
{
  "name": "Nombre de la Herramienta",
  "description": "Descripción detallada",
  "website": "https://ejemplo.com"
}
```

- Los tres campos son **opcionales**.
- Si **no existe** el JSON, el nombre por defecto es el de la carpeta.
- La app también acepta `info.json` como nombre alternativo del fichero.
- El ejecutable principal es el `.sh` cuyo nombre coincide con el de la
  carpeta; si no hay coincidencia, el primero en orden alfabético.

## Cómo añadir una herramienta

1. Crea una subcarpeta dentro de `ULX Tools/`.
2. Pon dentro tu script `.sh` (con el mismo nombre que la carpeta, si quieres
   que quede como principal).
3. Añade un `metadata.json` con `name`, `description` y `website` (opcional).
4. Documentación en `README.es.md` / `README.en.md` (opcional).
5. Compila y ejecuta: la herramienta aparece sola en *Herramientas*.

> Nota: al compilar, esta carpeta se copia al JAR con el prefijo `ULXTools/`
> (ver `shared/build.gradle.kts`). En runtime se extrae a
> `~/.config/ulx/LinuxHelm/ULX Tools/` y se escanea. También puedes dejar tus
> propias subcarpetas directamente en esa ruta de configuración y las detectará
> igualmente (queda en tu equipo, no en el paquete).

Herramienta de ejemplo incluida: [`ulx-sys-overview/`](./ulx-sys-overview).
