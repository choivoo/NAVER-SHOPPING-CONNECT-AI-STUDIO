# RELEASE REPORT

| | |
|---|---|
| Version | 1.0.0 (versionCode 1), package `com.shoppingconnect.aistudio` (debug: `.debug`) |
| Build date | 2026-09-23 |
| Git commit | see `git log` on branch `claude/naver-shopping-ai-studio-ultra-wq9cah` |
| Toolchain | JDK 21 · Gradle 8.14.3 · AGP 8.13.2 · Kotlin 2.2.21 · compileSdk/targetSdk 36 · minSdk 29 |
| Debug build | **PASS** — `app/build/outputs/apk/debug/app-debug.apk` (81.0 MB) |
| Release build | **PASS (unsigned)** — `app/build/outputs/apk/release/app-release-unsigned.apk` (6.7 MB, R8) |
| AAB | **PASS (unsigned)** — `app/build/outputs/bundle/release/app-release.aab` (8.6 MB) |
| Signing | No production keystore in the environment. A **dev-signed** copy (local throwaway key, not for distribution) was produced outside Git: `release-artifacts/NaverShoppingConnectAIStudio-1.0.0-release-devsigned.apk`. Configure `keystore.properties` for real signing. |
| Tests | 90 / 90 passed |
| Lint | 0 errors, 0 warnings |

Build outputs are not committed to Git; rebuild with `./build_release.sh` or download them from the GitHub Actions artifact.

## Known issues / limitations
- Not run on a physical device or emulator (no KVM): MP4 encoding, TTS, OAuth Custom Tab, MediaStore export and the R8 release build have not been executed at runtime.
- Claude API not called live (no key available here). Auto Best targets Claude Opus 5.5 (`claude-opus-5-5`) when the account lists it, then the newest Opus, then Sonnet — the actual model is chosen at runtime and shown in 설정 → AI → 연결 테스트.
- No public NAVER Blog write API is assumed; posting is a hand-off to NAVER's own editor. The web write URL (`GoBlogWrite.naver`) is configurable and was not verified live.
- NAVER Login needs an https callback → a small backend (`backend/`) or equivalent.
- Some shops block automated page access (CAPTCHA/429); the app then offers manual input or the official search API.
- Not implemented in v1.0: product background removal (cutout), QR scanning, speech-to-text from audio files (Android has no file STT API; SRT import and TTS timing are provided), drag-and-drop on the Shorts timeline (tap-to-apply is used).
