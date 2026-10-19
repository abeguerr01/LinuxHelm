# ulx-sys-overview

Example tool for the **ULX Tools** structure.

Shows a **quick, read-only** summary of the system: kernel and distro, uptime,
CPU, memory and the usage of mounted partitions. It **does not modify** the
machine.

## Requirements

- Nothing extra: uses standard tools (`uname`, `free`, `df`, `/proc`).

## Usage

```bash
./ulx-sys-overview.sh
```

## Structure (template for adding your own tools)

```
ulx-sys-overview/
├── ulx-sys-overview.sh   # main executable (.sh)
├── metadata.json          # name, description and website (optional)
├── README.es.md           # documentation (optional)
└── README.en.md           # documentation (optional)
```

On startup, LinuxHelm recursively scans `ULX Tools/`, detects this folder
(because it contains a `.sh`), registers it in the database as a **system
tool** and shows it in the *Tools* section. As a system tool it **cannot be
deleted** from the interface.
