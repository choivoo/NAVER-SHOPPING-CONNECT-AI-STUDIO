# TEST REPORT — v1.0.0 (2026-09-23)

`./gradlew :app:testDebugUnitTest` → **91 tests, 91 passed, 0 failed** (JVM + Robolectric 4.16, SDK 35, native graphics).

| Suite | Tests | Covers |
|---|---|---|
| UrlSecurityTest | 5 | http/https only, file/javascript/intent schemes, localhost/private/link-local/CGNAT/IPv6, user-info, ports |
| UrlProcessorTest (+ BlogHandoffUrlTest) | 5 | URL extraction from shared text, source classification, tracking removal, compatible/unsupported |
| HtmlProductExtractorTest | 7 | JSON-LD, OpenGraph, SmartStore embedded state, login wall, CAPTCHA, prices, manual specs, prompt-injection text excluded from facts |
| JsonRepairAndRedactionTest | 5 | fenced/prose JSON, trailing commas, truncated JSON, braces in strings, secret redaction in logs |
| ClaudeClientTest | 18 | headers/body, 429+529 retry, rate-limit exhausted, 401, 404 model, refusal, empty response, 400 feature degradation, 413, timeout, not configured, Auto Best discovery (Opus 5.5 first), discovery fallback, JSON repair, validation-feedback retry, parse failure, next-model fallback |
| DemoEngineAndSchemaTest | 5 | demo pipeline compliance, titles ≥10, shortform duration, schema closure, enum coercion, verified-only fact sheet |
| ContentGuardsTest | 8 | fake experience (user vs non-user), exaggeration, missing disclosure, price/discount/review/rating mismatch, quality score & keyword stuffing, paragraph split, duplicates/content memory, HTML escaping |
| ShortsLogicTest | 13 | scene timing with transitions, split/delete/duplicate/move, undo/redo, AI auto edit, voice sync, Korean subtitle segmentation (incl. decimals), fact-only highlights, SRT round trip, mixer/ducking/limiter, synthesized audio, templates |
| RenderersTest | 8 | every card type × aspect with long Korean+emoji, 1440/2160 export, text fitting, WCAG contrast, thumbnails, frame renderer with all transitions/motions/text styles/subtitle styles, 1 and 20 scenes, WAV round trip |
| DatabaseTest | 4 | Room CRUD, versions, cascade delete, search/filter, published timestamp, backup export, generation states, version trimming, settings serialization |
| AdaptiveLayoutTest | 6 | cover 1-pane, medium 2-pane, inner 3-pane, separating hinge, error panel actions, blog renderer |
| AppSmokeTest | 5 | real app launch (Hilt/Room/DataStore/Nav) at 360dp, 840dp, 1280dp landscape, night mode: onboarding, link check, unsupported URL, tabs, settings, manual input |
| EndToEndDemoTest | 2 | full 8-step demo pipeline (cards rendered, shorts draft, thumbnail, QC) then UI walkthrough on phone and Fold-inner sizes with screenshots → `docs/screenshots/` |

## Not covered by automated tests (environment limits)
- MediaCodec/EGL MP4 encoding, Android TTS synthesis, Custom Tab OAuth, MediaStore export: require a device/emulator. No KVM → no emulator in this build environment.
- Live Claude API and live NAVER pages: no credentials in the build environment; client behaviour is verified against API-shaped mock responses.

## On-device instrumented tests (added 2026-09-24) — compiled, NOT RUN
`app/src/androidTest/java/com/shoppingconnect/aistudio/device/` — `DeviceMediaTest` (7), `DeviceAppFlowTest` (3), `DeviceNetworkTest` (2).
`./gradlew :app:assembleDebugAndroidTest` → BUILD SUCCESSFUL. They require a connected device (`./scripts/device_qa.sh`); no device was available, so **0 of 12 have been executed**.
