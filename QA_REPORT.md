# QA REPORT — v1.0.0

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
