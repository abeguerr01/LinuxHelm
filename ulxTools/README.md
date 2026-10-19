# ulxTools

Carpeta de **herramientas del sistema** de LinuxHelm. Es la **única fuente** de
herramientas incluidas: todo lo que hay aquí se empaqueta dentro del JAR y, al
arrancar, la app despliega **solo los ejecutables** en
`~/.config/ulx/LinuxHelm/tools/` y los registra **automáticamente** en la BD como
herramientas fijas (**no borrables** desde la interfaz).

> Las herramientas que el usuario añade con el botón *Añadir script* de la app son
> otra cosa distinta: sí se pueden borrar y viven en la ruta que el usuario elija.
> Este sistema y los scripts del usuario conviven sin tocarse.

## Estructura

Cada herramienta vive en su propia subcarpeta. Una subcarpeta se considera
herramienta **si contiene al menos un ejecutable** (`.sh`, `.jar`, `.py`, `.run`,
`.bin`, `.AppImage` o un binario sin extensión) y un `metadata.json`.

```
ulxTools/
├── ulxCheckDirtyDrives/
│   ├── ulxCheckDirtyDrives.sh   # ejecutable
│   ├── metadata.json            # metadatos (obligatorio)
│   ├── README.md                # docs (solo en el repo, NO se despliegan)
│   └── README_EN.md
└── ulxFixDiskOwnership/
    ├── ulxFixDiskOwnership.sh
    ├── metadata.json
    └── READMEs…
```

Al arrancar, la app empaqueta cada subcarpeta, copia **solo el ejecutable** a
`~/.config/ulx/LinuxHelm/tools/` (plano, con permiso `+x`) y lee su
`metadata.json` para registrar nombre, descripción y web en la BD. Los `README`
se quedan en el repositorio: la carpeta `tools/` en el equipo del usuario queda
**solo con ejecutables**.

### `metadata.json` (obligatorio)

```json
{
  "name": "Nombre de la Herramienta",
  "description": "Descripción detallada",
  "website": "https://ejemplo.com"
}
```

- Los tres campos son opcionales; si falta `name`, se usa el nombre de la carpeta.
- El ejecutable principal es el fichero cuyo nombre (sin extensión) coincide con
  el de la carpeta; si no coincide ninguno, uno con extensión conocida y, en su
  defecto, uno sin extensión (determinista). La app sabe lanzar todos estos tipos
  (ver `ulxScripts.kt`).

## Cómo añadir una herramienta

1. Crea una subcarpeta dentro de `ulxTools/`.
2. Pon dentro tu ejecutable (con el mismo nombre que la carpeta, si quieres que
   quede como principal).
3. Añade un `metadata.json` con `name`, `description` y `website`.
4. (Opcional) Documentación en `README.md` / `README_EN.md`: se queda en el repo.
5. Compila y ejecuta: la herramienta aparece sola en *Herramientas*, sin papelera.

> Nota: al compilar, cada subcarpeta se copia al JAR bajo el prefijo `ULXTools/`
> (ver `shared/build.gradle.kts`). En runtime se extrae a una carpeta temporal, se
> despliega el ejecutable en `tools/` y se lee el `metadata.json`.
