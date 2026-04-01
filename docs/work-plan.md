# OpenAutoLink — Work Plan

---

## 🔄 Carry-Forward Issues (Bridge-Side)

These exist in the current bridge code and will need fixing regardless of the app rewrite.

### 1. Video Startup Delay (High Priority)
- 65s gap between phone connect and car app connect creates stale frame backlog
- 2666 frames dropped at startup (MAX_PENDING=120 cap)
- **Fix:** Don't queue video frames until car app is connected (`client_fd_ < 0` → skip). Clear pending on new connection

### 2. Video FPS Below Target
- Stats show 28-52fps (target 60fps)
- May be bridge sending at 30fps despite `OAL_AA_FPS=60`
- **Investigate:** Verify aasdk SDR actually requests 60fps from phone

### 3. Phone AA Session Drops (Error 33)
- Phone occasionally drops TCP with EOF
- Bridge cert files not deployed → search fails on restart
- **Fix:** Deploy headunit.crt/key to `/opt/openautolink/cert/`

### 4. Black Screen After Reconnect
- Bridge rate-limits keyframe replay to 5s
- After app reconnect, no fresh IDR available
- **Fix:** Bridge should bypass rate limit on first keyframe request after new app connection

### 5. Bluetooth HFP Not Working
- BT pairing works, BLE works, RFCOMM ch8 works, WiFi credential exchange works
- HFP (Hands-Free Profile) NOT connected → no BT audio routing for calls
- **Architecture:** Phone pairs via BT to the SBC/bridge, NOT to the car. Call/voice audio must flow: Phone → BT HFP → SBC → bridge captures SCO audio → forwards over TCP to app
- **Needed for:** phone calls, voice assistant, proper AA auto-connect

---

## 🔧 Bridge Milestones

### B1: OAL Protocol Migration

**Blocks:** M3 (HFP audio), M5 (mic control), M6 (config sync), end-to-end testing

Replace CPC200 framing (16-byte magic headers, inverted checksums, heartbeat-gated writes) with OAL protocol on all three TCP channels. The app already speaks OAL — once this lands, end-to-end streaming works.

**Control Channel (Port 5288) → JSON Lines**
- [ ] JSON line messages for all control communication
- [ ] Hello handshake with capabilities exchange
- [ ] Phone connected/disconnected events
- [ ] Audio start/stop per purpose
- [ ] Nav state forwarding
- [ ] Media metadata forwarding
- [ ] Config echo on settings change
- [ ] Mic start/stop signals

**Video Channel (Port 5290) → 16-byte Header**
- [ ] OAL 16-byte header: payload_length, width, height, pts_ms, flags
- [ ] Flags: keyframe bit, codec config bit, EOS bit
- [ ] First frame must be codec config (SPS/PPS)
- [ ] Fix carry-forward #1: don't queue video until app is connected
- [ ] Fix carry-forward #4: bypass IDR rate limit on first keyframe after new app connection

**Audio Channel (Port 5289) → 8-byte Header**
- [ ] OAL 8-byte header: direction, purpose, sample_rate, channels, length
- [ ] Direction field (0=playback, 1=mic capture)
- [ ] Purpose field for routing (media/nav/assistant/call/alert)
- [ ] Bidirectional: bridge→app playback, app→bridge mic

**Touch/Input Channel (via Control 5288)**
- [ ] JSON touch events with action, coordinates, pointer array
- [ ] GNSS NMEA forwarding
- [ ] Vehicle data JSON

**Also resolves carry-forward issues:** #1 (video startup delay), #2 (fps — verify aasdk SDR config during migration), #4 (black screen after reconnect).

### B2: Bluetooth HFP + Auto-Connect

**Blocks:** M3 (call audio), M5 (mic routing), M9 (voice button)

Establish HFP profile so phone calls and voice assistant audio flow through the bridge.

- [ ] Connect HFP profile after BT pairing (currently only BLE + RFCOMM ch8)
- [ ] Capture SCO audio from HFP → forward as OAL PCM with call/assistant purpose
- [ ] Forward mic PCM from app → BT SCO for phone call uplink
- [ ] Fix carry-forward #3: deploy headunit.crt/key to `/opt/openautolink/cert/`
- [ ] AA auto-connect via BT (phone discovers bridge, starts WiFi TCP automatically)

---

## 📱 App Milestones (New Build)

See [docs/architecture.md](architecture.md) for full component island breakdown and public APIs.

### M1: Connection Foundation
- [x] Gradle project scaffold (min SDK 32, Compose, DataStore)
- [x] Transport island: TCP connect, JSON control parsing, reconnect
- [x] Session state machine (IDLE → CONNECTING → BRIDGE_CONNECTED → PHONE_CONNECTED → STREAMING)
- [x] ProjectionScreen with SurfaceView + connection status HUD

### M2: Video
- [x] MediaCodec decoder with codec selection (H.264/H.265/VP9)
- [x] OAL video frame parsing (16-byte header)
- [x] NAL parsing for SPS/PPS extraction
- [x] Stats overlay (FPS, codec, drops)

### M3: Audio
- [x] 5-purpose AudioTrack slots with ring buffers
- [x] OAL audio frame parsing (8-byte header)
- [x] Audio focus management (request/release/duck)
- [x] Purpose routing (media/nav/assistant/call/alert)
- [ ] Dual audio path support — all audio flows through the bridge via TCP: *(blocked by B1 + B2)*
  - **AA session audio** (aasdk channels): media, navigation, alerts — decoded by aasdk, sent as PCM over OAL
  - **BT HFP audio** (phone → SBC Bluetooth): phone calls, voice assistant — bridge captures SCO audio from HFP and forwards as PCM over OAL with call/assistant purpose
- [ ] Detect active audio purpose and manage focus (e.g., duck media during call)
- [ ] Handle call audio transitions: ring, in-call, call end

### M4: Touch + Input
- [x] Touch forwarding with coordinate scaling
- [x] Multi-touch (POINTER_DOWN/UP for pinch zoom)
- [x] JSON touch serialization to control channel

### M5: Microphone + Voice
- [x] Timer-based mic capture from car's mic (via AAOS AudioRecord)
- [x] Send on audio channel (direction=1)
- [x] Mic source preference: car mic (default) or phone mic, toggled in Settings
- [ ] Bridge mic_start/mic_stop control messages *(blocked by B1)*
- [ ] Coordinate mic routing: bridge forwards mic PCM to aasdk for AA voice, and to BT SCO for phone calls *(blocked by B2)*

### M6: Settings + Config
- [x] DataStore preferences (codec, resolution, fps, display mode) — basic prefs done in M1, display mode added with M2
- [x] Settings Compose UI — bridge host/port, display mode selector
- [ ] Config sync: app → bridge → echo *(blocked by B1)*
- [ ] Bridge discovery (mDNS + manual IP)

### M6b: Self-Update via GitHub Pages

Enable OTA-style self-updating so new builds can be deployed without AAB/Play Store round-trips. Critical for fast iteration on M8/M9 (no ADB access on the car).

- [ ] `REQUEST_INSTALL_PACKAGES` permission + `FileProvider` for APK sharing
- [ ] `update/` island: `UpdateManifest`, `UpdateChecker`, `AppInstaller`
- [ ] GitHub Pages manifest check (`update.json` with versionCode, APK URL, changelog)
- [ ] Download APK to app-internal cache, trigger `PackageInstaller` session
- [ ] DataStore preference: self-update enabled (default: off)
- [ ] DataStore preference: update manifest URL
- [ ] Settings UPDATES tab: toggle, URL field, Check Now button, download progress, changelog display
- [ ] Graceful failure if AAOS blocks `REQUEST_INSTALL_PACKAGES` (show user-friendly error)
- [ ] ProGuard keep rules for serialization of `UpdateManifest`

### M7: Vehicle Integration
- [x] GNSS forwarding (LocationManager → NMEA → bridge)
- [x] VHAL properties (37 properties via Car API reflection)
- [x] Navigation state display + maneuver icons

### M8: Cluster Display
- [ ] Cluster service for navigation: turn-by-turn maneuver icons, distance, road names
- [ ] Cluster service for media: album artwork, track info from Spotify/Apple Music/etc.
- [ ] Handle GM restrictions (third-party cluster services may be killed — detect and recover)
- [ ] Fallback rendering if cluster service is blocked

### M9: Steering Wheel Controls
- [ ] Media button mapping: skip forward, skip back, play/pause via `KeyEvent` interception
- [ ] Volume controls via `AudioManager` or `KeyEvent`
- [ ] Voice button interception: intercept the AAOS voice/assistant `KeyEvent` (currently launches Google Assistant) and forward as AA voice trigger to activate Gemini on the phone
- [ ] Investigate `KEYCODE_VOICE_ASSIST` / `KEYCODE_SEARCH` interception feasibility on GM AAOS (may require accessibility service or input method)

### M10: Polish
- [x] Diagnostics screen
- [x] Error recovery (reconnect, codec reset)
- [x] Display modes (fullscreen, system bars) — pulled forward, implemented with M2
- [x] Overlay buttons (settings, stats) — pulled forward, draggable floating buttons
- [x] App icon and logo — adaptive icon from brand asset
- [x] Stats for nerds overlay — monospace panel with session/video stats

---

## 🧭 Development Workflow

### One Milestone Per Conversation
Each milestone should be completed in a **single Copilot conversation**. When a milestone's exit criteria are met, **stop and tell the user to start a new conversation** for the next milestone. This keeps context focused and avoids degraded output from overly long conversations.

### How to Start Each Conversation
Open a new Copilot chat and say:
> "Let's build M[N]: [milestone name]. Start with [first task]."

Copilot will read the instruction files, repo memory, and this work plan automatically — no need to re-explain the project.

### Within a Milestone
- Prompt by island or logical task (e.g., "Build the Transport island", "Add unit tests for JSON parsing")
- Let Copilot finish each piece, verify no compile errors, then move to the next
- Copilot should check off `[ ]` items in this plan as they're completed

### Milestone Boundaries
- **Do not start the next milestone in the same conversation** — context quality degrades
- Between milestones: build, deploy to device/emulator, test manually, note any issues
- Start the next conversation with any issues or adjustments discovered during testing

### Parallel Work
Parallel Copilot sessions are **not recommended** for this project:
- Sessions don't communicate or coordinate file writes
- Build state isn't shared — one session can't see another's compile errors
- Island architecture helps in theory, but merge conflicts aren't worth the risk
- Sequential milestones have hard dependencies (M2 needs M1's transport, M3 needs M1's transport, M4 needs M2's surface, etc.)

### If a Conversation Gets Too Long
If Copilot starts losing context or producing lower quality output mid-milestone, it's fine to start a new conversation and say:
> "Continuing M[N]. [Island X] is done, [Island Y] still needs [specific tasks]."

---

## 💡 Future Ideas

### Two-Way Config Sync
- Bridge sends config echo after settings update
- App populates settings dialog from bridge echo, showing actual running config

### Stats Overlay Enhancements
- Parse SPS/PPS for actual stream resolution (not just codec init dims)
- Bridge-side stats (frames queued/dropped/written) sent via control channel
- Audio: PCM frame count, ring buffer fill level

### mDNS Discovery
- Bridge advertises `_openautolink._tcp` via Avahi
- App discovers automatically — no manual IP entry needed
- Fallback to manual IP for networks without mDNS

---

## Car Hardware Reference
- **SoC:** Qualcomm Snapdragon (2024 Chevrolet Blazer EV)
- **Display:** 2914×1134 physical, ~2628×800 usable (nav bar hidden)
- **HW Decoders:** H.264 (`c2.qti.avc.decoder`), H.265, VP9 — all 8K@480fps max
- **Network:** USB Ethernet NIC (car USB port), 100Mbps (validate this as it might be gigabit). iIt is always assigned 192.168.222.108 by GM's AAOS.
