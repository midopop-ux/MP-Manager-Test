# MP-Manager Patch & Build Registry

> Tracking exact state of Git commits, online CI builds, and offline APK hot-patches on branch `test`.

## 1. Latest Online CI Build
- **Workflow Run:** `#45979579247` (`2026-11-27 10:19 UTC`)
- **Base Commit:** `f0012994` (`fix(arsc): use Node.depth field in collapseDeepestExpanded`)
- **Includes Online Commits:**
  - `57e6f9db` — `feat(native): add persistent D and batch B directory sizing to mpio serve v2`
  - `7f8e4ae0` — `feat(privilege): prioritize Root/mpio over Shizuku including Android/data and Android/obb`
  - `c40302ac` — `feat(cache): add smart LRU DirSizeCache with tiered TTL, cooldowns, and batch prefetching`
  - `e2bc74e0` — `feat(arsc): auto-expand root package and add step-back + double-back exit prompt`
  - `20291e94` — `perf(mpio,cache): stream per-item folder sizes immediately and follow symlinked root dirs`
  - `f0012994` — `fix(arsc): use Node.depth field in collapseDeepestExpanded`

---

## 2. Commits After Latest CI Build (Not Built Online Yet)

| Commit | Description | Pushed Online (`origin/test`) | Built Online (CI) | Patched Offline on Device APK |
| :--- | :--- | :---: | :---: | :---: |
| `00c3b82e` | `perf(ui): coalesce streaming dirsize UI updates every 80ms and tighten batch timeouts` | ❌ No (Local) | ❌ No | ✅ Yes (batch chunks `12`/`24` + timeouts `500ms`/`2000ms` via Smali) |
| `d7837f8d` | `feat(mpio,ui): measure symlinked target directories safely with virtual_filesystem guard` | ❌ No (Local) | ❌ No | ✅ Yes (`mpio` binary + `FileIconLoader.shouldMeasureFolder` Smali) |
| `33c32ac1` | `perf(mpio): exclude redundant /mnt FUSE bind-mount trees from recursive sizing` | ❌ No (Local) | ❌ No | ✅ Yes (`assets/bin/arm64-v8a/mpio` binary replaced) |

---

## 3. Offline APK Hot-Patch Details (`2026-11-27 11:06 UTC`)
- **Target APK:** `/storage/emulated/0/Download/apks/Pref/Antigravity/MP-Manager-release.apk`
- **Signing Key:** `permanent_release.keystore` (`alias: mido`)
- **Installed on Device:** ✅ Yes (`pm install -r` -> `Success`)
- **Patched Components:**
  1. `assets/bin/arm64-v8a/mpio` — Updated to compiled `33c32ac1` binary (follows target dir symlinks, guards resolved target against `virtual_filesystem`, excludes `/mnt/{runtime,androidwritable,installer,pass_through,user}`).
  2. `smali_classes3/.../FileIconLoader.smali`:
     - `shouldMeasureFolder`: removed `RootFile.isLink()`, `ShizukuFile.isLink()`, and `FileUtils.isSymlink()` exclusions.
     - `runBatchPrefetch`: reduced `SIZE_BATCH_FIRST_CHUNK` (`0x18` -> `0xc`) and `SIZE_BATCH_NEXT_CHUNK` (`0x28` -> `0x18`).
     - `measureDirectoryBatch`: tightened `measureSizesBatch` timeouts (`800ms/4500ms` -> `500ms/2000ms`).

---

## 4. CI Mapping Support
- `.github/workflows/android.yml` updated to upload `app/build/outputs/mapping/fullRelease/mapping.txt` alongside the APK in `MP-Manager-APK` artifacts for future offline Smali/ProGuard mapping.
