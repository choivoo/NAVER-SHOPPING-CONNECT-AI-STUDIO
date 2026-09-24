# QA REPORT — v1.0.0 (+ v1.0.1 real-device QA attempt)

Environment: Linux container, JDK 21, Android SDK 36, **no emulator/device (no KVM)**. "PASS (Robolectric)" = verified by running the real app UI/code under Robolectric; "Build-verified" = compiles and is wired, not executed on hardware.

| Test | Result | Notes |
|---|---|---|
| Gradle sync / compile | PASS | Kotlin 2.2.21, AGP 8.13.2, compileSdk 36 |
| assembleDebug | PASS | 81 MB (unminified, debug) |
| assembleRelease (R8) | PASS | 6.7 MB unsigned; dev-signed copy verified with apksigner |
| bundleRelease | PASS | 8.6 MB AAB |
| Lint | PASS | 0 errors, 0 warnings (style checks UseKtx/ModifierParameter/ObsoleteSdkInt disabled) |
| Unit + Robolectric tests | PASS | 90/90 |
| App launch | PASS (Robolectric) | Hilt graph, Room, DataStore, navigation |
| Onboarding | PASS (Robolectric) | 5 pages, skip/start |
| URL input / link check | PASS (Robolectric) | Compatible / Unsupported states |
| Product analysis → blog → cards → shorts → QC | PASS (Robolectric, demo engine) | EndToEndDemoTest |
| Blog editor / titles / preview / publish checklist | PASS (Robolectric) | screenshots phone + fold |
| Shorts studio preview & timeline | PASS (Robolectric) | real FrameRenderer frame |
| Claude API | PASS (mock server) | not called live — no API key in environment |
| NAVER OAuth | Build-verified | needs registered app + https callback |
| Blog hand-off (clipboard, gallery, open app/web) | Build-verified | |
| TTS / voice sync | Logic PASS, synthesis build-verified | TextToSpeech needs device voices |
| MP4 render (GLES + MediaCodec + AAC) | Build-verified | needs device encoder |
| Save / Share (MediaStore, Sharesheet) | Build-verified | |
| Fold cover (360dp) | PASS (Robolectric) | single column, bottom bar |
| Fold inner (~904dp) | PASS (Robolectric) | rail + 3-pane workspace; center pane widened after QA |
| Tablet / landscape | PASS (Robolectric) | 1280dp |
| Dark mode | PASS (Robolectric) | night qualifier |
| Restart / persisted data | PASS (Robolectric) | settings & projects persist across launches in-process |

## Issues found during QA and fixed
1. Fold inner (~900dp): 3-pane editor center column too narrow (title wrapped every 3 characters) → side panes now scale with width.
2. Subtitle segmentation split "5.3" / "1,200" → punctuation splits no longer occur inside numbers (+ regression test).
3. Home on small phones: primary CTA pushed below the fold by the AI setup banner → banner moved below the link card.
4. Notifications never shown on Android 10–12 (runtime permission check on API < 33) → fixed.
5. Invalid dark surface color hex (lint) → fixed.
6. Demo titles duplicated "[데모]" → fixed.
7. Unused RECORD_AUDIO permission removed.

Note: product/card images in Robolectric screenshots are blank because Coil loads asynchronously; card bitmaps themselves are rendered and asserted in RenderersTest/EndToEndDemoTest.

---

## v1.0.1 Real-device QA — 2026-09-24

**Result: REAL DEVICE NOT CONNECTED.** `adb devices -l` returned no devices; the build container has no USB bus (`/dev/bus/usb` absent) and no KVM (no emulator). No device result below is claimed.

| Item | Robolectric (JVM) | Galaxy Z Fold (real device) |
|---|---|---|
| App launch | PASS | NOT TESTED |
| Fold cover (360dp) | PASS | NOT TESTED |
| Fold inner (~904dp) | PASS | NOT TESTED |
| Fold ↔ Unfold transition | n/a (config change not simulated) | NOT TESTED |
| Onboarding | PASS | NOT TESTED |
| Demo pipeline (WorkManager worker) | PASS (orchestrator called directly) | NOT TESTED |
| Blog editor input / undo / redo | n/a | NOT TESTED |
| Image cards 1080 / 2160 | PASS (native Canvas) | NOT TESTED |
| Android TTS (Korean) | n/a | NOT TESTED |
| MP4 render (MediaCodec H.264) | n/a | NOT TESTED |
| Playback / Gallery / Share | n/a | NOT TESTED |
| NAVER login | n/a | NOT CONFIGURED (no Client ID) |
| NAVER blog hand-off URL | — | URL verified over HTTP (below); app transition NOT TESTED |
| Claude API | mock server PASS | NOT TESTED — NO API KEY |
| Real product URL | fixtures PASS | NOT TESTED — no URL provided |
| Release (R8) launch | n/a | NOT TESTED |

### Checks that did not need a device (done)
| Check | Result | Notes |
|---|---|---|
| Release manifest permissions | PASS | INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, FOREGROUND_SERVICE(+DATA_SYNC, MEDIA_PROCESSING); WAKE_LOCK / RECEIVE_BOOT_COMPLETED come from WorkManager. No RECORD_AUDIO, no storage/media permissions |
| Cleartext traffic | PASS | network security config forbids cleartext |
| Logging paths | PASS | all app logging goes through `AppLog` → `Redactor`; no `println`/`printStackTrace`; no OkHttp logging interceptor; release strips `Log.d/v` |
| Blog hand-off default URL | PASS | `https://blog.naver.com/GoBlogWrite.naver` → 302 to NAVER login with return URL (valid). `m.blog.naver.com/GoBlogWrite.naver` and `PostWriteForm.naver` → not found. Default kept; regression test added |

### Ready-to-run device QA
`./scripts/device_qa.sh [--release] [--allow-reinstall]` (optionally `CLAUDE_API_KEY=… PRODUCT_URL=…`) runs, on a connected phone:
device info → install (stops on signature conflict unless `--allow-reinstall`) → cold start timing → fold/unfold/fold via `cmd device_state` with screenshots, process/Activity-restart check → dark mode + 1.3× font → instrumented tests (`app/src/androidTest/.../device/`) → process-death relaunch → optional R8 release smoke → logcat scan (FATAL/ANR/OOM/SecurityException + secret leakage).
Instrumented tests: device info & encoder list, Hero/Feature/Spec/CTA cards at 1080 and 2160px, Korean TTS, **30 s 1080×1920 H.264 render with TTS/subtitles/BGM/transitions verified via MediaExtractor (tracks, size, duration, black frames, rotation)**, 720p HEVC if supported, MediaStore save + content-URI share, real app demo pipeline through WorkManager, editor Korean/English/number/emoji input + undo/redo + autosave, Shorts Studio and Settings screenshots, live Claude (with key) and live product URL (with URL).
Results are written to `docs/device-qa/<timestamp>/SUMMARY.md`.
