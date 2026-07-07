<p align="center">
  <img src="nkm-logo.png" alt="NKM Logo">
</p>
<h1 align="center" style="font-size: 48px;">N0Kontzzz Kernel Manager</h1>

![Platform](https://img.shields.io/badge/platform-Android-green?style=for-the-badge&logo=android)
![Language](https://img.shields.io/badge/language-Kotlin-purple?style=for-the-badge&logo=kotlin)
![UI](https://img.shields.io/badge/Jetpack-Compose-blue?style=for-the-badge&logo=jetpackcompose)
![License](https://img.shields.io/github/license/bimoalfarrabi/N0Kontzzz-Kernel-Manager?style=for-the-badge&refresh=1)
![Root Required](https://img.shields.io/badge/Root-Required-critical?style=for-the-badge&logo=android)
[![Repo Size](https://img.shields.io/github/repo-size/bimoalfarrabi/N0Kontzzz-Kernel-Manager?style=for-the-badge&logo=github)](https://github.com/bimoalfarrabi/N0Kontzzz-Kernel-Manager)
[![Downloads](https://img.shields.io/github/downloads/bimoalfarrabi/N0Kontzzz-Kernel-Manager/total?color=%233DDC84&logo=android&logoColor=%23fff&style=for-the-badge)](https://github.com/bimoalfarrabi/N0Kontzzz-Kernel-Manager/releases)
[![Telegram](https://img.shields.io/badge/Telegram-Join-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/n0kontzzz)

> [!CAUTION]
> **Use this application at your own risk.**
> This utility performs advanced system-level operations and kernel tuning that may impact device stability, cause data loss, or potentially damage hardware if misconfigured. The developers assume no responsibility for any issues or damages resulting from the use of this software.

**N0Kontzzz Kernel Manager** is an Android root utility specifically optimized for the POCO X3 Pro (vayu), built with Kotlin and Jetpack Compose. It gives you real-time hardware telemetry and deep kernel tuning — CPU, GPU, memory, thermal, battery, and more — through a clean Material Design 3 interface.

---

## Features

### Real-time Monitoring
Live dashboard across all critical hardware components.

- **CPU** — per-core frequency, load percentage, and temperature
- **GPU** — clock speed and utilization
- **Memory** — RAM and ZRAM usage with compression stats
- **Storage** — internal filesystem breakdown
- **Battery** — voltage, current (mA), power (W), and health

### Battery Analytics
Track full charge/discharge sessions: average and peak currents, temperatures, screen-on/off drain rates, deep sleep ratio, and charging speed. Resets on reboot, charge event, or a configured battery level.

### FPS Meter & Benchmarking
- Floating overlay showing FPS, 1% Low, and Frame Time — draggable, auto-dims when not touched
- Record sessions to generate detailed reports: Avg/Min/Max FPS, 0.1% Low, jank count, frame time jitter, CPU cluster frequencies, GPU usage, power draw, and thermal data
- Review past sessions with multi-line charts and duration summaries

### Per-App Profiles
Create per-app profiles that activate when an app comes to the foreground. Each profile can set CPU governor and frequencies (Little/Big/Prime), GPU frequency and power level, thermal profile, KGSL Skip Pool Zeroing, Avoid Dirty PTE, and Bypass Charging.

### Wakelock Monitor
Real-time tracking of system and kernel wakelocks — active count, wakeup frequency, and total prevent-suspend duration. Heuristic labels flag high-impact wakelocks to simplify idle drain debugging.

### Performance Mode
One-tap presets that set the CPU governor across all clusters simultaneously.

| Mode | Governor | Use case |
|---|---|---|
| Balanced | `schedutil` | Daily driver |
| Performance | `performance` | Gaming, benchmarks |
| Powersave | `powersave` | Extending battery life |

### CPU Tuning
Adjust min/max frequencies and governor independently for each cluster (Little, Big, Prime).

### GPU Control
Set GPU governor, min/max frequency, and power throttle level. Useful for capping heat in extended gaming sessions or unlocking peak clocks for benchmarks.

### RAM & Memory Management
- **ZRAM** — monitor usage and switch compression algorithms (LZ4, ZSTD, etc.)
- **VM tweaks** — adjust `swappiness`, `dirty_ratio`, `dirty_background_ratio`, `dirty_writeback_centisecs`, `dirty_expire_centisecs`, and `min_free_kbytes`

### Thermal Management
Apply thermal profiles controlling how aggressively the kernel throttles CPU and GPU under heat. Conservative profiles protect hardware; permissive ones allow sustained peak performance.

### Kernel Log
Live `dmesg` viewer with keyword search and full log export.

### Custom Tunables
Browse the root filesystem, read any sysfs/procfs node, write a value, and optionally persist it across reboots via the Apply on Boot switch. The interface shows both target and actual current values so you can verify immediately.

### Dexopt
Manually run ART profile compilation (`speed-profile`, `layout`) and the system background dexopt script to improve app launch times.

### Background App Blocker
Write package names to a kernel-managed blocklist (`bg_blocklist` node) to restrict background resource consumption. Requires a kernel that exposes the node.

### Bypass Charging
Power the device directly from the adapter while pausing battery charging. Reduces heat and battery wear during intensive workloads. Requires kernel support.

### Charging Control
Automate the charge cycle — stop at a user-defined ceiling, resume at a floor — piggybacking on the Battery Monitor's polling loop to avoid a separate service.

### USB Fast Charge
Force higher charging current. Increases heat; use with caution. Requires kernel support.

### KGSL Skip Pool Zeroing
Skip zero-initialization of Adreno GPU memory pages to reduce overhead and improve graphics performance. Requires kernel support.

### Avoid Dirty PTE
Prevent brute-force dirty page clearing to reduce overhead and improve benchmark consistency. Requires kernel support; may occasionally affect ZRAM consistency.

### TCP Congestion Control
Switch between available algorithms (BBR, Cubic, Reno, etc.). Optionally persist across reboots.

### I/O Scheduler Tuning
Pick the kernel I/O scheduler that fits your workload. Optionally persist across reboots.

### Intelligent Backup & Restore
Selectively back up or restore System Tuning, Network & Storage, Battery & Charging, or UI preferences. Restore validates every setting against the current kernel before applying — unsupported values are skipped, not forced.

### Modern UI
- Material Design 3 Expressive components
- Light, Dark, and System-adaptive themes
- AMOLED pure-black mode for OLED power savings
- Customizable notification icon (battery %, app logo, or transparent)
- Fully localized in English and Indonesian
- Permission Manager showing all required permissions and their status

---

## Requirements

- Kona (SM8250) / vayu (POCO X3 Pro) device running N0Kontzzz, N0kernel, FusionX, Lunar, E404R, perf+, Oxygen+, or dead-butterflies kernel
- Root access via Magisk or KernelSU

---

## Permissions

| Permission | Purpose |
|---|---|
| Root (`ACCESS_SUPERUSER`) | Kernel-level operations via `libsu` |
| Dump | Dexopt command execution and monitoring |
| Usage Access (`PACKAGE_USAGE_STATS`) | Per-App Profiles foreground detection |
| Storage (`MANAGE_EXTERNAL_STORAGE`) | Backup and restore via SAF |
| Query All Packages | Per-App Profile app list |
| Display Over Other Apps | FPS Meter overlay |
| Battery Optimization Ignore | Background monitoring stability |
| Vibrate | Haptic feedback on sliders |
| Post Notifications | Battery Monitor and Thermal Service notifications |
| Boot Completed | Re-apply profiles after reboot |
| Foreground Service | Keep monitoring services alive |

**No internet permission is requested. The app runs entirely offline.**

---

## Technology Stack

| Layer | Library |
|---|---|
| Language | [Kotlin](https://kotlinlang.org/) + Coroutines + Flow |
| UI | [Jetpack Compose](https://developer.android.com/jetpack/compose) + Material Design 3 |
| Architecture | MVVM |
| Root shell | [libsu](https://github.com/topjohnwu/libsu) |
| DI | [Dagger Hilt](https://dagger.dev/hilt/) |
| Database | [Room](https://developer.android.com/training/data-storage/room) |
| Background tasks | WorkManager + Foreground Services |
| Preferences | DataStore |
| Serialization | [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization) |
| Image loading | [Coil](https://coil-kt.github.io/coil/) |
| Navigation | Compose Navigation |

---

> [!TIP]
>
> - Start with **Balanced** (`schedutil`) for daily use — best battery-to-performance ratio.
> - Switch to **Performance** only for gaming sessions or benchmarks; temperatures rise quickly.
> - Use **Per-App Profiles** to automate this: assign Performance to your game, Balanced everywhere else.
> - Enable **Bypass Charging** during long gaming sessions plugged in to protect battery health.

---

## Repository

Issues, feature requests, and pull requests are welcome at [github.com/bimoalfarrabi/N0Kontzzz-Kernel-Manager](https://github.com/bimoalfarrabi/N0Kontzzz-Kernel-Manager).

For questions and community support, join the [Telegram group](https://t.me/n0kontzzz).

---

### Acknowledgments
- **[Xtra Kernel Manager](https://github.com/Gustyx-Power/Xtra-Kernel-Manager)** — The foundational project for this application.
- **[Danda](https://github.com/Danda420)** — For significant contributions to development and insights into Android system internals.
- **[RvKernel Manager](https://github.com/Rve27/RvKernel-Manager)** — Provided inspiration for specific features and implementation references.
- POCO X3 Pro Community — For ongoing support and feedback.
