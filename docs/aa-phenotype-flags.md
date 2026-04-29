# Android Auto Phenotype Flags — Research & Override Guide

> **Source**: Decompiled AA APK (`com.google.android.projection.gearhead`) in `Gmaps_teardown/aa_apk_src/`.
> All flags are in the Phenotype namespace `com.google.android.projection.gearhead`.
> Research date: April 2026.

## How Phenotype Flags Work

### The Flag Chain

```
Google Server (Phenotype service)
    │  pushes configurations via GMS
    ▼
GMS Phenotype ContentProvider
  content://com.google.android.gms.phenotype/com.google.android.projection.gearhead
    │  syncs to local protobuf files
    ▼
Local protobuf snapshot
  /data/data/com.google.android.projection.gearhead/files/phenotype/<pkg>.pb
    │  read by von/vok → deserialized by voj into Map<String, Object>
    ▼
vld (PhenotypeContext) → vnt (FlagStore) → voj (snapshot map)
    │  queried by
    ▼
vng.mo37528f(vld) → looks up flag name in snapshot map
    │  cached in
    ▼
vnv (AtomicReferenceArray, 83 slots) — one slot per flag
    │  exposed via
    ▼
acfi.mo3077S() → acfg.m2994U() → boolean value used in layout code
```

### Key Classes

| Class | Role |
|-------|------|
| `acfg` | Singleton accessor — static methods read flags |
| `acfi` | Flag definitions — maps flag name + slot index + default value |
| `vnv` | Slot-based cache — `AtomicReferenceArray` with lazy init per slot |
| `vmp` | Abstract flag value — `mo1539eF()` returns the resolved value |
| `vng` | Concrete flag resolver — reads from `vld` PhenotypeContext |
| `vld` | PhenotypeContext — manages protobuf snapshot + ContentProvider |
| `von` | Protobuf file reader — `storage-info.pb`, per-package `.pb` |
| `voj` | Snapshot parser — builds `Map<String, Object>` from protobuf |
| `vkz` | **Hermetic overrides** — debug-only file-based override |
| `qwa` | Namespace config — package name + tags |
| `abtt` | Namespace registration — `"com.google.android.projection.gearhead"`, tags `CAR`, `GEARHEAD_ANDROID_PRIMES` |

### Two Storage Backends

**Backend A: Protobuf files (primary for AA)**
- Location: `<app>/files/phenotype/<subpkg>/<pkgname>.pb`
- Format: `vol` protobuf → list of `vom` (flag name + type + value)
- Types: 0=unset, 2=long, 3=bool, 4=double, 5=string, 6=bytes

**Backend B: ContentProvider queries**
- URI: `content://com.google.android.gms.phenotype/<package>`
- Projection: `["key", "value"]`
- Registers `ContentObserver` for live updates
- AA's car package (`com.google.android.gms.car`) **skips GServices** — the `m37501b()` call bypasses `content://com.google.android.gsf.gservices`

---

## Override Methods (Ranked by Feasibility)

### 1. Xposed/LSPosed Module — Hook Build Properties (Rooted Phone)

**The best option.** Google built a debug override mechanism into the AA APK. In `vkz.java`:

```java
// Only on eng/userdebug builds with dev-keys/test-keys:
if ((Build.TYPE.equals("eng") || Build.TYPE.equals("userdebug"))
    && (Build.TAGS.contains("dev-keys") || Build.TAGS.contains("test-keys"))) {

    File file = new File(
        context.createDeviceProtectedStorageContext()
            .getDir("phenotype_hermetic", 0),
        "overrides.txt"
    );
    // parse line by line...
}
```

**Steps:**
1. Install LSPosed/Xposed on rooted phone
2. Create a module that hooks `Build.TYPE` → `"userdebug"` and `Build.TAGS` → `"dev-keys"` **only in the AA process** (`com.google.android.projection.gearhead`)
3. Write `overrides.txt` to:
   ```
   /data/user_de/0/com.google.android.projection.gearhead/app_phenotype_hermetic/overrides.txt
   ```
4. Flag format — one per line, space-separated:
   ```
   <phenotype_package> <flag_name> <value>
   ```
5. Restart AA

**Example `overrides.txt`:**
```
com.google.android.projection.gearhead SystemUi__edge_to_edge_maps_enabled true
com.google.android.projection.gearhead SystemUi__widescreen_breakpoint_dp 880
com.google.android.projection.gearhead SystemUi__semi_widescreen_breakpoint_dp 600
com.google.android.projection.gearhead SystemUi__add_dynamic_resize_hero_rail true
com.google.android.projection.gearhead SystemUi__block_content_area_requests_in_immersive_mode false
com.google.android.projection.gearhead HeroFeature__force_hero_layout true
com.google.android.projection.gearhead CieloFeature__earth_enabled true
com.google.android.projection.gearhead CradleFeature__show_dpi_picker true
```

**Pros:** Uses Google's own mechanism. Survives AA updates. Tiny module. Only affects AA process.
**Cons:** Requires rooted phone with Xposed framework.

### 2. GMS Phenotype Database Edit (Rooted Phone)

Flags are cached in GMS's database:
```
/data/data/com.google.android.gms/databases/phenotype.db
```

```bash
sqlite3 /data/data/com.google.android.gms/databases/phenotype.db \
  "INSERT OR REPLACE INTO FlagOverrides \
   (packageName, flagType, name, boolVal) \
   VALUES ('com.google.android.projection.gearhead', 0, \
           'SystemUi__edge_to_edge_maps_enabled', 1);"
```

Tools like **GMS-Flags** (Android app) provide a GUI for this.

**Pros:** No Xposed needed, just root + sqlite3.
**Cons:** GMS may overwrite on next server sync. Need to re-apply periodically or use a Magisk module to make edits persistent.

### 3. Xposed Hook on Flag Reading Method (Rooted Phone)

Hook `acfi.mo3077S()` (or any specific flag method) to return custom values. More surgical than the hermetic file approach.

```java
// Pseudocode for Xposed module
findAndHookMethod("p000.acfi", lpparam.classLoader,
    "mo3077S",  // edge_to_edge_maps_enabled
    new XC_MethodReplacement() {
        @Override
        protected Object replaceHookedMethod(MethodHookParam param) {
            return true;  // force edge-to-edge
        }
    }
);
```

**Pros:** Precise control per flag. Can add logic (e.g., only enable on wide displays).
**Cons:** Method names change with each AA version (obfuscated). Maintenance burden.

### 4. Custom AA APK (Modified + Resigned)

Decompile the AA APK, patch `acfi` to hardcode desired defaults (change `false` → `true` for `edge_to_edge_maps_enabled`, `1240L` → `880L` for `widescreen_breakpoint_dp`), recompile, resign, sideload.

**Problems:**
- AA is a system app — need root to replace, or use `pm install -r --user 0`
- GMS signature verification may reject a resigned AA app
- AA updates from Play Store overwrite the mod
- Some features use SafetyNet/Play Integrity attestation

### 5. Fake ContentProvider Interposition (Rooted Phone)

Create an app with a ContentProvider at `content://com.google.android.gms.phenotype/` that intercepts AA queries and returns modified flag values, proxying everything else to the real GMS provider.

**Problems:** ContentProvider authority uniqueness — Android won't install two providers with the same authority. Would need to modify GMS itself or use Xposed to redirect the URI.

### 6. Custom ROM with userdebug Build Type

Flash a custom ROM where `Build.TYPE = "userdebug"` and `Build.TAGS` contains `"dev-keys"`. Natively enables the hermetic overrides file without any hacks.

**Pros:** Cleanest approach if you have a compatible custom ROM.
**Cons:** Most users won't flash a custom ROM just for AA flags.

### 7. No-Root: DPI Manipulation (What We Do)

Without phone root, our only lever is the SDR's DPI value. We implemented per-tier DPI scaling that influences `screenWidthDp`, which controls which layout tier AA selects (canonical / semi-widescreen / full widescreen).

This doesn't override flags but changes which layout *path* AA takes. See the per-tier DPI feature in Settings → AA Display Density.

---

## Complete Flag Reference

### SystemUi__ — Display, Layout, UI Chrome

#### Layout Breakpoints & Modes

| Flag | Type | Default | Effect |
|------|------|---------|--------|
| `edge_to_edge_maps_enabled` | bool | `false` | Maps renders to full display edge instead of stopping at card boundary |
| `widescreen_breakpoint_dp` | long | `1240` | Width in dp above which → full widescreen layout (wide cards, multi-card CoolWalk) |
| `semi_widescreen_breakpoint_dp` | long | `880` | Width in dp above which → semi-widescreen (medium cards) |
| `widescreen_aspect_ratio_breakpoint` | double | `1.67` | Aspect ratio above which → widescreen mode (5:3) |
| `portrait_breakpoint_dp` | long | `900` | Height in dp threshold for portrait mode |
| `short_portrait_breakpoint_dp` | long | `680` | Short portrait threshold |
| `horizontal_rail_canonical_breakpoint_dp` | long | `450` | Width below which → horizontal rail instead of vertical |
| `check_height_in_semi_wide_kill_switch` | bool | `true` | Check height when classifying semi-wide displays |
| `irregular_display_scrim_inset_diff_threshold_dp` | long | `100` | Inset difference threshold for irregular display detection |

#### Hero / CoolWalk Layout

| Flag | Type | Default | Effect |
|------|------|---------|--------|
| `add_dynamic_resize_hero_rail` | bool | `false` | Dynamic hero card/rail sizing based on content |
| `block_content_area_requests_in_immersive_mode` | bool | `true` | Prevent maps from extending past normal bounds in immersive |
| `use_car_service_relayout` | bool | `false` | Use car service for layout recalculation |
| `use_compose_rail` | bool | `false` | Compose-based navigation rail (new framework) |
| `set_max_lifecycle_for_video_focus` | bool | `false` | Video lifecycle management |

#### Cielo Scrim

| Flag | Type | Default | Effect |
|------|------|---------|--------|
| `cielo_scrim_with_stable_area_cutout_enabled` | bool | `false` | Cielo design scrim with display cutout |
| `cielo_scrim_with_stable_area_cutout_variable_scrim_enabled` | bool | `false` | Variable-width scrim for cutout |

#### Notifications / HUD

| Flag | Type | Default | Effect |
|------|------|---------|--------|
| `hun_default_heads_up_timeout_ms` | long | `8000` | Heads-up notification timeout |
| `hun_delay_poll_time_ms` | long | `1000` | HUN poll delay |
| `media_hun_timeout_ms` | long | `4000` | Media notification timeout |
| `media_hun_in_rail_widget_timeout_ms` | long | `8000` | Media HUN in rail widget |
| `notification_badge_with_count_millis_duration` | long | `12000` | Badge count display duration |
| `allow_hun_after_recycle_kill_switch` | bool | `true` | Allow HUN after recycling |
| `dnd_suppress_notifications_enabled` | bool | `false` | DND notification suppression |
| `mark_notifications_seen_on_close_kill_switch` | bool | `true` | Mark notifications as seen |
| `rail_notification_badge_animation_enabled` | bool | `true` | Badge animation |
| `stack_hun_buttons_if_actions_contain_text_enabled` | bool | `false` | Stack HUN buttons |

#### Theming / Wallpaper

| Flag | Type | Default | Effect |
|------|------|---------|--------|
| `wallpaper_backdrop_enabled` | bool | `false` | Phone wallpaper as AA backdrop |
| `wallpaper_backdrop_threshold` | long | `20` | Wallpaper quality threshold |
| `cache_backdrop_wallpaper_protection_color_kill_switch` | bool | `true` | Cache wallpaper color |
| `force_reload_template_on_palette_change_kill_switch` | bool | `true` | Reload template on theme change |

#### Assistant / Gemini

| Flag | Type | Default | Effect |
|------|------|---------|--------|
| `enable_gemini_in_aap_kill_switch` | bool | `true` | Gemini AI in AA |
| `use_cal_voice_plate_for_assistant` | bool | `false` | CAL voice plate for assistant |
| `use_cal_voice_plate_for_gemini` | bool | `false` | CAL voice plate for Gemini |
| `assistant_scrim_debug_background_color_enabled` | bool | `false` | Debug assistant scrim |
| `ignore_assistant_close_if_recently_opened_kill_switch` | bool | `true` | Debounce assistant close |
| `ignore_assistant_close_if_recently_opened_timeout_millis` | long | `1000` | Debounce timeout |
| `media_next_restarts_assistant_enabled` | bool | `false` | Media next restarts assistant |
| `media_next_restarts_assistant_delay_ms` | long | `1000` | Restart delay |

#### Input / Scrolling

| Flag | Type | Default | Effect |
|------|------|---------|--------|
| `inertial_scrolling_enabled` | bool | `false` | Inertial scroll in lists |
| `tuned_recycler_enabled` | bool | `false` | Tuned RecyclerView performance |
| `process_hardware_key_on_action_up` | bool | `false` | Process HW keys on key-up |

#### Miscellaneous

| Flag | Type | Default | Effect |
|------|------|---------|--------|
| `parked_app_badge_enabled` | bool | `false` | Parked app badge |
| `satellite_network_status` | bool | `false` | Satellite network indicator |
| `log_sensor_location_accuracy` | bool | `false` | GPS accuracy logging |
| `sensor_location_accuracy_distance_meters` | long | `15` | GPS accuracy threshold |
| `check_system_permission_for_microphone` | bool | `false` | Mic permission check |
| `persist_projection_configuration_context` | bool | `false` | Persist projection config |
| `suppress_bluetooth_illegal_argument_exception` | bool | `false` | Suppress BT exception |
| `navigation_cluster_allow_duplicate_updates` | bool | `true` | Allow duplicate cluster nav updates |
| `navigation_cluster_turn_event_cache_ttl` | long | `2000` | Cluster turn event TTL |
| `send_nav_focus_to_car_when_abandoned_kill_switch` | bool | `true` | Send nav focus on abandon |
| `disable_gearsnacks_on_motorcycles_kill_switch` | bool | `true` | Disable gearsnacks on motorcycles |
| `compose_icon_catch_svg_exception_kill_switch` | bool | `true` | Catch SVG exceptions |
| `enable_initial_focus_kill_switch` | bool | `true` | Initial focus management |
| `initialize_car_info_cache_faster_kill_switch` | bool | `true` | Faster car info cache init |
| `only_add_to_regions_casting_insets_if_overlapping_kill_switch` | bool | `true` | Region inset casting |
| `open_phone_settings_from_notification_kill_switch` | bool | `true` | Open phone settings from notification |
| `remove_notification_center_override_for_status_bar_kill_switch` | bool | `true` | Remove status bar override |
| `remove_rail_pre_draw_listeners` | bool | `false` | Remove rail pre-draw |
| `start_context_manager_in_lifecycle_start_updated_kill_switch` | bool | `true` | Context manager lifecycle |
| `process_font_weight_adjustment_configuration_change_kill_switch` | bool | `true` | Font weight config change |
| `unify_lifecycle_death_recipients` | bool | `false` | Unified lifecycle |
| `use_car_icon_for_notification_kill_switch` | bool | `true` | Use car icon for notifications |
| `use_default_device_for_permissions_kill_switch` | bool | `true` | Default device for permissions |
| `use_in_process_shared_prefs` | bool | `false` | In-process shared prefs |
| `use_internal_context` | bool | `false` | Internal context |
| `use_internal_context_kill_switch` | bool | `false` | Internal context kill switch |
| `use_recycle_protected_image_kill_switch` | bool | `true` | Recycle-protected images |
| `in_process_shared_prefs_keys_to_migrate` | list | (complex) | Keys to migrate |
| `oem_exit_*` | various | (complex) | OEM exit button config |
| `phone_wallpaper_in_launcher_denylist` | list | (complex) | Wallpaper denylist |
| `thermal_*` | various | various | Thermal throttling thresholds |

---

### HeroFeature__ — CoolWalk "Hero" Layout

| Flag | Type | Default | Effect |
|------|------|---------|--------|
| `enabled` | bool | `true` | Hero layout master toggle |
| `force_hero_layout` | bool | `false` | Force hero layout on all displays |
| `force_hero_vertical` | bool | `false` | Force vertical hero orientation |
| `force_cutouts` | bool | `false` | Force display cutout handling |
| `cutouts_enabled` | bool | `true` | Enable display cutout support |
| `punch_through_enabled` | bool | `false` | Punch-through rendering (transparent regions) |
| `assistant_hero_enabled` | bool | `false` | Assistant integrated into hero layout |
| `car_controls_enabled` | bool | `true` | Car controls in hero |
| `car_media_enabled` | bool | `true` | Media card in hero |
| `theming_enabled` | bool | `true` | Dynamic theming support |
| `use_new_media_ui` | bool | `false` | New media player UI |
| `radio_enabled` | bool | `true` | Radio card in hero |
| `demo_app_enabled` | bool | `false` | Demo/showcase app |
| `herosim_enabled` | bool | `false` | Hero simulation mode |
| `herosim_hmg_temp_enabled` | bool | `false` | HMG temp simulation |
| `herosim_mtc_temp_enabled` | bool | `false` | MTC temp simulation |
| `disable_assistant_button_in_reverse` | bool | `false` | Hide assistant button in reverse gear |
| `show_weather_by_default_on_portrait_kill_switch` | bool | `true` | Weather widget in portrait |
| `support_theming_in_frx_kill_switch` | bool | `true` | Theming in first-run experience |
| `enable_critical_ui_draw_check_kill_switch` | bool | `true` | Critical UI draw check |
| `assistant_transcription_aligned_by_rail_kill_switch` | bool | `true` | Transcription alignment |

---

### CieloFeature__ — Next-Gen AA UI ("Earth")

| Flag | Type | Default | Effect |
|------|------|---------|--------|
| `earth_enabled` | bool | `false` | Master toggle for "Earth" redesign |
| `earth_car_window_enabled` | bool | `false` | Earth car window mode |
| `earth_shade_enabled` | bool | `false` | Earth shade/panel UI |
| `earth_dashboard_widget_combo_enabled` | bool | `false` | Dashboard widget combos |
| `earth_companion_enhanced_ui_enabled` | bool | `false` | Enhanced companion screen |
| `cielo_status` | string | `""` | Cielo feature gate string |
| `earth_status` | string | `""` | Earth feature gate string |

---

### CradleFeature__ — Wireless AA / Desktop Mode

| Flag | Type | Default | Effect |
|------|------|---------|--------|
| `immersive_mode_enabled` | bool | `true` | Immersive mode |
| `app_controlled_immersive_mode_enabled` | bool | `false` | App-controlled immersive toggle |
| `show_dpi_picker` | bool | `false` | Show DPI picker in AA settings |
| `all_app_launcher_enabled` | bool | `false` | Full app launcher |
| `allow_video_apps` | bool | `false` | Allow video streaming apps |
| `day_night_enabled` | bool | `false` | Day/night mode |
| `promo_apps_enabled` | bool | `true` | Promotional app suggestions |
| `display_added_before_resume_kill_switch` | bool | `true` | Display lifecycle fix |
| `close_virtual_device_on_release_kill_switch` | bool | `true` | Close virtual device on release |
| `enable_browser_intent_interception` | bool | `true` | Browser intent interception |
| `add_activity_policy_exemption_baklava_kill_switch` | bool | `true` | Activity policy exemption |
| `max_parked_native_apps_forced_notifications` | long | `0` | Max forced notifications |
| `allowed_activities_list` | list | (complex) | Allowed activities |
| `app_launcher_package_list` | list | (complex) | App launcher packages |
| `app_package_list` | list | (complex) | App packages |
| `compatible_car_list` | list | (complex) | Compatible cars |
| `extended_toolbar_enabled_cars` | list | (complex) | Extended toolbar cars |
| `explicit_audio_focus_native_games` | list | (complex) | Games needing audio focus |

---

### FrameworkGalFeature__ — GAL Transport Protocol

| Flag | Type | Default | Effect |
|------|------|---------|--------|
| `fragment_size` | long | `16128` | GAL fragment size (bytes) |
| `fragment_size_for_wifi` | long | `16128` | WiFi-specific fragment size |
| `framer_send_buffer_size` | long | `16384` | Framer send buffer |
| `framer_send_buffer_size_for_wifi` | long | `16384` | WiFi send buffer |
| `max_audio_buffer_ms` | long | `900` | Max audio buffer |
| `max_hu_audio_buffer_ms` | long | `0` | Max HU-side audio buffer (0=unlimited) |
| `max_hu_audio_buffer_ms_navigation` | long | `0` | Max HU nav audio buffer |
| `default_max_hu_audio_buffer_ms` | long | `0` | Default max HU audio |
| `default_max_hu_audio_buffer_ms_navigation` | long | `0` | Default max HU nav audio |
| `stall_threshold_ms_usb` | long | `5000` | USB stall detection threshold |
| `stall_threshold_ms_wifi` | long | `5000` | WiFi stall detection threshold |
| `target_buffer_after_stall_ms_usb` | long | `250` | Target buffer after USB stall |
| `target_buffer_after_stall_ms_wifi` | long | `250` | Target buffer after WiFi stall |
| `target_buffer_after_max_buffer_reached_ms` | long | `700` | Target buffer after max reached |
| `target_hu_buffer_after_max_hu_buffer_reached_ms` | long | `0` | HU target after max |
| `target_hu_buffer_after_max_hu_buffer_reached_ms_navigation` | long | `0` | HU nav target after max |
| `default_target_hu_buffer_after_max_hu_buffer_reached_ms` | long | `0` | Default HU target |
| `default_target_hu_buffer_after_max_hu_buffer_reached_ms_navigation` | long | `0` | Default HU nav target |
| `audio_deprioritization_threshold_ms_usb` | long | `0` | USB audio deprioritization |
| `audio_deprioritization_threshold_ms_wifi` | long | `0` | WiFi audio deprioritization |
| `hu_gal_ping_timeout_delta_ms` | long | `50` | HU ping timeout delta |
| `use_sequence_numbers` | bool | `false` | Sequence number tracking |
| `close_on_reader_finish` | bool | `false` | Close on reader finish |
| `detect_hu_gal_ping_timeout` | bool | `false` | Detect HU ping timeout |
| `invoke_callback_when_bye_bye_requested_by_md` | bool | `false` | Invoke callback on bye-bye |
| `is_gal_snoop_available` | bool | `false` | GAL protocol snoop |
| `is_gal_snoop_enabled_in_starship` | bool | `false` | GAL snoop in Starship |
| `post_reader_close_on_control_channel_handler` | bool | `false` | Post reader close |
| `use_ping_configuration` | bool | `false` | Use ping config |
| `gal_ping_configuration` | list | (complex) | Ping configuration |

---

### AudioBufferingFeature__ — Audio Buffering

| Flag | Type | Default | Effect |
|------|------|---------|--------|
| `default_minimum_audio_buffers_for_wifi` | long | `8` | Default min WiFi audio buffers |
| `minimum_audio_buffers_for_wifi` | long | `4` | Min WiFi audio buffers |
| `minimum_audio_buffers_for_usb` | long | `0` | Min USB audio buffers |
| `minimum_audio_buffers_for_wifi_navigation` | long | `4` | Min WiFi nav audio buffers |
| `minimum_audio_buffers_for_usb_navigation` | long | `0` | Min USB nav audio buffers |
| `system_sound_capture_queue_frames_navigation_16khz` | long | `8` | Capture queue (16kHz) |
| `system_sound_capture_queue_frames_navigation_48khz` | long | `12` | Capture queue (48kHz) |
| `minimum_audio_buffers_for_wifi_exclusion_list` | list | (complex) | Exclusion list |

---

### AudioCodecPreferencesFeature__

| Flag | Type | Default | Effect |
|------|------|---------|--------|
| `wifi_pcm_codec_exclusion_list` | list | (complex) | Devices excluded from PCM over WiFi |

---

### VideoTimingControllerFeature__ — Video Frame Timing

| Flag | Type | Default | Effect |
|------|------|---------|--------|
| `enable_video_timing_controller` | bool | `true` | Master toggle for video timing |
| `enable_video_timing_controller_parked_exp` | bool | `true` | Parked experiment toggle |
| `video_timing_controller_max_delay_ms` | long | `17` | Max frame delay (~60fps) |
| `video_timing_controller_min_delay_ms` | long | `9` | Min frame delay |
| `video_timing_controller_max_delay_parked_exp_ms` | long | `70` | Max delay parked (~15fps) |
| `video_timing_controller_min_delay_parked_exp_ms` | long | `40` | Min delay parked |

---

### SynchronizedMediaFeature__ — A/V Sync

| Flag | Type | Default | Effect |
|------|------|---------|--------|
| `enable_sync` | bool | `false` | A/V sync master toggle |
| `enable_sync_parked_exp` | bool | `true` | Parked experiment sync |
| `audio_buffer_limit_ms` | long | `300` | Audio buffer limit |
| `audio_buffer_limit_ms_tts` | long | `400` | TTS audio buffer limit |
| `audio_buffer_limit_parked_exp_ms` | long | `300` | Parked audio buffer |
| `audio_buffer_limit_parked_exp_ms_tts` | long | `400` | Parked TTS buffer |
| `max_lag_tolerance_ms` | long | `20` | Max acceptable A/V lag |
| `max_lag_tolerance_parked_exp_ms` | long | `20` | Parked max lag |
| `sync_interval_ms` | long | `1000` | Sync check interval |
| `sync_interval_parked_exp_ms` | long | `1000` | Parked sync interval |
| `max_audio_delay_allowed_ms` | long | `500` | Max audio delay |
| `max_audio_delay_allowed_parked_exp_ms` | long | `500` | Parked max audio delay |
| `max_video_delay_allowed_ms` | long | `500` | Max video delay |
| `max_video_delay_allowed_parked_exp_ms` | long | `500` | Parked max video delay |
| `average_lag_weight` | long | `4` | Lag averaging weight |
| `average_lag_weight_parked_exp` | long | `4` | Parked lag weight |
| `sync_offset_override_ms` | long | `0` | Manual sync offset |
| `sync_offset_override_parked_exp_ms` | long | `0` | Parked offset |
| `enable_media_stats` | bool | `true` | Media stats collection |
| `max_media_stats_reports_to_dump` | long | `20` | Stats dump limit |
| `media_stats_report_interval_ms` | long | `5000` | Stats interval |
| `require_media_options_for_av_sync_kill_switch` | bool | `true` | Require media options |

---

### BufferPoolsFeature__

| Flag | Type | Default | Effect |
|------|------|---------|--------|
| `crash_on_buffer_leak_threshold_video` | long | `30` | Crash threshold for leaked video buffers |
| `max_pool_capacities` | list | (complex) | Buffer pool size config |

---

### AudioDiagnosticsFeature__ / VideoDiagnosticsFeature__

| Flag | Type | Default | Effect |
|------|------|---------|--------|
| `audio_stats_logging_period_milliseconds` | long | `30000` | Audio stats logging interval |
| `log_audio_latency_stats_telemetry_threshold_usb` | long | `700` | USB latency telemetry threshold |
| `log_audio_latency_stats_telemetry_threshold_wifi` | long | `1400` | WiFi latency telemetry threshold |
| `max_events_per_diagnostics_message` | long | `128` | Max events per diagnostics msg |
| `publishing_period_millis` | long | `1000` | Diagnostics publishing period |
| `report_audio_latency_stats_interval_ms` | long | `30000` | Latency stats interval |
| `video_stats_logging_period_ms` | long | `300000` | Video stats period (5min) |
| `video_stats_period_focused_time_only` | bool | `false` | Only log when focused |
| `max_media_stats_reports_to_dump` (video) | long | `20` | Video stats dump limit |
| `audio_latency_histogram_intervals_ms` | list | (complex) | Histogram buckets |
| `audio_video_lag_histogram_intervals_ms` | list | (complex) | A/V lag histogram |
| `audio_video_perceived_lag_histogram_intervals_ms` | list | (complex) | Perceived lag histogram |
| `video_latency_histogram_intervals_ms` | list | (complex) | Video latency histogram |

---

### CarConnectionHandoffFeature__

| Flag | Type | Default | Effect |
|------|------|---------|--------|
| `bypass_first_activity` | bool | `false` | Bypass first activity launch |
| `check_frx_timeout_ms` | long | `2000` | First-run experience timeout |
| `never_fall_back_to_gms_core` | bool | `true` | Never fallback to GMS Core |
| `should_fall_back_to_gms_core` | bool | `true` | Should fallback to GMS Core |
| `validate_start_connection_intents_via_handshake` | bool | `true` | Validate connection intents |
| `handoff_handlers` | list | (complex) | Handoff handler config |

---

### HeadUnitFeature__

| Flag | Type | Default | Effect |
|------|------|---------|--------|
| `max_notifications_for_head_unit` | long | `5` | Max notifications |
| `show_update_notifications_enabled` | bool | `false` | Show update notifications |
| `update_notification_frequency` | long | `50` | Update notification frequency |
| `known_updates` | list | (complex) | Known HU updates |
| `hkmc_min_updates` | list | (complex) | Hyundai/Kia min updates |

---

### Other Feature Flags

| Flag | Type | Default | Effect |
|------|------|---------|--------|
| `AceFeature__maximum_ace_wait_time` | long | `10000` | Max ACE wait time |
| `AceFeature__mode` | long | `0` | ACE mode |
| `ApolloFeature__apollo` | bool | `false` | Apollo media UI |
| `ApolloFeature__expressive_enabled` | bool | `false` | Expressive media UI |
| `CakewalkFeature__usb_reset_enabled` | bool | `false` | USB reset feature |
| `CakewalkFeature__usb_reset_timeout_ms` | long | `3000` | USB reset timeout |
| `CakewalkFeature__dont_show_again_enabled` | bool | `false` | Don't-show-again option |
| `CakewalkFeature__dont_show_again_dismiss_count` | long | `3` | Dismiss count |
| `CakewalkFeature__phase_1_75_block_on_unlock_patch_enabled` | bool | `false` | Unlock patch |
| `CarInfoSanitizerFeature__possible_pii_in_car_model_list` | list | (complex) | PII sanitization |
| `DpadAsTouchpadFeature__enable_dpad_as_touchpad` | bool | `false` | D-pad as touchpad |
| `FrameworkAudioSaverFeature__audio_saver_storage_limit` | int | `104857600` | Audio saver storage (100MB) |
| `NeoplanFeature__enabled` | long | `0` | Neoplan feature |
| `PhoneFeature__use_standard_template_for_contacts` | bool | `false` | Standard contact template |
| `PhoneThemeFeature__enable_dynamic_font_family` | bool | `false` | Dynamic fonts |
| `PhoneThemeFeature__cache_alternative_system_app_icon_components` | bool | `false` | Icon component caching |
| `ProjectedAppsFeature__enabled` | bool | `false` | Projected apps (third-party on AA) |
| `RhdFeature__rhd_default` | bool | `false` | Right-hand drive default |
| `VersionTenFeature__version_ten_launch_param` | bool | `false` | Version 10 launch |
| `WirelessFrxFeature__dongle_device_name_matches` | string | `"Intercooler"` | Wireless dongle name |
| `WirelessFrxFeature__min_os_api_number` | long | `29` | Min API for wireless |
| `WirelessFrxFeature__show_if_location_services_disabled` | bool | `true` | Show if location off |
| `WirelessFrxFeature__show_if_missing_location_permission` | bool | `true` | Show if no location perm |
| `WirelessFrxFeature__activity_in_gearhead_to_launch` | string | `"com.google.android.apps.auto.components.frx.phonescreen.WirelessConnectingActivity"` | Wireless setup activity |
| `WorkAppsFeature__work_apps_enabled` | bool | `false` | Work profile apps |
| `SenderlibCertFeature__*` | string | `""` | Certificate/key table entries |
| `SystemHealthFeature__*_power_rails` | list | (complex) | Power monitoring rails |
| `SystemHealthFeature__odpm_api_refresh_interval_ms` | long | `30000` | ODPM refresh interval |

---

## Display Layout Classification

AA classifies displays into layout modes based on `screenWidthDp`:

```
screenWidthDp = pixelWidth × 160 ÷ DPI
```

| Layout Mode | Condition | Card Width |
|-------------|-----------|------------|
| Canonical | < 880dp | `dashboardBounds / shadeBounds` ratio |
| Semi-widescreen | 880–1240dp | `1 / (numCards + 1)` ≈ 33% |
| Full widescreen | > 1240dp OR AR > 1.67 | `1 / (numCards + 1)` ≈ 33% |

Layout type enum (`yfj.java`):
```
UNKNOWN(0), STANDARD(1), STANDARD_VERTICAL_RAIL(2),
STANDARD_VERTICAL_RAIL_RTL(3), WIDESCREEN(4), WIDESCREEN_RTL(5),
STANDARD_TOP_RAIL(6), CLUSTER_STANDARD(7), CLUSTER_WITH_LAUNCHER(8),
AUXILIARY_STANDARD(9), PORTRAIT_STANDARD(10), PORTRAIT_SHORT(11),
SEMI_WIDESCREEN(12), SEMI_WIDESCREEN_RTL(13),
SHORT_CANONICAL(14), SHORT_CANONICAL_RTL(15)
```

Semi-widescreen (12, 13) maps to **CANONICAL** display mode, not WIDESCREEN. It uses canonical card layout but with different card proportions.

---

## Recommended Override Sets

### "AAOS-like Maps" (wider maps, narrower search results)

```
com.google.android.projection.gearhead SystemUi__edge_to_edge_maps_enabled true
com.google.android.projection.gearhead SystemUi__block_content_area_requests_in_immersive_mode false
com.google.android.projection.gearhead SystemUi__add_dynamic_resize_hero_rail true
```

### "Compact Layout" (narrower cards, less wasted space)

```
com.google.android.projection.gearhead SystemUi__widescreen_breakpoint_dp 880
com.google.android.projection.gearhead SystemUi__semi_widescreen_breakpoint_dp 600
```

### "Next-Gen UI" (experimental — may crash)

```
com.google.android.projection.gearhead CieloFeature__earth_enabled true
com.google.android.projection.gearhead CieloFeature__earth_car_window_enabled true
com.google.android.projection.gearhead CieloFeature__earth_shade_enabled true
```

### "Full Hero Mode"

```
com.google.android.projection.gearhead HeroFeature__force_hero_layout true
com.google.android.projection.gearhead HeroFeature__punch_through_enabled true
com.google.android.projection.gearhead HeroFeature__force_cutouts true
com.google.android.projection.gearhead HeroFeature__assistant_hero_enabled true
com.google.android.projection.gearhead HeroFeature__use_new_media_ui true
```

### "DPI Picker + Immersive"

```
com.google.android.projection.gearhead CradleFeature__show_dpi_picker true
com.google.android.projection.gearhead CradleFeature__app_controlled_immersive_mode_enabled true
```

### "Audio/Video Tuning" (performance)

```
com.google.android.projection.gearhead SynchronizedMediaFeature__enable_sync true
com.google.android.projection.gearhead FrameworkGalFeature__fragment_size_for_wifi 32256
com.google.android.projection.gearhead FrameworkGalFeature__framer_send_buffer_size_for_wifi 32768
com.google.android.projection.gearhead AudioBufferingFeature__minimum_audio_buffers_for_wifi 2
```
