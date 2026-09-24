# CHANGELOG

## Unreleased (toward 1.0.1-rc1)
### Added
- On-device QA: instrumented tests for real MediaCodec render (1080p H.264 / 720p HEVC), Android TTS, card rendering 1080/2160px, MediaStore export, share URI, app demo flow, editor input, live Claude / product URL (opt-in)
- `scripts/device_qa.sh`: install, cold start, fold/unfold states, dark mode/large font, process death, R8 release smoke, logcat crash & secret scan
### Verified
- NAVER blog hand-off default URL (`blog.naver.com/GoBlogWrite.naver`) checked live; regression test added
### Not yet done
- Real-device execution (no device connected in the build environment)

## 1.0.0 — 2026-09-23 (Official Release · Ultra Content Automation Edition)
### Added
- Link → Product pipeline (Naver Shopping, SmartStore/BrandStore, Shopping Connect short links, generic pages, manual input, optional Naver Shopping Search API)
- Multi-agent Claude pipeline with structured JSON, repair/retry, model discovery (Auto Best → Claude Opus 5.5 when available), cost tiers, prompt versioning
- Hallucination guard (FactGuard), fake-review guard, Content Guard, disclosure checks, duplicate detector, Content Memory
- Blog editor (blocks, AI rewrite actions, titles A/B, version history, quality score, facts panel, drag & drop on large screens), preview (Mobile/Fold/Desktop), publish checklist + NAVER Blog hand-off
- AI Blog Visual Design Engine: 8 card types, 7 style presets, 6 aspect ratios, palette extraction, contrast enforcement, auto layout, high-res export, Visual Editor
- Shorts Studio 2.0: multi-track timeline, 13 transitions, motion, text animations, 13 templates, hooks, safe zone, subtitles (Korean segmentation, SRT), Android TTS voice sync, synthesized BGM/SFX, mixer with ducking/limiter, thumbnail studio
- Hardware MP4 renderer (GLES → MediaCodec H.264/HEVC + AAC), render queue with foreground notification and cancel
- Foldable workspace (cover/medium/inner, hinge aware), onboarding, dashboard, batch workspace, analytics, settings, developer & storage screens, demo mode
- Reference backend (Cloudflare Worker), GitHub Actions CI, build scripts, docs
