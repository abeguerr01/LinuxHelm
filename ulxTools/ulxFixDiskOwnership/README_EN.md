> ### [Documentacion en español](README.md)

# ulxFixDiskOwnership.sh

A Bash script to **check and fix the owner of secondary disks and partitions** mounted
on Linux.

It's very common for a data disk mounted at `/mnt/HDD`, `/mnt/data` or similar to be left
owned by `root:root` (because it was mounted at boot, mounted by hand, or carried over from
a previous boot), which means your user cannot write to it. This script detects those disks,
shows you who currently owns them and, if you confirm, assigns them to your user.

> Previous script name: `ulxFixFolderProperty.sh`

---

## What does it do?

1. **Checks** that you did not run it as `root` (it must run as your normal user).
2. **Finds** the mount points that are **real secondary disks/partitions**.
3. **Checks** the current owner of each one (`stat -c '%U:%G'`).
4. **Reports**: `Correcto` (OK) if you already own it, `Conflicto` (conflict) if not.
5. **Asks** whether to change it (default: **No**) and, if you accept, runs
   `sudo chown -R user:group <mountpoint>`.
6. **Waits for you to press Enter** at the end so you can read the output.

---

## How does it work?

### Secondary disk detection (the filter)

First the **root disk** is identified:

```bash
root_source=$(findmnt -n -o SOURCE / | sed -E 's/\[.*$//')   # e.g. /dev/sdb6
```

The `sed` strips the btrfs subvolume suffix (`/dev/sdb6[/@]` → `/dev/sdb6`), and then every
mount point goes through two filters:

| Filter | Rule | Example discarded |
|---|---|---|
| `awk` | The source must be a **real block device** (`/dev/…`) **different from the root disk** | btrfs subvolumes of the root (`/@home`, `/@log`), `tmpfs`, `proc`, `sysfs` |
| `grep -v` | And not under a **system path**: `/`, `/sys`, `/proc`, `/dev`, `/run`, `/snap`, `/boot`, `/tmp`, `/var`, `/usr`, `/etc` | `/boot` (EFI), `/var/log`, `/run/media/...` |

That leaves only things like `/mnt/HDD` (NTFS), `/mnt/data` (ext4) or `/media/user/USB`.

> ⚠️ This filter is deliberate: without it, on a btrfs machine the script would "detect"
> `/home`, `/root` and `/var/log` as conflicting disks and offer to `chown -R` your whole
> system tree.

### Owner check

Each mount point is inspected with `stat -c '%U'` and `stat -c '%G'`. If both match your
user and group it's marked `Correcto` and the script moves on; otherwise it reports
`Conflicto` and asks.

### Fixing

The answer accepts `y`, `Y`, `yes` or `YES` in Spanish terms — the script is in Spanish, so
it expects `s` / `si` (anything else, including Enter, skips it):

```bash
sudo chown -R "$CURRENT_USER:$CURRENT_GROUP" "$mount_point"
```

The `chown` exit code is checked and success or failure is reported.

### Permissions

- **Checking** the disks does not need root: reading `findmnt` and `stat` is enough.
- **Fixing** does require `sudo` (only then, and only for each disk you agree to fix).
- If it is run **as root**, the script refuses and exits with code `1`.

---

## Requirements

- Bash (Linux).
- `findmnt`, `stat`, `awk`, `sed` and `grep` (from `util-linux`/`coreutils`/`gawk`/`sed`/
  `grep`, present in almost every distribution).
- `sudo` (only for the fixing step).

No extra packages are needed and root is not required in read-only mode.

---

## Usage

```bash
./ulxFixDiskOwnership.sh     # checks secondary disks and fixes them if you say so
```

Run it **as your normal user** (not `sudo ./…`, the script rejects that on purpose), ideally
from an open terminal so the final "Enter" pause works.

| Argument | Effect |
|---|---|
| *(none)* | Interactive mode: checks, reports and asks |

### Sample output

```
Comprobador de permisos de discos/particiones para: alejandro


Se han detectado los siguientes puntos de montaje:
--------------------------------------------------------------------------

 Disco/Ruta: /mnt/HDD
 Propietario actual: root:root
 Estado: Conflicto (El propietario es 'root' y tu usuario es 'alejandro').
   ¿Deseas cambiar el propietario de '/mnt/HDD' a 'alejandro'? (s/N): s
 Aplicando cambios con permisos de administrador...
 ¡Permisos corregidos con éxito en /mnt/HDD!
--------------------------------------------------------------------------


Proceso finalizado.

Pulsa Intro (Enter) para salir...
```

When everything is fine it doesn't ask anything:

```
 Disco/Ruta: /mnt/HDD
 Propietario actual: alejandro:alejandro
 Estado: Correcto (Ya eres el propietario).
```

> Note: the script's messages are in Spanish. Answer **`s`** (sí/yes) to apply a change.

---

## Behaviour and notes

- **Interactive mode**: the default answer is **No**; if there is no terminal (or you don't
  answer), nothing is changed.
- **Final pause**: it waits for Enter on all three exit paths (run as root, no secondary
  disks, and normal finish).
- **Recursive `chown -R`**: it changes the owner of the whole disk contents, not just the
  root directory. If you only want the root directory changed, run the `chown` manually
  without `-R`.
- **Filesystems without Unix permissions**: on **FAT/exFAT** (`vfat`/`exfat` mounts) and on
  **NTFS** (`ntfs3`) there are no per-file owners: `chown` may report success or fail with
  no real effect. There, the mount options (`uid=`, `fmask=`, `dmask=`) are what matter. See
  the tips below.
- **USB drives auto-mounted by udisks**: those in `/run/media/$USER/…` already belong to you,
  and the filter discards them anyway as system paths.
- **No colored output**: plain text, safe to redirect to a file.
- **Exit codes**: `0` = all good or no secondary disks; `1` = run as root.

---

## Tips

1. **Don't use `chown` on FAT/exFAT/NTFS**: mount with the right options instead. For
   example, in `/etc/fstab`:

   ```
   /dev/sdb2   /mnt/HDD   ntfs3   uid=alejandro,gid=alejandro,umask=0022,nofail   0   0
   ```

   The disk then comes up owned by you with no further action.

2. **If the disk always mounts in the same place**, add it to `/etc/fstab` (with `nofail`
   if it may be unplugged): you gain owner control, permissions and a fixed mount point.

3. **For your own ext4/btrfs disks**, once:

   ```bash
   sudo chown -R "$USER:$USER" /mnt/data
   ```

4. **Does `root:root` keep coming back on an NTFS disk?** It's almost always a missing
   `uid=` mount option, not a permission problem: fix the fstab, not the chown.

---

## Script structure

| Section / function | Purpose |
|---|---|
| `pausar()` | Waits for Enter before exiting (the three exit paths) |
| (root check) | Refuses to run as `root` → `exit 1` |
| (mount filter) | `findmnt` + `sed` (root disk) → `awk` (real `/dev/…` devices other than root) → `grep` (drops system paths) |
| (main loop) | For each mount point: `stat` the owner → report → ask → `sudo chown -R` |
| (main) | Greeting, "nothing found" notice and farewell with pause |
