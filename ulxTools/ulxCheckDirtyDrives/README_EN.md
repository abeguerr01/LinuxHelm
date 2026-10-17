> ### [Documentacion en español](README.md)

# ulxCheckDirtyDrives.sh

A Bash script to **check and repair "dirty" NTFS partitions** from Linux.

It's common, after installing Linux next to Windows or after a forced shutdown, for
Windows to leave its NTFS partitions marked as *dirty* or "pending chkdsk". In that
state the disk may throw errors, Windows shows "checking disk" dialogs at boot, or you
simply can't trust it. This script detects them, asks you, and fixes them.

> Internal name in the script header: `revisar-dirty.sh`

---

## What does it do?

1. **Finds** all NTFS partitions on the machine (or only the ones you specify).
2. **Checks** which ones are in a bad state:
   - **dirty**: the NTFS volume flag is set (`ntfsinfo`).
   - **hibernated**: `hiberfil.sys` exists (Windows Fast Startup / hibernation).
3. **Asks** whether to fix them (or does it directly with `-y`).
4. **Repairs** each one in 4 steps:
   1. Unmounts the partition (if it was mounted).
   2. Removes the Windows hibernation file (`ntfs-3g -o force,remove_hiberfile`).
   3. Clears the dirty flag (`ntfsfix -d`).
   4. Mounts it again with the original options (or the ones from `/etc/fstab`).
5. **Verifies** the result and prints advice so it doesn't happen again.

---

## How does it work?

### Dirty detection

The `detectar_dirty` function runs `ntfsinfo -m <device>` and inspects the output:

| `ntfsinfo` output                        | Result |
|------------------------------------------|--------|
| contains `scheduled for check`           | DIRTY (the volume can't even be opened) |
| `Volume Flags: 0x…` different from `0x0` | DIRTY (the hex value is displayed) |
| `permission denied` / `no such file`     | Can't be checked → skipped |
| any other error                          | Treated as DIRTY (inconsistent NTFS) |

### Hibernation detection

`detectar_hiber` lists the root files with `ntfsls -f` and searches for `hiberfil.sys`.
The `-f` flag is required because a dirty volume won't be listed without it.

### Repair (`arreglar`)

Before touching anything it saves the mount state (mount point, options, filesystem
type, and whether it's in `/etc/fstab`), so it can be restored afterwards:

- If the volume **can't be unmounted** (some program is using it), that volume is
  aborted and nothing is changed.
- Hibernation is removed by temporarily mounting with `ntfs-3g -o force,remove_hiberfile`
  in a temporary directory and unmounting it right away.
- The dirty flag is cleared with `ntfsfix -d`, and then **re-checked** to confirm it's
  actually clean.
- If remounting fails, the manual command to do it is printed.

### Permissions

- With no arguments it needs root (it inspects the system disks) and **re-executes
  itself** with `sudo`, prompting for the password.
- With arguments it only requires root if one of them is a block device (`/dev/sdb3`);
  if you pass a disk image (`.img`) root is not needed.

---

## Requirements

- Bash (Linux).
- The **ntfs-3g** package, which provides `ntfsinfo`, `ntfsfix`, `ntfsls` and `ntfs-3g`:

  ```bash
  sudo apt install ntfs-3g
  ```

- `lsblk` and `findmnt` (from `util-linux`, already present in almost every distribution).
- Root or `sudo` (to access the disks).

If any tool is missing, the script warns and exits before touching anything.

---

## Usage

```bash
./ulxCheckDirtyDrives.sh              # check all NTFS partitions
./ulxCheckDirtyDrives.sh /dev/sdb3    # check only that partition (or an .img image)
./ulxCheckDirtyDrives.sh -y           # don't ask: fix everything it can
./ulxCheckDirtyDrives.sh -h           # show help
```

Options:

| Option        | Effect                                            |
|---------------|---------------------------------------------------|
| *(none)*      | Check all NTFS partitions on the machine          |
| `partition`   | Check only that NTFS partition (or an `.img`)     |
| `-y`, `--yes` | Don't ask, fix everything it can                  |
| `-h`, `--help`| Show help                                          |

### Sample output

```
=== NTFS volume check ===
Partitions found: 2

  • /dev/sdb3 (Data)  →  mounted at /mnt/data
      ✗ DIRTY: volume flags are 0x1
      ✗ HIBERNATED: Windows left hiberfil.sys (Fast Startup / hibernation)

  • /dev/sdc1 (Backup)  →  not mounted
      ✓ OK

1 partition(s) with problems found.
  - /dev/sdb3  (dirty + hibernated; the hibernated Windows session will be lost)

Fix it/them? [y/N]: y

Fixing /dev/sdb3 ...
  1. Unmounting /mnt/data ...
  2. Removing the hibernation file (hiberfil.sys) ...
  3. Clearing the dirty flag (ntfsfix -d) ...
  ✔ dirty flag removed.
  4. Mounting again at /mnt/data ...
  ✔ remounted at /mnt/data.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
RESULT: the dirty flag was cleared on
the 1 partition(s) checked. ALL GOOD.
```

---

## Behaviour and notes

- **Interactive mode**: if there's no terminal and `-y` isn't passed, it fixes nothing
  and exits (so it never destroys a hibernated Windows session without asking).
- **Losing the hibernated session**: deleting `hiberfil.sys` discards the state Windows
  had saved (that's what Fast Startup is for). The script warns you before asking.
- **Colored output**: only when stdout is a terminal. Disable it with the `NO_COLOR`
  environment variable or by redirecting the output to a file.
- **No NTFS partitions**: it just says so and exits without error.
- **Exit codes**: `0` = all good or nothing to repair; `1` = missing tools, unknown
  option, or missing permissions.

---

## Tips to prevent it from happening again

1. **Turn off Fast Startup in Windows** (the #1 cause). Open an elevated Command
   Prompt and run:

   ```
   powercfg /h off
   ```

   If you'd rather keep hibernation, just disable Fast Startup in
   *Control Panel → Power Options → Choose what the power buttons do*.

2. **Don't force-shutdown Linux** with an NTFS disk mounted: unmount first or shut
   down normally.

3. If Windows complains again that the disk is "dirty", just run this script again.

---

## Script structure

| Section / function | Purpose |
|---|---|
| `usar()` | Prints the help text |
| (tool check) | Verifies `ntfsinfo`, `ntfsfix`, `ntfsls`, `lsblk`, `findmnt` |
| (phase 1: root) | Re-runs itself with `sudo` when needed |
| (phase 2: arguments) | Parses `-y`, `-h` and the target partitions |
| `detectar_dirty()` | Returns 0 = clean, 1 = dirty, 2 = not checkable; stores the reason in `$_motivo` |
| `detectar_hiber()` | Detects `hiberfil.sys` |
| `etiqueta()` | Gets the volume label with `lsblk` |
| `arreglar()` | Unmount → remove hibernation → `ntfsfix -d` → verify → remount |
| (main) | Lists disks, reports, asks for permission and runs the repair |
