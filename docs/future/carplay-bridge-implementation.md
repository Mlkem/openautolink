# Wireless CarPlay on OpenAutoLink Bridge — Feasibility & Implementation Plan

> **Status**: Future feature — not yet planned for any milestone.

## Key Discovery

**Wireless CarPlay does NOT require an Apple MFi chip.** The MFi authentication chip is only needed for **wired** (USB iAP2) CarPlay. Wireless CarPlay uses **HomeKit Pairing v2** — a software-only protocol based on open, well-documented cryptography (SRP-6a, X25519, ChaCha20-Poly1305).

This means we can implement a wireless CarPlay receiver on the SBC bridge using the same WiFi + BT infrastructure already in place for Android Auto.

## Protocol Stack

```
iPhone
  │
  ├── 1. Bluetooth BR/EDR pairing → bridge advertises as CarPlay accessory
  ├── 2. WiFi credentials exchange → bridge provides WiFi AP details
  ├── 3. iPhone joins WiFi AP → same AP used for AA (192.168.43.x)
  ├── 4. Bonjour discovery → bridge advertises _carplay._tcp + _airplay._tcp
  ├── 5. RTSP control session → TCP port 5000
  ├── 6. HomeKit Pairing v2 (SRP-6a) → first-time pairing with PIN
  ├── 7. Pair-Verify (X25519 + HKDF) → subsequent reconnections
  └── 8. Encrypted AirPlay streams:
       ├── Video (H.264) → TCP port 7000
       ├── Audio (ALAC/AAC/PCM) → TCP port 7001
       └── Control (RTSP) → TCP port 5000
```

## Cryptography Required (All Open Standard)

| Algorithm | Purpose | Library |
|-----------|---------|---------|
| SRP-6a (3072-bit, SHA-512) | First-time pairing (like HomeKit PIN) | libsrp6a, or custom impl |
| X25519 (Curve25519) | Key exchange for pair-verify | libsodium or OpenSSL |
| ChaCha20-Poly1305 | Transport encryption | libsodium or OpenSSL |
| AES-256-GCM | Alternative transport encryption | OpenSSL |
| HKDF-SHA-512 | Key derivation | OpenSSL |
| Ed25519 | Long-term identity signing | libsodium |

The SBC has OpenSSL 3.0 which includes all of the above.

## Key Derivation Labels

```
Control-Read-Encryption-Key     — decrypt control channel from phone
Control-Write-Encryption-Key    — encrypt control channel to phone
DataStream-Output-Encryption-Key — encrypt media/data to phone
DataStream-Input-Encryption-Key  — decrypt media/data from phone
Events-Read-Encryption-Key      — decrypt event channel from phone
Events-Write-Encryption-Key     — encrypt event channel to phone
Pair-Verify-ECDH-Salt / Info    — HKDF params for pair-verify M1
Pair-Verify-Encrypt-Salt / Info — HKDF params for pair-verify M3
```

## Bonjour Service Records

The bridge must advertise these mDNS/DNS-SD services:

```
_carplay._tcp    port 5000    — CarPlay RTSP control
_airplay._tcp    port 7000    — AirPlay media streams
```

TXT record keys for `_airplay._tcp` include device model, features bitmap, protocol version, etc.

## Bluetooth SDP Record

```
Service: Wireless iAP
UUID: 00000000-deca-fade-deca-deafdecacafe
RFCOMM Channel: 1
```

This is different from the AA BT UUID (`4de17a00-52cb-11e6-bdf4-0800200c9a66` on channel 8). Both can coexist.

## Bridge Implementation Architecture

### New Components Needed

```
bridge/openautolink/headless/
├── src/
│   ├── carplay_session.cpp          — CarPlay session manager
│   ├── carplay_rtsp.cpp             — RTSP server (port 5000)
│   ├── carplay_airplay.cpp          — AirPlay stream receiver (ports 7000/7001)
│   ├── carplay_homekit_pairing.cpp  — SRP-6a + pair-verify
│   ├── carplay_hid.cpp              — HID touch event injection
│   └── carplay_bonjour.cpp          — mDNS service advertisement
├── include/openautolink/
│   ├── carplay_session.hpp
│   ├── carplay_crypto.hpp           — ChaCha20/AES-GCM/HKDF wrappers
│   └── carplay_protocol.hpp         — RTSP message parsing
```

### Reusable Existing Infrastructure

| Component | Already Have | Reuse For CarPlay |
|-----------|-------------|-------------------|
| WiFi AP (hostapd) | ✅ 5GHz 802.11ac | Same AP, same subnet |
| BT advertising (BlueZ) | ✅ BLE + BR/EDR | Add CarPlay SDP + UUID |
| mDNS (avahi) | ✅ `_openautolink._tcp` | Add `_carplay._tcp` + `_airplay._tcp` |
| TCP transport | ✅ OAL protocol | Relay to car app via OAL framing |
| Touch forwarding | ✅ Touch pipeline | CarPlay touch format conversion |
| Video codec | ✅ H.264 pipeline | Same codec, different framing (AirPlay vs aasdk) |
| Audio pipeline | ✅ PCM/48kHz | CarPlay supports same PCM formats |

## Implementation Phases

### Phase 1: RTSP + Bonjour (Discovery)

1. Advertise `_carplay._tcp` and `_airplay._tcp` via avahi
2. Implement basic RTSP server on port 5000
3. Handle RTSP OPTIONS, DESCRIBE, SETUP, PLAY, TEARDOWN
4. iPhone should discover and attempt connection

**Validation:** iPhone shows "CarPlay available" in Settings → CarPlay

### Phase 2: HomeKit Pairing (Authentication)

1. Implement SRP-6a pair-setup (user enters PIN on car screen)
2. Store long-term public key (LTPK) after successful pairing
3. Implement pair-verify for subsequent connections (X25519 + HKDF)
4. Establish encrypted control channel (ChaCha20-Poly1305)

**Reference implementations:**
- [hap-nodejs](https://github.com/homebridge/HAP-nodejs) — HomeKit protocol in Node.js
- [pyatv](https://github.com/postlund/pyatv) — Apple TV protocol with pair-setup/verify
- [airplay2-receiver](https://github.com/openairplay/airplay2-receiver) — AirPlay 2 receiver

**Validation:** iPhone completes pairing, shows "Connected" in CarPlay settings

### Phase 3: AirPlay Streams (Media)

1. Accept AirPlay video stream (H.264) on port 7000
2. Accept AirPlay audio stream (ALAC/AAC-LC/PCM) on port 7001
3. Decrypt streams using session keys from pair-verify
4. Forward video frames to car app via OAL protocol (same as AA video path)
5. Forward audio frames to car app via OAL protocol (same as AA audio path)

**Validation:** CarPlay UI renders on car display, audio plays

### Phase 4: Input (Touch + HID)

1. Receive touch events from car app
2. Convert to CarPlay HID touch reports
3. Send via encrypted control channel to iPhone
4. Handle Siri button, home button via HID key events

**Validation:** Touch interaction works on CarPlay UI

## Key Risks

1. **Apple protocol changes** — Apple occasionally updates the AirPlay/CarPlay protocol. Future iOS updates may break compatibility.

2. **Feature bitmap** — The `_airplay._tcp` TXT record includes a features bitmap that tells the iPhone what capabilities the receiver supports. Getting this wrong causes silent connection failures.

3. **DRM/FairPlay** — Some AirPlay content uses FairPlay Streaming. CarPlay screen mirroring does NOT use FairPlay (it's raw H.264), but some audio streams might.

4. **Timing** — AirPlay is latency-sensitive. The RTSP control channel uses precise NTP-based timing for A/V sync.

## Recommendation

Start with **Phase 1 (Discovery)** to validate that the iPhone recognizes the bridge as a CarPlay receiver. This requires only Bonjour + basic RTSP — no crypto, no streams. If the iPhone connects, proceed to Phase 2.
