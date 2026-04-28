---
description: "Use when modifying display scaling, video resolution, DPI, crop/letterbox mode, width_margin, height_margin, pixel_aspect_ratio, or any SDR video configuration. Contains the complete aspect ratio compensation system."
applyTo: "app/**"
---
# Display Aspect Ratio Compensation — Complete Guide

## The Problem

Car displays are rarely 16:9. AA encodes video at standard 16:9 resolutions (1920×1080, 3840×2160).
If we stretch a 16:9 video to fill a wider display, circles become ovals.

## The Solution: width_margin / height_margin

`width_margin` tells the phone to **extend the rendered UI width** beyond the standard 16:9 resolution.
The phone encodes the FULL width including the margin, so the video's aspect ratio matches the
display's aspect ratio. No stretching. No black bars. Circles stay circular.

For portrait displays (taller than 16:9), `height_margin` extends the height instead.

### Formula

```
Wide display (landscape car HU):
  width_margin = displayAR × videoHeight - videoWidth
  e.g. S21 2340×1080 (2.17:1) at 1080p: 2.167 × 1080 - 1920 = 420

Tall display (portrait car HU):
  height_margin = videoWidth / displayAR - videoHeight
  e.g. 1080×1920 (0.56:1) at 1080p: 1920 / 0.5625 - 1080 = 2333
```

### How the Phone Handles It

The margin is set per video_config in the SDR. The phone **scales the margin proportionally** when
it picks a higher resolution tier. So a margin of 420 computed for 1080p works correctly even when
the phone auto-negotiates to 4K — the phone scales it to ~840 for 3840×2160.

## ⚠️ CRITICAL: pixel_aspect_ratio_e4 Does NOT Work

**DO NOT rely on `pixel_aspect_ratio_e4` for aspect ratio compensation.**

Despite being a documented protobuf field, it is **not honored** by the phone's AA encoder in
practice. Tested on OnePlus 13 with AA v16.7 — the field is sent correctly in the SDR but the
phone does not pre-shrink the encoded video. Additionally, Qualcomm MediaCodec decoders **ignore**
both `VIDEO_SCALING_MODE_SCALE_TO_FIT` and `VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING` — they
always stretch the video to fill the Surface regardless of the scaling mode set.

The `pixel_aspect_ratio_e4` field is kept in the code as a manual override option but is NOT used
in the auto-compute path. `width_margin` is the correct mechanism.

## Data Flow (End to End)

```
SessionManager.startSession()
    ↓
WindowManager.currentWindowMetrics.bounds → displayW × displayH
    ↓
displayAR = displayW / displayH
videoAR = resW / resH (from selected resolution, e.g. 1920/1080)
    ↓
if scalingMode == "crop" && displayAR > videoAR * 1.02:
    computedWidthMargin = displayAR × resH - resW    (wide display)
else if scalingMode == "crop" && displayAR < videoAR * 0.98:
    computedHeightMargin = resW / displayAR - resH   (tall display)
else:
    no margin needed (display matches 16:9 or letterbox mode)
    ↓
AasdkSdrConfig(marginWidth = computedWidthMargin, marginHeight = computedHeightMargin)
    ↓
C++ SDR builder: vc->set_width_margin(margin) for each video_config
    ↓
Phone AA renders UI at (videoWidth + margin) × videoHeight
    ↓
Encoded video AR matches display AR → no stretching
```

## CRITICAL RULES — DO NOT VIOLATE

### Rule 1: width_margin is the ONLY mechanism for aspect ratio compensation
- `pixel_aspect_ratio_e4` does not work — phone ignores it
- `MediaCodec.setVideoScalingMode()` does not work — Qualcomm ignores it
- `width_margin` / `height_margin` is what headunit-revived uses and what works

### Rule 2: Auto-compute only in crop mode
- **Crop mode** (`fillMaxSize`): video fills full display → margin needed to match AR
- **Letterbox mode** (`aspectRatio(16f/9f)`): SurfaceView constrained to 16:9 → no margin needed

### Rule 3: Margins are computed from DISPLAY dimensions, not video resolution
The display is the physical constant. The margin adjusts the phone's render viewport to match it.
If the user overrides resolution or DPI, the margin is still computed from the display.

### Rule 4: The margin goes in EVERY video_config in the SDR
Whether auto-negotiate (all tiers) or manual (single tier), every `video_config` entry must have
the margin set. The phone scales it proportionally to the selected resolution.

### Rule 5: Manual override via settings
Users can override with `aa_width_margin` / `aa_height_margin` in settings. If either is > 0,
auto-compute is bypassed and the manual value is used as-is.

## Verification

Take a screenshot and measure the Spotify icon (green circle in the AA dock bar at the bottom).

```powershell
adb -s <device> shell screencap -p /sdcard/check.png
adb -s <device> pull /sdcard/check.png
```

**Find the Spotify green circle or Phone white circle** — NOT our Compose overlay icons (settings
gear, info button) which are on the right side. Measure width vs height of the icon. They should
be equal within 5%.

```python
# Quick check script
from PIL import Image
img = Image.open('check.png')
pixels = img.load()
w, h = img.size
for sx in range(0, w-120, 10):
    rows = []
    for y in range(h-80, h):
        le = re = None
        for x in range(sx, min(sx+120, w)):
            r, g, b = pixels[x, y][:3]
            if (r+g+b)/3 > 15:
                if le is None: le = x
                re = x
        if le and 20 < (re-le) < 100:
            rows.append(re-le+1)
    if len(rows) > 25:
        mw = max(rows)
        if 30 < mw < 100:
            ratio = mw / len(rows)
            if 0.7 < ratio < 1.5:
                print(f'x~{sx}: W={mw} H={len(rows)} ratio={ratio:.3f}',
                      'CIRCLE' if 0.9 < ratio < 1.1 else '')
```

## History

- 2026-04-26: Initial pixel_aspect_ratio_e4 implementation — sent correctly but phone ignores it
- 2026-04-27: Tested SCALE_TO_FIT and SCALE_TO_FIT_WITH_CROPPING — Qualcomm decoder ignores both
- 2026-04-27: Discovered width_margin approach from headunit-revived + AA APK teardown
- 2026-04-27: Implemented auto-computed width_margin/height_margin — circles confirmed circular
- pixel_aspect_ratio_e4 kept as manual override option but NOT used in auto path
