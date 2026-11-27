# MP-Manager Active Patch Registry

> **Active-only tracker:** Keeps only commits **not yet built in CI** and **offline hot-patches** applied to the device APK.
> Once a commit is pushed **and** built by CI, move it to [PATCH_REGISTRY_ARCHIVE.md](./PATCH_REGISTRY_ARCHIVE.md).

- **Latest Online CI Baseline:** Run `#46000183769` (`2026-11-27 12:45 UTC`, commit `c0700262`) -> see [PATCH_REGISTRY_ARCHIVE.md](./PATCH_REGISTRY_ARCHIVE.md).

---

## Pending / Unbuilt Commits & Offline Hot-Patches

| Commit | Description | Pushed (`origin/test`) | Built Online (CI) | Patched Offline on Device APK |
| :--- | :--- | :---: | :---: | :---: |
| — | *Clean slate — all pushed commits have been built into release in Run `#46000183769`.* | — | — | — |

---

## Active Offline APK Snapshot (`2026-11-27 12:46 UTC`)
- **APK:** `/storage/emulated/0/Download/apks/Pref/Antigravity/MP-Manager-release.apk` (Official CI build Run `#46000183769`, signed with `permanent_release.keystore`, `mido`)
- **Status:** Installed on device via Shizuku / ADB shell.
- **Applied Hot-Patches:** None pending (fully synchronized with CI release).

