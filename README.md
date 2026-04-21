# USB Audio PoC Milestone 1

Minimal Android audio player proof-of-concept with:

- USB DAC output path via Android USB Host permission flow + native USB isochronous skeleton
- Android fallback path via `AudioTrack`
- One shared PCM pipeline for both backends
- WAV PCM streaming only, with no whole-file buffering

## Project structure

```text
.
|-- app
|   |-- build.gradle.kts
|   `-- src/main
|       |-- AndroidManifest.xml
|       |-- cpp
|       |   |-- CMakeLists.txt
|       |   |-- descriptor_parser.cpp/.h
|       |   |-- native_bridge.cpp
|       |   |-- ring_buffer.h
|       |   `-- usb_audio_engine.cpp/.h
|       |-- java/dev/bleu/usbaudiopoc
|       |   |-- MainActivity.kt
|       |   |-- SimpleItemSelectedListener.kt
|       |   |-- audio
|       |   |   |-- AudioRouteOverride.kt
|       |   |   |-- AudioTrackBackend.kt
|       |   |   |-- PlaybackBackend.kt
|       |   |   |-- UsbAudioBackend.kt
|       |   |   |-- WavFormat.kt
|       |   |   `-- WavStreamingSource.kt
|       |   |-- player
|       |   |   |-- PlaybackController.kt
|       |   |   |-- PlayerUiState.kt
|       |   |   `-- PlayerViewModel.kt
|       |   `-- usb
|       |       |-- UsbAudioRouteManager.kt
|       |       |-- UsbNativeBridge.kt
|       |       |-- UsbPlaybackSession.kt
|       |       `-- UsbRouteState.kt
|       `-- res
|           |-- layout/activity_main.xml
|           `-- values
|-- build.gradle.kts
`-- settings.gradle.kts
```

## End-to-end flow

1. `MainActivity` lets the user pick a WAV file and choose route override.
2. `PlaybackController` opens `WavStreamingSource`, which parses RIFF/WAVE headers from a `ParcelFileDescriptor` and then streams PCM chunks from the file channel.
3. The controller chooses a backend:
   - `UsbAudioBackend` if route override allows USB and `UsbAudioRouteManager` finds a compatible DAC with permission.
   - `AudioTrackBackend` otherwise.
4. The shared PCM loop is:
   - parse WAV once
   - read bounded PCM chunk
   - write the same chunk to the selected backend
5. `UsbAudioBackend` forwards chunks through `UsbNativeBridge.writePcm()` into a bounded native ring buffer.
6. `UsbAudioEngine` parses USB Audio Streaming descriptors, selects an exact alternate setting for the requested sample rate / bit depth / channels, and runs the isochronous transfer thread.
7. `AudioTrackBackend` writes the same PCM chunks to `AudioTrack` in stream mode without any app-side DSP.

## USB path notes

- The USB backend does not resample or reformat in Milestone 1.
- Native descriptor matching is exact: if the DAC does not advertise the file's exact PCM format, the USB path fails instead of converting.
- Sample-rate control is attempted only when a UAC1-style endpoint advertises multiple discrete sample rates on the same alternate setting.
- Continuous sample-rate ranges and UAC2 clock-entity negotiation are not implemented yet; those devices should be treated as unsupported for the USB path in this milestone.

## Resource-safety design

- Kotlin file IO is streaming and uses `ParcelFileDescriptor` + `FileChannel`, not full-file reads.
- The native USB engine duplicates the Java-provided file descriptor so ownership is explicit.
- Native resources use RAII-style wrappers (`UniqueFd`, vectors, `std::thread`, bounded ring buffer).
- The ring buffer is fixed-size at 256 KiB.
- Stop/release paths close the WAV source, `UsbDeviceConnection`, `AudioTrack`, native file descriptors, and stop events.
- Blocking waits use `condition_variable`, `poll()`, and `eventfd`; there are no intentional busy-wait loops.

## Verification checklist

- No resampling in USB path
  - `DescriptorParser::FindExactMatch()` requires exact sample rate, bit depth, and channel count.
  - `PlaybackController` falls back instead of converting when USB exact match is unavailable.
- Correct sample rate negotiation
  - Exact descriptor match is required before starting USB streaming.
  - `UsbAudioEngine::ConfigureSampleRateLocked()` issues a real class-specific endpoint control transfer when the alt setting exposes multiple discrete rates.
  - Logs are emitted for descriptor parsing, stream start, sample-rate negotiation, stop, and release.
- No memory leaks during start/stop loops
  - Repeated start/stop should release `AudioTrack`, `UsbDeviceConnection`, `ParcelFileDescriptor`, duplicated USB fd, stop-event fd, and transfer thread.
  - Native heap objects are released through `nativeRelease()` and `std::unique_ptr`.
  - All buffers are bounded and reused.

## Assumptions for Milestone 1

- WAV support is PCM only, 16-bit or 24-bit little-endian.
- `AudioTrack` fallback currently supports mono and stereo WAV files.
- The USB backend is structured around real `usbdevfs` isochronous URBs, but broader DAC interoperability still needs hardware validation.
- There is no foreground playback service yet; lifecycle safety is handled through the activity/view-model boundary only.
