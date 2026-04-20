# Changes Since cf9024f1 — Detailed Intent & Status

**Baseline**: `cf9024f1` — "update README" (last known-good commit where AA connected reliably)

---

## Committed Changes

### 1. `50368246` — Multi-phone safety, boot optimization, pairing persistence (v0.1.112)
**Status**: KEEP — well-tested, working in car  
**Files**: `live_session.cpp`, `oal_session.cpp`, `aa_bt_all.py`, `install.sh`

**What it does**:
- TCP peer-IP latching: reject wireless TCP from different phone during active session
- RFCOMM startup grace: 10s head-start for default/preferred phone at boot
- Switch override file for explicit phone-switching from app UI
- Reconnect worker prefers default phone for first 3 cycles
- Pairing mode persistence to `/var/lib/openautolink/pairing_mode`
- Boot optimization: disable unused services, Python -OO, precompile .pyc (19.6s → 17.5s)
- Clean FIN on rejected RFCOMM fds, rate-limited rejection logs

**Risk**: The peer-IP latching originally rejected ALL TCP during active session (safe). This commit changed it to allow same-IP reconnect (tear-down-and-replace) which enabled the SSL handshake cycling bug. The entity-active guard in the uncommitted changes fixes this.

---

### 2. `5f3d9b48` — Use usable capacity for VEM SOC (v0.1.113)
**Status**: KEEP — EV energy model improvement  
**Files**: `live_session.cpp` (sensor handler), `oal_session.cpp` (vehicle data parsing)

**What it does**:
- Prefers `EV_CURRENT_BATTERY_CAPACITY` (usable) over `INFO_EV_BATTERY_CAPACITY` (gross)
- Adds `max_charge_power_w`, `max_discharge_power_w`, `aerodynamic_consumption_rate` to VEM
- Parses `ev_current_cap_wh` and `ev_charge_rate_w` from app vehicle_data JSON
- Caches EV battery data on bridge for instant VEM on phone reconnect

---

### 3. `532df636` — Estimate usable capacity as 88% of gross
**Status**: SUPERSEDED by e53fa257 — reverted  
**Files**: `live_session.cpp`

**What it does**: When `EV_CURRENT_BATTERY_CAPACITY` unavailable, estimate usable as 88% of gross  
**Why reverted**: Real car testing showed dashboard uses gross capacity for SOC

---

### 4. `496f8df0` — Show VEM capacity source on diagnostics (v0.1.114)
**Status**: KEEP — UI improvement  
**Files**: `DiagnosticsScreen.kt`

**What it does**: Shows capacity source (usable vs gross) with color coding on Car tab

---

### 5. `e53fa257` — Revert 88% capacity factor
**Status**: KEEP — corrects the mistake in 532df636  
**Files**: `live_session.cpp`

**What it does**: Uses raw gross capacity in fallback path, still prefers `EV_CURRENT_BATTERY_CAPACITY`

---

### 6. `e29e512d` — Improve diagnostics row readability
**Status**: KEEP — UI-only  
**Files**: `DiagnosticsScreen.kt`

**What it does**: Full-width rows, alternating backgrounds, right-aligned values

---

### 7. `9a470feb` — Eliminate config_changed restart + version logging
**Status**: MIXED — contains both critical fixes and the pixel_aspect bug  
**Files**: `live_session.cpp`, `oal_session.cpp`, `tcp_car_transport.hpp`, `headless_config.hpp`, `oal_log.hpp`, `main.cpp`, `aa_bt_all.py`, `deploy-bridge.ps1`, docs

**KEEP these sub-changes**:
- `oal_log.hpp` + BLOG macro — version-prefixed bridge logging
- `OalLog.kt` — version-prefixed app logging  
- `oal_print()` in `aa_bt_all.py` — version-prefixed BT logging
- `deploy-bridge.ps1` stamps `OAL_VERSION` in env
- HFP SLC state machine fix (AT+BRSF → CIND → CMER)
- Entity race fix: weak_ptr guard in disconnect callback
- `display_width`/`display_height` stored separately from `video_width`/`video_height` in config
- Env file upsert pattern (grep+sed||append)
- Docs: emulator connectivity, CRLF procedure

**REVERT these sub-changes**:
- Auto pixel_aspect computation from display AR — **CRASHES PHONE** with values like 14454
- `pixel_aspect_explicit` flag — unnecessary without auto-compute

**NEEDS REWORK**:
- Config change restart flow — original killed bridge process (Communication Error 6). New in-process ByeByeRequest + BT-only restart works but needs more testing
- `saveAndRestart(restartBluetooth=false)` default — phone needs BT restart to reconnect after config change

---

### 8. `9c1dbe10` — Cap diagnostics content pane width to 720dp
**Status**: KEEP — UI-only  
**Files**: `DiagnosticsScreen.kt`

---

## Uncommitted Changes (this session)

### A. Deferred `on_phone_connected` (live_session.cpp)
**Status**: VALIDATED — fixes SSL handshake Error 25  
**What**: Move `oal_session_->on_phone_connected()` from `create_entity()` to after `TLS HANDSHAKE COMPLETE` via a `handshake_complete_callback`  
**Why**: The car app's video/audio TCP connections were racing with the phone's SSL handshake on the same `io_service`, causing handshake timeouts (Error 25, Native Code 2)  
**Risk**: Low — just changes notification timing, no protocol change

### B. USB scanning disabled in wireless mode (live_session.cpp)
**Status**: VALIDATED — eliminates io_service flooding  
**What**: Skip `libusb_init()` and USB scanning when `config_.use_usb_host == false`  
**Why**: USB device scanning ran continuously on the same `io_service` as AA protocol, adding latency to every callback. Phone never connects via USB in wireless mode  
**Risk**: None for wireless-only setups. USB mode still works when `--usb` flag is passed

### C. Entity-active TCP guard (live_session.cpp)
**Status**: VALIDATED — prevents entity churn during reconnection  
**What**: Reject same-IP TCP connections only when `entity_` is alive (no time-based cooldown)  
**Why**: The multi-phone safety commit (50368246) changed the TCP accept to allow same-IP reconnect, which caused cascading entity teardowns during the phone's rapid TCP retry cycle  
**Risk**: Low — restores the original "reject while active" behavior but scoped to same-IP only

### D. `wireless_peer_ip_` preserved across disconnect (live_session.cpp)
**Status**: VALIDATED — needed for entity-active guard to work  
**What**: Don't clear `wireless_peer_ip_` in disconnect callbacks (only clear on USB mode switch or `on_host_disconnect`)  
**Why**: Disconnect callbacks were clearing the IP, defeating the entity-active guard before the next TCP arrived  
**Risk**: Low — IP is only used for the duplicate check, not routing

### E. Config change: in-process ByeByeRequest + BT-only restart (oal_session.cpp)
**Status**: PARTIALLY VALIDATED — ByeByeRequest works, BT-only restart works, but phone shows error dialog during reconnection  
**What**: For AA config changes (codec/resolution/DPI), send ByeByeRequest in-process, then restart BT only (not bridge process). Bridge stays alive.  
**Why**: Killing bridge process caused Communication Error 6. Phone needs BT restart to trigger RFCOMM → WiFi → TCP reconnection  
**Risk**: Medium — the ByeByeRequest + immediate BT restart can show Communication Error 6 on phone during the transition. Works but not clean. Needs timing optimization.

### F. RFCOMM cooldown in BT script (aa_bt_all.py)
**Status**: PARTIALLY VALIDATED — prevents RFCOMM spam but current close-only approach blocks reconnection  
**What**: 60s per-MAC cooldown after successful WiFi credential exchange. During cooldown, just `os.close(fd)` without doing the exchange  
**Why**: Phone fires RFCOMM every ~0.5s during reconnection. Each exchange triggers a new TCP:5277 connection that kills the previous handshake  
**Risk**: Medium — closing fd during cooldown may prevent phone from establishing TCP. Need to find middle ground: complete exchange but suppress the TCP somehow

### G. `oal_log.hpp` include in live_session.cpp
**Status**: KEEP — build fix  
**What**: Added `#include "openautolink/oal_log.hpp"` to `live_session.cpp`  
**Why**: `BLOG` macro was used without including the header (latent build error from 9a470feb)

### H. Auto pixel_aspect removal (oal_session.cpp)
**Status**: VALIDATED — fixes phone AA process crash  
**What**: Removed auto-compute of `pixel_aspect_ratio_e4` from display dimensions  
**Why**: `pixel_aspect=14454` (from 2914x1134 display vs 1920x1080 video) crashed the phone's Gearhead `:car` process within 1 second of SDR receipt. `CarErrorDisplayActivityImpl` appeared immediately  
**Risk**: None — reverts to pre-9a470feb behavior (pixel_aspect=0, experiment disabled)

---

## Recommended Re-implementation Order

1. **Version logging** (oal_log.hpp, OalLog.kt, oal_print) — no risk, universally useful
2. **HFP SLC fix** — phone BT connected indicator works
3. **Multi-phone safety** (50368246) — with entity-active guard fix (C)
4. **Deferred on_phone_connected** (A) — fixes handshake, zero risk
5. **USB scanning disable** (B) — eliminates noise, zero risk
6. **Entity-active TCP guard** (C) + peer IP preservation (D) — prevents cycling
7. **VEM improvements** (5f3d9b48, e53fa257) — EV energy model
8. **UI improvements** (e29e512d, 496f8df0, 9c1dbe10) — diagnostics cosmetics
9. **Config restart rework** (E) — needs more testing, do last
10. **RFCOMM cooldown** (F) — needs redesign, skip for now

## Changes to NOT re-implement
- Auto pixel_aspect computation — crashes phone
- 88% capacity factor (532df636) — already reverted by e53fa257
- Time-based TCP cooldown — blocks legitimate reconnection
