# Android Glasses

Android app for smart glasses with two playback modes:

- Camera mode: Fullscreen RTSP stream
- Video mode: Local `.mp4` playback with optional Google Drive sync

## Functional requirements

### Camera mode

- Display a single RTSP camera stream in fullscreen.
- Support 16:9 and 4:3 external displays.
- Use "cover" scaling: maintain aspect ratio and center-crop as needed to avoid black bars.
- Keep the stream running continuously; auto-reconnect on transient network errors.
- Show a lightweight error overlay if the stream cannot be started (e.g., invalid URL, no network).

### Video mode

- Discover and play all `.mp4` videos from device storage and attached USB flash storage.
- Only `.mp4` containers are in scope for playback.
- Provide a simple list/grid to select and play videos, or auto-play all found videos sequentially.
- Continue playback when offline.

## Non-functional requirements

- Start quickly; UI remains responsive during scans and syncs.
- Handle missing permissions gracefully with clear prompts.
- Operate reliably in intermittent connectivity scenarios (especially for Camera mode reconnects).
- Minimal, distraction-free UI suitable for glasses displays.

## Configuration and permissions

- RTSP source URL(s) are configurable in settings.
- Storage access permission is required to scan and read local/USB videos.
- Automatically detect new USB device attached to trigger scanning and syncing
- Network access is required for RTSP and Drive sync.

## Assumptions

- Device supports hardware decoding for common `.mp4` content; unsupported files will be skipped without breaking the UI or process
- External display may be 16:9 or 4:3; scaling uses cover to avoid letterboxing.
- "Flash storage" refers to internal shared storage and/or attached USB mass storage.

## Acceptance criteria

- Camera mode shows the RTSP stream fullscreen, without black bars on 16:9 and 4:3 displays.
- Camera mode automatically attempts to reconnect after a transient drop and surfaces failures.
- Video mode finds `.mp4` files on local storage only
- Detect USB device and trigger auto-syncing
- The app functions without crashes during continuous playback or streaming for at least 30 minutes.
