# MP-Manager Active Patch Registry

> **Active-only tracker:** Keeps only commits **not yet built in CI** and **offline hot-patches** applied to the device APK.
> Once a commit is pushed **and** built by CI, move it to [PATCH_REGISTRY_ARCHIVE.md](./PATCH_REGISTRY_ARCHIVE.md).

- **Latest Online CI Baseline:** Run `#46076049032` (`2026-11-27 22:30 UTC`, commit `6593ffd5`) -> see [PATCH_REGISTRY_ARCHIVE.md](./PATCH_REGISTRY_ARCHIVE.md).
- **ProGuard Mapping Location:** `/data/user/0/com.agy/files/.gemini/antigravity-app/scratch/mp_patch/mappings/latest-mapping.txt` (symlinked to `mapping-release-6593ffd5.txt`).

---

## Pending / Unbuilt Commits & Offline Hot-Patches

*(No pending commits currently. All commits up to `6593ffd5` have been built in CI Run `#46076049032`).*

---

## Active Offline APK Snapshot (`2026-11-27 20:49 UTC`)
- **APK:** `/storage/emulated/0/Download/MP-Manager-release.apk` (signed with `permanent_release.keystore`, `mido`)
- **Applied Hot-Patches:**
  1. `AccessManager.smali` & `RootManager.smali` (`6de3bae6` — strict `WorkingMode` enforcement + live `resetServe()`)
  2. `ApkOverlayTools.smali` (`f03ce43e` — compact `B`/`I`/`U`/`Color` & `Save`/`Delete` profile buttons)
  3. `CompareDexActivity.smali` + `classes5.dex` (`e71ac0ea` — parallel `.mtcr` export helper)
  4. `MainActivity.smali` (`listPlainFolder` — isolate Root to `Backend.ROOT`, direct Shizuku listing for `Backend.SHIZUKU`, isolate `Backend.NONE`)


