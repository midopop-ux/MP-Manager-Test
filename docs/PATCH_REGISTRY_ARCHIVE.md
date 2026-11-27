# MP-Manager Patch Registry Archive

> Completed builds & commits that have been pushed to `origin/test` **and** built/released via GitHub Actions CI.

## Build Run `#46076049032` (`2026-11-27 22:30 UTC`)
- **Branch:** `test` (Head: `6593ffd5`)
- **ProGuard Mapping:** `/data/user/0/com.agy/files/.gemini/antigravity-app/scratch/mp_patch/mappings/mapping-release-6593ffd5.txt`
- **Included Commits:**
  - `6de3bae6` — `fix(privilege): strictly enforce WorkingMode in AccessManager/RootManager and reset root daemon on switch`
  - `f03ce43e` — `fix(ui): align format row buttons, compact profile actions, and fix bottom scroll padding in overlay editor`
  - `2d05e70c` — `feat(editor): highlight Smali .method declaration lines with MT-style row background`
  - `05651745` — `feat(editor): support importing .mtsx and .tmLanguage.json syntaxes and refine Smali method header colors`
  - `e71ac0ea` — `perf(compare): parallelize Smali disassembly and UTF-8 encoding in bounded batches during .mtcr export`
  - `8461eae2` — `fix(privilege): isolate working modes strictly across AccessManager, RootManager, and MainActivity listing`
  - `6593ffd5` — `feat(crypto): add content-first file crypto analyzer and standalone decryption workspace`

## Build Run `#46000183769` (`2026-11-27 12:45 UTC`)
- **Branch:** `test` (Head: `c0700262`)
- **Included Commits:**
  - `00c3b82e` — `perf(ui): coalesce streaming dirsize UI updates every 80ms and tighten batch timeouts`
  - `d7837f8d` — `feat(mpio,ui): measure symlinked target directories safely with virtual_filesystem guard`
  - `33c32ac1` — `perf(mpio): exclude redundant /mnt FUSE bind-mount trees from recursive sizing`
  - `caf7e6f5` — `docs(ci): add PATCH_REGISTRY.md and upload ProGuard mapping.txt in CI artifacts`
  - `08031bd7` — `fix(files): treat Android/media as direct local path so local edits refresh immediately`
  - `f73835b7` — `docs: split patch registry into active tracker and archive`
  - `5c7320ae` — `feat(ui): make 3D Icons optional via preference (off by default, flat fallback)`
  - `7acc4e22` — `fix(ui): restore 0xFF252525 classic folder tile and invalidate icon caches on 3D toggle`
  - `a6153a86` — `fix(installer): treat Android 15 'Completed with warning' pm install output as success`
  - `a48d54fb` — `fix(installer): check Completed with warning before exit code in pm install-commit`
  - `50ea64de` — `feat(installer): show red clickable 'warning' after installed successfully`
  - `0af63ed6` — `docs: mark 50ea64de as offline-patched in PATCH_REGISTRY.md`
  - `f76eb9b3` — `fix(installer): prevent warning long-press copy from also opening error dialog on touch up`
  - `c0700262` — `docs: record f76eb9b3 in PATCH_REGISTRY.md`

## Build Run `#45979579247` (`2026-11-27 10:19 UTC`)
- **Branch:** `test` (Head: `f0012994`)
- **Included Commits:**
  - `57e6f9db` — `feat(native): add persistent D and batch B directory sizing to mpio serve v2`
  - `7f8e4ae0` — `feat(privilege): prioritize Root/mpio over Shizuku including Android/data and Android/obb`
  - `c40302ac` — `feat(cache): add smart LRU DirSizeCache with tiered TTL, cooldowns, and batch prefetching`
  - `e2bc74e0` — `feat(arsc): auto-expand root package and add step-back + double-back exit prompt`
  - `20291e94` — `perf(mpio,cache): stream per-item folder sizes immediately and follow symlinked root dirs`
  - `f0012994` — `fix(arsc): use Node.depth field in collapseDeepestExpanded`

