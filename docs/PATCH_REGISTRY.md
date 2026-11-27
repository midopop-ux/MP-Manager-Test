# MP-Manager Active Patch Registry

> **Active-only tracker:** Keeps only commits **not yet built in CI** and **offline hot-patches** applied to the device APK.
> Once a commit is pushed **and** built by CI, move it to [PATCH_REGISTRY_ARCHIVE.md](./PATCH_REGISTRY_ARCHIVE.md).

- **Latest Online CI Baseline:** Run `#45979579247` (`2026-11-27 10:19 UTC`, commit `f0012994`) -> see archive.

---

## Pending / Unbuilt Commits & Offline Hot-Patches

| Commit | Description | Pushed (`origin/test`) | Built Online (CI) | Patched Offline on Device APK |
| :--- | :--- | :---: | :---: | :---: |
| `00c3b82e` | `perf(ui): coalesce streaming dirsize UI updates every 80ms and tighten batch timeouts` | ✅ Yes (`origin/test`) | ❌ No | ✅ Yes (batch chunks `12`/`24` + timeouts `500ms`/`2000ms` via Smali) |
| `d7837f8d` | `feat(mpio,ui): measure symlinked target directories safely with virtual_filesystem guard` | ✅ Yes (`origin/test`) | ❌ No | ✅ Yes (`mpio` binary + `FileIconLoader.shouldMeasureFolder` Smali) |
| `33c32ac1` | `perf(mpio): exclude redundant /mnt FUSE bind-mount trees from recursive sizing` | ✅ Yes (`origin/test`) | ❌ No | ✅ Yes (`assets/bin/arm64-v8a/mpio` binary replaced) |
| `caf7e6f5` | `docs(ci): add PATCH_REGISTRY.md and upload ProGuard mapping.txt in CI artifacts` | ✅ Yes (`origin/test`) | ❌ No | — (CI/Docs) |
| `08031bd7` | `fix(files): treat Android/media as direct local path so local edits refresh immediately` | ✅ Yes (`origin/test`) | ❌ No | ✅ Yes (`MainActivity.smali` regex patched) |
| `5c7320ae` | `feat(ui): make 3D Icons optional via preference (off by default, flat fallback)` | ✅ Yes (`origin/test`) | ❌ No | ✅ Yes (`UiPrefs`, `FileIconSpec`, `FileIconArt`, `SettingsController` Smali) |
| `7acc4e22` | `fix(ui): restore 0xFF252525 classic folder tile and invalidate icon caches on 3D toggle` | ✅ Yes (`origin/test`) | ❌ No | ✅ Yes (`FileIconSpec.smali`, `FileIconLoader.smali`) |
| `a6153a86` | `fix(installer): treat Android 15 'Completed with warning' pm install output as success` | ✅ Yes (`origin/test`) | ❌ No | ✅ Yes (`RootManager.smali`, `PrivilegedInstaller.smali`) |
| `a48d54fb` | `fix(installer): check Completed with warning before exit code in pm install-commit` | ✅ Yes (`origin/test`) | ❌ No | ✅ Yes (`RootManager.smali`, `PrivilegedInstaller.smali`) |
| `50ea64de` | `feat(installer): show red clickable 'warning' after installed successfully` | ✅ Yes (`origin/test`) | ❌ No | ❌ No (Pushed, ready for offline patch or CI build) |

---

## Active Offline APK Snapshot (`2026-11-27 11:33 UTC`)
- **APK:** `/storage/emulated/0/Download/apks/Pref/Antigravity/MP-Manager-release.apk` (signed with `permanent_release.keystore`, `mido`)
- **Applied Hot-Patches:**
  1. `assets/bin/arm64-v8a/mpio` (`33c32ac1`)
  2. `smali_classes3/.../FileIconLoader.smali` (`00c3b82e` + `d7837f8d`)
  3. `smali_classes3/.../MainActivity.smali` (`08031bd7`)
  4. `smali_classes3/.../UiPrefs.smali`, `FileIconSpec.smali`, `FileIconArt.smali`, `SettingsController.smali` (`5c7320ae` — `3D Icons` toggle in Appearance, default off)
