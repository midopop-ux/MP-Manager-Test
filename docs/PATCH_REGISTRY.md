# MP-Manager Active Patch Registry

> **Active-only tracker:** Keeps only commits **not yet built in CI** and **offline hot-patches** applied to the device APK.
> Once a commit is pushed **and** built by CI, move it to [PATCH_REGISTRY_ARCHIVE.md](./PATCH_REGISTRY_ARCHIVE.md).

- **Latest Online CI Baseline:** Run `#46000183769` (`2026-11-27 12:45 UTC`, commit `c0700262`) -> see [PATCH_REGISTRY_ARCHIVE.md](./PATCH_REGISTRY_ARCHIVE.md).

---

## Pending / Unbuilt Commits & Offline Hot-Patches

| Commit | Description | Pushed (`origin/test`) | Built Online (CI) | Patched Offline on Device APK |
| :--- | :--- | :---: | :---: | :---: |
| `6de3bae6` | `fix(privilege): strictly enforce WorkingMode in AccessManager/RootManager and reset root daemon on switch` | ✅ Yes (`origin/test`) | ❌ No | ✅ Yes (`AccessManager.smali`, `RootManager.smali`) |
| `f03ce43e` | `fix(ui): align format row buttons, compact profile actions, and fix bottom scroll padding in overlay editor` | ✅ Yes (`origin/test`) | ❌ No | ✅ Yes (`ApkOverlayTools.smali` format/profile buttons) |
| `2d05e70c` | `feat(editor): highlight Smali .method declaration lines with MT-style row background` | ✅ Yes (`origin/test`) | ❌ No | ❌ No (needs CI build for `LineBackground.<init>`) |
| `05651745` | `feat(editor): support importing .mtsx and .tmLanguage.json syntaxes and refine Smali method header colors` | ✅ Yes (`origin/test`) | ❌ No | ❌ No (new class `CustomSyntaxManager`) |
| `e71ac0ea` | `perf(compare): parallelize Smali disassembly and UTF-8 encoding in bounded batches during .mtcr export` | ✅ Yes (`origin/test`) | ❌ No | ✅ Yes (`CompareDexActivity.smali` + `MtcrExportHelper`) |

---

## Active Offline APK Snapshot (`2026-11-27 20:49 UTC`)
- **APK:** `/storage/emulated/0/Download/MP-Manager-release.apk` (signed with `permanent_release.keystore`, `mido`)
- **Applied Hot-Patches:**
  1. `AccessManager.smali` & `RootManager.smali` (`6de3bae6` — strict `WorkingMode` enforcement + live `resetServe()`)
  2. `ApkOverlayTools.smali` (`f03ce43e` — compact `B`/`I`/`U`/`Color` & `Save`/`Delete` profile buttons)
  3. `CompareDexActivity.smali` + `classes5.dex` (`e71ac0ea` — parallel `.mtcr` export helper)
  4. `MainActivity.smali` (`listPlainFolder` — isolate Root to `Backend.ROOT`, direct Shizuku listing for `Backend.SHIZUKU`, isolate `Backend.NONE`)


