# AGENTS.md — N0Kontzzz Kernel Manager

> Guidance for AI coding agents working on this repository.
> Read this before touching anything.

## Project Identity

| Property | Value |
|---|---|
| App name | N0Kontzzz Kernel Manager (NKM) |
| Root project name | `XtraKernelManager` (see `settings.gradle.kts`) |
| Application ID | `id.nkz.nokontzzzmanager` |
| Version | 1.7.0 (versionCode 106) |
| License | GPL-3.0 |
| Target hardware | Kona (SM8250) — Poco F4 (munch); extended to other Snapdragon/MediaTek SOCs |
| Supported kernels | N0Kontzzz, N0kernel, FusionX, Lunar, E404R, perf+, Oxygen+, dead-butterflies |
| Official repo | `bimoalfarrabi/N0Kontzzz-Kernel-Manager` |
| Origin | Fork of [Xtra Kernel Manager](https://github.com/Gustyx-Power/Xtra-Kernel-Manager) by Gustyx-Power |

## SDK & Build

| Setting | Value |
|---|---|
| compileSdk | 36 |
| minSdk | 31 |
| targetSdk | 36 |
| Java | 17 |
| Kotlin | 2.4.0 |
| AGP | 9.2.1 |
| KSP | 2.3.9 |
| Build system | Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`) |
| ProGuard | Enabled for release (`isMinifyEnabled = true`) |
| Annotation processing | KSP (Hilt + Room) |

### Build & Run

```bash
# Debug build
./gradlew assembleDebug

# Release build (ProGuard/R8 minified)
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Clean build
./clean_build.sh   # or: ./gradlew clean assembleDebug
```

> **Note:** This project requires Android SDK 36, JDK 17, and a rooted Android device (minSdk 31) for runtime testing. No emulator can exercise root functionality.

## Technology Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Kotlin | 2.4.0 |
| UI | Jetpack Compose + Material 3 Expressive | BOM 2026.06.00, Material3 1.5.0-alpha22 |
| DI | Dagger Hilt | 2.59.2 |
| Navigation | Compose Navigation | 2.9.8 |
| Lifecycle | AndroidX Lifecycle | 2.11.0 |
| Database | Room | 2.8.4 |
| Background | WorkManager + Foreground Services | 2.11.2 |
| Persistence | DataStore Preferences | 1.2.1 |
| Serialization | kotlinx-serialization | 1.11.0 |
| Collections | kotlinx-collections-immutable | 0.5.0 |
| Root shell | libsu (topjohnwu) | 6.0.0 |
| Image loading | Coil | 2.7.0 |
| Other | Accompanist (drawablepainter) | 0.37.3 |
| Testing | JUnit 4.13.2, Mockito 5.23.0, Robolectric 4.16.1 |

**No internet permission.** The app is fully offline. Never add a network dependency.

## Architecture — MVVM

```
NkzApp (Application, HiltAndroidApp)
  └── MainActivity (single Activity)
       ├── Compose NavHost (14 routes)
       ├── Scaffold: UnifiedTopAppBar + BottomNavBar + FAB
       └── Screens → ViewModels → Repositories → Root/Shell
```

### Package Structure

```
id.nkz.nokontzzzmanager/
├── NkzApp.kt                    — Application: Shell init, service auto-start, locale
├── data/
│   ├── config/
│   │   └── KernelSupportConfig.kt   — Kernel signature whitelist + host mapping
│   ├── database/                    — Room: 6 entities, 6 DAOs, BatteryHistoryDatabase (v14)
│   ├── model/                       — Data classes: CpuCluster, GpuInfo, BatteryInfo, KernelInfo, etc.
│   └── repository/                  — Data layer: Root, Tuning, System, Thermal, Battery, etc.
├── di/
│   ├── AppModule.kt                — Hilt providers (Singleton scope)
│   └── Qualifiers.kt               — @ThermalSettings qualifier
├── manager/
│   ├── FpsMonitorManager.kt        — FPS overlay lifecycle
│   └── TileUpdateManager.kt        — Quick Settings tile refresh
├── receiver/
│   └── BootReceiver.kt             — BOOT_COMPLETED → restore settings + start services
├── service/                         — 8 foreground services + 2 WorkManager workers
├── tile/                            — 2 Quick Settings tiles
├── ui/
│   ├── MainActivity.kt             — Single Activity, NavHost, permission flow, FAB
│   ├── WarmStartActivity.kt         — Headless prewarm activity
│   ├── components/                  — Reusable Composables (CpuCard, GpuCard, ThermalCard, etc.)
│   ├── dialog/                      — Compose dialogs (Backup, CpuTuning, GpuTuning, etc.)
│   ├── screens/                     — 15 Compose screens
│   ├── theme/                       — Theme.kt, Type.kt, ThemeMode.kt, BlurSurfaceColors.kt
│   ├── viewmodel/                   — 4 ViewModels (BatteryInfo, Misc, Settings, StorageInfo)
├── viewmodel/                       — 11 ViewModels (Home, Main, Tuning, ProcessMonitor, etc.)
└── utils/                           — PreferenceManager, ThemeManager, KernelPaths, LocaleHelper, etc.
```

### Critical Dependency Bake

Any change to these files can ripple across the entire app. Touch with extreme care:

- `RootRepository.kt` — all root shell execution; every repository depends on it
- `TuningRepository.kt` — CPU, GPU, RAM (ZRAM), thermal, SELinux — the heart of the tuning engine
- `NkzApp.kt` — Application bootstrap, Shell init, service auto-start decisions
- `AppModule.kt` — entire DI graph; adding a repository requires a provider here
- `KernelSupportConfig.kt` — kernel verification whitelist; changing this affects who can use the app
- `BatteryHistoryDatabase.kt` — Room schema (v14); entity changes require migration or `fallbackToDestructiveMigration`
- `AndroidManifest.xml` — foreground service declarations, permission declarations, boot receiver

## Navigation Routes

| Route | Screen | Nav from |
|---|---|---|
| `home` | HomeScreen | Bottom nav |
| `tuning` | TuningScreen | Bottom nav |
| `misc` | MiscScreen | Bottom nav |
| `settings` | SettingsScreen | TopAppBar icon |
| `battery_history` | BatteryHistoryScreen | Misc |
| `custom_tunable` | CustomTunableScreen | Misc |
| `app_profiles` | AppProfilesScreen | Home |
| `bg_blocker` | BgBlockerScreen | Misc |
| `process_monitor` | ProcessMonitorScreen | Misc |
| `permission_manager` | PermissionManagerScreen | Settings |
| `dexopt` | DexoptScreen | Misc |
| `wakelock_monitor` | WakelockScreen | Misc |
| `kernel_log` | KernelLogScreen | Misc |
| `fps_monitor` | FpsMonitorScreen | Home |
| `benchmark_detail/{benchmarkId}` | BenchmarkDetailScreen | FpsMonitor |

Route patterns: `home`, `tuning`, `misc` use fade+scale transitions. All others use slide-in/out horizontally.

## Root Architecture

### Shell Execution

All root operations go through **libsu** (`com.topjohnwu.superuser.Shell`):

```
Shell.cmd("echo ...").exec()  →  Result (isSuccess, out, err)
```

The `Shell` builder is configured in `NkzApp.onCreate()`:
- `FLAG_REDIRECT_STDERR` — stderr merged into stdout
- `setTimeout(10)` — 10-second timeout
- `enableVerboseLogging = false`

`RootRepository` provides:
- `isRooted()` — `Shell.getShell().isRoot`
- `checkRootFresh()` — cached root check (500ms TTL) via `id` command
- `run(cmd, useRetry)` — execute with up to 2 retries, 100ms×attempt backoff
- `isRootStillAvailable()` — convenience wrapper around `checkRootFresh()`

### SELinux Bypass Pattern

`TuningRepository` temporarily sets SELinux to permissive for sysfs writes:

```kotlin
val originalMode = getSelinuxModeInternal()         // "Enforcing" or "Permissive"
if (originalMode == "Enforcing") {
    setSelinuxModeInternal(false)                    // setenforce 0
}
// ... execute all tuning commands ...
if (originalMode == "Enforcing") {
    setSelinuxModeInternal(true)                     // setenforce 1 — always restored
}
```

For batch operations, use `runBatchTuning(commands: List<String>)` which toggles SELinux once around the entire batch and uses a `Mutex` to prevent concurrent access.

### Shell Fallback

If `Shell.cmd().exec()` fails or throws, `TuningRepository` falls back to:

```kotlin
Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
```

The `isSuShellWorking` flag is set to `false` on first failure and all subsequent calls use the fallback path. It is never reset — a process restart is required to re-enable libsu.

### Key sysfs/procfs Paths

| Subsystem | Path | Purpose |
|---|---|---|
| CPU governor | `/sys/devices/system/cpu/{cluster}/cpufreq/scaling_governor` | Read/write governor |
| CPU freq | `/sys/devices/system/cpu/{cluster}/cpufreq/scaling_{min,max}_freq` | Frequency limits |
| CPU available | `/sys/devices/system/cpu/{cluster}/cpufreq/{scaling_available_governors,scaling_available_frequencies}` | Enumerate options |
| Core online | `/sys/devices/system/cpu/cpu{n}/online` | Hotplug control (core ≥ 4) |
| Cluster detection | `/sys/devices/system/cpu/cpu{n}/cpufreq/{affected_cpus,related_cpus}` | Dynamic cluster leader identification |
| GPU (KGSL) | `/sys/class/kgsl/kgsl-3d0/devfreq/{governor,min_freq,max_freq,cur_freq}` | GPU frequency/governor |
| GPU power level | `/sys/class/kgsl/kgsl-3d0/{default_pwrlevel,min_pwrlevel,max_pwrlevel}` | Adreno power throttling |
| GPU usage | `/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage` | Utilization % |
| Thermal | `/sys/class/thermal/thermal_message/sconfig` | Thermal mode index |
| ZRAM | `/sys/block/zram0/{disksize,reset,comp_algorithm,initstate,mm_stat}` | ZRAM config & stats |
| VM | `/proc/sys/vm/{swappiness,dirty_ratio,dirty_background_ratio,dirty_writeback_centisecs,dirty_expire_centisecs,min_free_kbytes}` | Virtual memory tuning |
| Swap | `/proc/swaps` | Active swap devices |
| BG Blocker | `/sys/kernel/n0kz_attributes/bg_blocklist` or `/sys/kernel/e404/bg_blocklist` | App blocklist (kernel-dependent) |
| Bypass charging | Kernel-specific node (via `SystemRepository`) | Pause battery charging |
| USB fast charge | Kernel-specific node | Force fast charge current |
| CPU persistence | `/data/adb/post-fs-data.d/cpu_settings.sh` | Boot-persistent CPU script (Magisk) |

`{cluster}` = dynamically identified cluster leaders (e.g., `cpu0`, `cpu4`, `cpu7`) via `getClusterLeaders()`.

## Services

All services are foreground services with `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` (Android 14+).

| Service | Type | Purpose | Key Behaviour |
|---|---|---|---|
| `ThermalService` | FG (specialUse) | Thermal watchdog | Polls every 5s; restores thermal mode + CPU freq/governor if kernel resets them; Android 15+ creates 1×1 invisible overlay window for FG start compatibility; reschedules itself via AlarmManager on task removal |
| `BatteryMonitorService` | FG (specialUse) | Precision battery tracking | Coulomb counting via `BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER`; screen-on/off drain attribution; deep sleep tracking; state persisted across reboots via device-protected SharedPreferences; bypass charging control; auto-reset on charge/discharge/threshold; 7-day graph retention; custom bitmap notification icons |
| `FpsOverlayService` | FG (specialUse) | Overlay FPS meter | Compose-based draggable overlay; `SYSTEM_ALERT_WINDOW` permission; contextual opacity; benchmark recording with full hardware telemetry; implements `LifecycleOwner`, `ViewModelStoreOwner`, `SavedStateRegistryOwner` for Compose in a Service |
| `BootRestoreService` | FG (specialUse) | Post-boot restore | Re-applies all persistent tuning on boot |
| `AppMonitorService` | FG (specialUse) | Foreground app detector | Uses `PACKAGE_USAGE_STATS` to detect foreground app; applies per-app profiles |
| `RootActionService` | FG (specialUse) | Root action executor | Executes deferred root commands |
| `DexoptService` | FG (specialUse) | Dex optimization | Triggers `cmd package compile` / `pm bg-dexopt` |
| `ThermalWorker` | WorkManager | Thermal deferred work | Used for initial thermal setup |
| `StartBatteryMonitorWorker` | WorkManager | Battery monitor bootstrap | Starts `BatteryMonitorService` from WorkManager context |

### Boot Flow

```
1. BootReceiver receives BOOT_COMPLETED (or LOCKED_BOOT_COMPLETED, USER_PRESENT, etc.)
2. Starts BootRestoreService → restores all saved tuning from DataStore
3. Starts BatteryMonitorService (if enabled in prefs)
4. Starts AppMonitorService (if enabled in prefs)
5. BootRestoreService creates post-fs-data.d scripts for persistence
```

The receiver is `directBootAware` — it can run before user unlock for critical settings.

### Application Auto-Start (`NkzApp.onCreate`)

```kotlin
if (preferenceManager.isBatteryMonitorEnabled())  → BatteryMonitorService.start(this)
if (preferenceManager.isAppMonitorEnabled())     → AppMonitorService.start(this)
```

## Quick Settings Tiles

| Tile | Service | Function |
|---|---|---|
| KGSL Zeroing | `KgslTileService` | Toggle `/sys/class/kgsl/kgsl-3d0/skip_pool_zeroing` |
| Bypass Charging | `BypassChargingTileService` | Toggle bypass charging node |

## Database (Room)

**Database:** `BatteryHistoryDatabase` (version 14, `fallbackToDestructiveMigration = true`)

| Entity | Table | PK | Purpose |
|---|---|---|---|
| `BatteryHistoryEntity` | (history) | (auto) | Historical battery readings |
| `BatteryGraphEntry` | (graph) | (auto) | Graph-plot battery data points |
| `AppProfileEntity` | `app_profiles` | `packageName` (String) | Per-app configuration (CPU/GPU JSON, thermal, bypass, KGSL, dirty PTE) |
| `CustomTunableEntity` | `custom_tunables` | `path` (String) | Custom sysfs tunables with `applyOnBoot` flag |
| `GameEntity` | `games` | `packageName` (String) | Game benchmark config (target FPS, layer pattern) |
| `BenchmarkEntity` | `benchmarks` | `id` (auto) | Full benchmark session: FPS stats, CPU/GPU usage, temps, power, frame-time data (all as JSON blobs) |

> ⚠️ `fallbackToDestructiveMigration = true` — schema changes wipe all data. If you add fields to an entity, bump the version and either write a migration or accept data loss.

## DI Graph (Hilt)

All providers are in `AppModule.kt` at `@SingletonComponent` scope:

```
Context
  ├── DataStore<Preferences> ("settings")
  ├── DataStore<Preferences> ("thermal_settings") @ThermalSettings
  ├── RootRepository
  ├── TuningRepository
  ├── SystemRepository (← RootRepository, TuningRepository)
  ├── ThermalRepository (← RootRepository, @ThermalSettings DataStore)
  ├── BatteryHistoryDatabase
  │     ├── BatteryGraphDao → BatteryGraphRepository
  │     ├── AppProfileDao   → AppProfileRepository (← RootRepository for CustomTunableRepo)
  │     ├── CustomTunableDao → CustomTunableRepository
  │     ├── GameDao          → GameRepository
  │     └── BenchmarkDao    → BenchmarkRepository
  └── PreferenceManager
```

`@ThermalSettings` qualifier separates the thermal DataStore from the general settings DataStore.

## Key Patterns & Conventions

### Reactive Data Flow
- Repositories expose `Flow<T>` for reads (e.g., `getCpuGov(cluster): Flow<String>`)
- Writes return `Boolean` (success) or `Flow<Boolean>`
- ViewModels collect flows with `collectAsStateWithLifecycle()`
- All shell reads run on `Dispatchers.IO` via `.flowOn(Dispatchers.IO)`

### Persistence Layers
| Layer | Technology | Used by |
|---|---|---|
| Tuning settings | DataStore Preferences | Tunables, thermal mode, CPU/GPU freq |
| Battery monitor state | SharedPreferences (device-protected) | BatteryMonitorService — survives reboot/FBE |
| Per-app profiles, tunables, benchmarks | Room | AppProfileRepository, CustomTunableRepository, etc. |
| Boot-persistent scripts | `/data/adb/post-fs-data.d/*.sh` | CPUManager — survives reboot via Magisk |

### Permission Flow
```
MainActivity.onCreate:
  1. installSplashScreen()
  2. mainViewModel.checkRootAndKernel()      → if no root: RootRequiredDialog (exit)
  3. if root OK: check kernel support         → if unsupported: KernelVerificationDialog (exit)
  4. if kernel OK: checkAndHandlePermissions() → BatteryOptimizationChecker
  5. BatteryOptDialog with retry limit (MAX_PERMISSION_RETRIES = 2)
  6. Android 13+: maybeRequestNotificationPermissionOnce()
```

`BatteryOptimizationChecker` validates: `FOREGROUND_SERVICE_DATA_SYNC`, `PACKAGE_USAGE_STATS`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

### Locale
`LocaleHelper.applyLanguage()` is called in both `NkzApp.attachBaseContext` and `MainActivity.attachBaseContext`. Supports English and Indonesian (`values-in/strings.xml`).

### Theming
- `ThemeManager` (Hilt-injected) controls theme mode (light/dark/system/AMOLED)
- `RvKernelManagerTheme` Compose wrapper applied in `MainActivity.setContent`
- AMOLED mode: pure black backgrounds for OLED power savings

## Testing

| Type | Framework | Location |
|---|---|---|
| Unit | JUnit 4.13.2 | `app/src/test/java/id/xms/xtrakernelmanager/` |
| Instrumented | AndroidX Test + Mockito + Robolectric | `app/src/androidTest/java/id/xms/xtrakernelmanager/` |

> ⚠️ Test packages use `id.xms.xtrakernelmanager` (old package name from Xtra Kernel Manager). Main source uses `id.nkz.nokontzzzmanager`. This mismatch is cosmetic but exists.

## Coding Conventions

1. **Kotlin idiomatic** — Coroutines, Flow, sealed classes, data classes
2. **No XML layouts** — 100% Jetpack Compose; the only XML is themes, strings, drawables
3. **Hilt injection** — `@AndroidEntryPoint` on activities/services; `@HiltViewModel` on ViewModels
4. **Shell commands** — always via `Shell.cmd().exec()` or `RootRepository.run()`; never `Runtime.exec("su")` directly (use the fallback in `TuningRepository` only)
5. **Reactive reads** — return `Flow<T>`, not suspend functions returning single values
6. **Error handling** — `runCatching` for service auto-start; try/catch with `Log.e` for shell operations; never crash on root failure
7. **Comments** — bilingual (English + Indonesian) in some files; respect existing style
8. **Strings** — all user-facing text in `res/values/strings.xml` + `res/values-in/strings.xml`
9. **No internet** — never add `INTERNET` permission or network dependencies

## Common Pitfalls

### 1. SELinux Toggle Race
`TuningRepository` toggles SELinux for every tuning write. If you add a new repository that writes sysfs, either:
- Use `TuningRepository.runBatchTuning()` (handles SELinux for you), or
- Implement the same getenforce/setenforce pattern — **always restore to enforcing**

### 2. Shell Flag Poisoning
The `isSuShellWorking` flag in `TuningRepository` is never reset to `true` after falling back. Once the fallback fires, all subsequent reads/writes use `Runtime.exec("su -c ...)` for the lifetime of the process. If you're debugging shell issues, this is likely why.

### 3. Database Migration
`fallbackToDestructiveMigration = true` means any entity change without a migration **wipes all user data**. Add migrations for production changes, or accept the trade-off deliberately.

### 4. BatteryMonitorService State Persistence
The service stores state in device-protected SharedPreferences (`battery_monitor_prefs`). On reboot, it detects `SystemClock.elapsedRealtime() < lastElapsed` and resets or carries over stats. If you add new state fields, persist AND restore them in `persistState()` / `restoreStateIfAny()`.

### 5. Android 15+ Foreground Service
`ThermalService` creates a 1×1 invisible overlay window before `startForeground()` on Android 15+ (API 36). This is required for FG start from background. If you add a new FG service that starts from background on Vanilla Ice Cream, replicate this pattern.

### 6. Navigation Route Strings
Routes are hardcoded strings in `MainActivity.kt`. There are 14 of them. If you add a screen, add the `composable()` block in the NavHost **and** the route string — both in `MainActivity.kt`. The title mapping also needs updating.

### 7. FpsOverlayService Compose-in-Service
`FpsOverlayService` manually implements `LifecycleOwner`, `ViewModelStoreOwner`, and `SavedStateRegistryOwner` to host a `ComposeView` in a Service window. This is fragile — don't refactor without understanding the Lifecycle/ViewModelStore wiring.

### 8. Post-FS-Data Scripts
`CPUManager` writes shell scripts to `/data/adb/post-fs-data.d/` for boot persistence. This directory is Magisk-specific. If the device uses KernelSU without Magisk compatibility, these scripts won't run. The code doesn't handle this case.

### 9. Package Name Mismatch
- Application ID: `id.nkz.nokontzzzmanager`
- Test packages: `id.xms.xtrakernelmanager` (inherited from XtraKM)
- Settings project name: `XtraKernelManager`

Don't "fix" this to be consistent unless you're prepared to update the application store listing, signed builds, and test package structure.

## Development Checklist

When adding a **new feature**:
- [ ] Create the Compose screen in `ui/screens/`
- [ ] Create the ViewModel in `viewmodel/` with `@HiltViewModel`
- [ ] Create or extend the repository in `data/repository/`
- [ ] Add Hilt provider in `AppModule.kt` if new repository
- [ ] Add navigation route + `composable()` block in `MainActivity.kt`
- [ ] Add route title in the `title` `when` block
- [ ] Add strings to both `values/strings.xml` and `values-in/strings.xml`
- [ ] Add any new permissions to `AndroidManifest.xml`
- [ ] If using a foreground service: declare in manifest with `foregroundServiceType="specialUse"` + subtype property
- [ ] If persistent: add to `PreferenceManager` or appropriate DataStore
- [ ] If boot-persistent: add restore logic in `BootRestoreService` or `BootReceiver`
- [ ] Test on a rooted device — no emulator can exercise root paths
