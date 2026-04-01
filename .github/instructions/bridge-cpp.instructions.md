---
description: "Use when writing or modifying C++ bridge code: headless binary, aasdk integration, CPC/OAL session management, TCP transport, service handlers. Covers thread safety, build patterns, and aasdk v1.6 requirements."
applyTo: "bridge/openautolink/headless/**"
---
# Bridge C++ Conventions

## Build
- C++20 standard, CMake build system
- Dependencies: Boost (system, log), OpenSSL, libusb, protobuf
- Build on SBC: `cmake --build . --target openautolink-headless -j$(nproc)`
- After scp from Windows: `touch` modified files before building

## Thread Model
- **boost::asio::io_service** — single worker thread for aasdk callbacks
- **TCP accept threads** — one per port (5288 control, 5290 video, 5289 audio)
- **Output sink** — thread-safe queue with mutex; aasdk callbacks enqueue, TCP threads dequeue
- Never call aasdk methods from TCP threads directly

## aasdk v1.6 Requirements
- Protocol version 1.6 in version exchange (phones respond 1.7)
- ServiceDiscoveryResponse uses typed ServiceConfiguration (MediaSinkService, SensorSourceService, etc.)
- Old ChannelDescriptor format (v1.1) causes phone to silently ignore the response
- Must send ChannelOpenResponse for every ChannelOpenRequest — omitting causes phone disconnect after ~60s
- Must send MediaAckIndication for every video frame — omitting causes phone flow-control backup
- Phone sends PingRequests to bridge; bridge must respond. Bridge pings to phone may be ignored

## OAL Protocol Output (replacing CPC200)
- Control channel: JSON lines to TCP 5288
- Video: 16-byte header + raw codec to TCP 5290
- Audio: 8-byte header + raw PCM to TCP 5289

## Current State: CPC200 Legacy
The bridge code currently uses CPC200 framing (16-byte magic header + payload-specific sub-headers). The OAL protocol spec in docs/protocol.md is the target design. Bridge migration from CPC200 → OAL is tracked in docs/work-plan.md under "Bridge Protocol Migration".

### CPC200 Wire Format (current implementation)
- **Magic header**: 4B magic (0x55AA55AA) + 4B payload_length + 4B message_type + 4B ~message_type
- **Video payload**: 4B width + 4B height + 4B encoder_state + 4B pts_ms + 4B flags + raw codec data
- **Audio payload**: 4B decode_type + 4B volume + 4B audio_type + raw PCM
- **Heartbeat-gated writes**: Legacy from USB FFS — should be removed for TCP
- No heartbeat-gated writes, no deferred bootstrap, no magic headers
- See docs/protocol.md for full spec

## Session Modes
- `aasdk-live` — production (real phone AA session)
- `aasdk-placeholder` — modeled control flow, no phone needed
- `stub` — synthetic output for app testing

## Key Classes
- `LiveAasdkSession` — real aasdk transport, owns HeadlessAutoEntity
- `HeadlessAutoEntity` — control channel orchestration, service handlers
- `TcpCarTransport` — TCP server for app connections (ICarTransport interface)
- `HeadlessConfig` — shared config (video dims, DPI, codec, resolution tier)
