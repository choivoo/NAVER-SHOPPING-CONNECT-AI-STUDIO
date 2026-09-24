# NAVER Shopping Connect AI Studio · v1.0.0

> 상품 링크 하나로 블로그와 숏폼까지. — Android / Galaxy Z Fold 최적화 네이티브 앱 (Kotlin · Jetpack Compose)

쇼핑 커넥트(또는 상품) 링크를 붙여넣고 **[AI 콘텐츠 만들기]** 를 누르면 멀티 에이전트 파이프라인이
상품 분석 → 사실 검증 → 콘텐츠 전략 → 블로그 글 → 이미지 카드 → 9:16 숏폼 드래프트 → 품질 검사까지 만들고,
사용자는 검수 후 **네이버 블로그 편집 화면에서 직접 게시**하고, 숏폼은 기기에서 **MP4로 렌더링·저장·공유**합니다.

> 이 앱은 NAVER의 공식 앱이 아닙니다. 비밀번호를 수집하지 않고, 자동 대량 게시·허위 후기·허위 가격 생성을 하지 않도록 설계되었습니다.

## 주요 기능

| 영역 | 내용 |
|---|---|
| 링크 처리 | 쇼핑 커넥트/`naver.me` 단축 링크, 스마트스토어, 네이버 쇼핑, 일반 상품 페이지. 리다이렉트 추적(최대 5회, 매 hop SSRF 검사), 트래킹 파라미터 제거(제휴 링크는 원본 유지), JSON-LD·앱 상태·OpenGraph·마이크로데이터·스펙 표 추출. 실패 시 **직접 입력** 또는 **네이버 쇼핑 검색 API**(선택) |
| 데이터 원칙 | 확인된 필드만 `verifiedFields`, 나머지는 `unknown`. AI에는 검증된 Fact Sheet만 전달하고 페이지 텍스트는 데이터로만 취급(프롬프트 인젝션 방어) |
| AI (Claude) | 에이전트별 구조화 JSON(JSON Schema) 호출: Product · Strategy · Writer · Title · VisualStrategy · Shortform · Compliance · Rewrite · Hook · Thumbnail. **Auto Best**: `/v1/models`로 실제 사용 가능 모델 확인 → Claude Opus 5.5 → 최신 Opus → Sonnet 순, 404 시 자동 폴백. JSON 1차 로컬 repair → 2차 오류 피드백 재시도 → 실패 시 한국어 오류. Economy/Balanced/Best 비용 정책, 분석 결과 7일 캐시 |
| Hallucination Guard | FactGuard(QC)가 본문 속 가격·할인율·리뷰 수·평점을 원본과 대조, 상품 정보 요약표는 모델이 아닌 원본 데이터로 앱이 생성 |
| 허위 후기 방지 | "실제 사용하셨나요?" → 사용하지 않았으면 경험 서술 금지 프롬프트 + 생성 후 자동 중립화 + 편집기 검사 |
| Content Guard | "무조건/100%/최고/절대/완벽/1위…" 경고 + 수정 추천, 광고/제휴 표시 누락 검사, 중복 글 탐지, Content Memory(반복 표현 회피) |
| 블로그 편집기 | 블록 에디터(H1/H2/굵게/인용/목록/이미지/구분선/링크/상품 카드/CTA/광고 표시), 선택 영역 AI 다듬기 11종, 제목 후보 10+ (SEO/클릭/깔끔/정보/짧은, A/B 누적), Undo/Redo, 자동 저장, 버전 기록(복원), Content Quality Score(순위 예측 아님), Facts 패널, 에셋 드래그&드롭(대화면) |
| 이미지 카드 | Hero/Feature/Spec/Pros/Check/Target/CTA/Price 카드를 Canvas로 렌더링. 7개 스타일, 6개 비율, 팔레트 자동 추출 + WCAG 대비 보정, 자동 레이아웃, 텍스트 자동 축소/말줄임, 1080/1440/2160px 내보내기, Visual Editor(Undo/Redo) |
| Shorts Studio | 멀티 트랙 타임라인(영상·전환·텍스트·자막·음성·음악·효과음·스티커), 핀치 줌, Split/Delete/Duplicate/Reorder/Trim/Speed/Crop/Zoom/Rotate/Fit·Fill, 13종 전환, 7종 모션, 7종 텍스트 애니메이션, 13개 템플릿(JSON 모델), Hook 후보 5종, Safe Zone 표시, 자동 저장/크래시 복구 |
| 음성·자막·음악 | Android TTS(설치된 한국어 음성 + 프리셋 피치/속도), 실제 합성 길이로 타임라인 재계산(Voice Sync), 한국어 자막 분할, 사실 기반 키워드 강조, SRT 가져오기/내보내기, **앱이 실시간 합성한 BGM/효과음(라이선스 문제 없음)**, 로컬 BGM, 자동 Ducking, 트랙별 볼륨/Mute/Solo/Fade/Pan, 리미터 |
| 렌더링 | Canvas 프레임 → OpenGL ES → MediaCodec(H.264, HEVC 선택) 하드웨어 인코딩 + AAC, MediaMuxer MP4. 720p/1080p/1440p, 30/60fps, Fast/Balanced/Quality, 코덱 지원 확인 후 폴백, 렌더 큐(WorkManager, 포그라운드 알림, 진행률, 취소) |
| 내보내기 | MediaStore(Movies/Pictures/AIStudio), Android Sharesheet(YouTube·Instagram·TikTok·NAVER 등), 썸네일 스튜디오, 프로젝트 JSON 백업 |
| 네이버 | 공식 네이버 로그인(OAuth, Custom Tab, state 검증, 토큰 Keystore 암호화, 갱신/폐기). 게시는 **Handoff**: 서식 포함 클립보드 + 이미지 갤러리 저장 + 네이버 블로그 앱/글쓰기 화면 열기 → 사용자가 최종 게시 |
| Foldable | Cover(1열) / Medium(2-pane) / Inner(3-pane Production Workspace). FoldingFeature로 분리형 힌지 회피, Fold/Unfold 시 Activity 재생성 없이 재배치, NavigationSuite(Bottom bar ↔ Rail) |
| 기타 | 5단계 온보딩, 대시보드, 프로젝트 검색/필터, Batch Workspace(개별 검수 필수), 분석(로컬 통계·토큰 사용량·수동 입력 수익), Brand Profile, Expert Mode, Developer 화면(debug), Storage Manager, 데모 모드, 다크 모드·Dynamic Color·모션 줄이기 |

## 화면 구조

`홈 · 프로젝트 · 콘텐츠 · Shorts · 분석 · 설정` (폰: 하단 바, Fold 펼침/태블릿: Navigation Rail)
→ 파이프라인(1/8~8/8) → 프로젝트 카드(개요/상품 사실/블로그/비주얼/숏폼/기록/메모) → 블로그 편집기 · 미리보기(Mobile/Fold/Desktop) · 게시 체크리스트 · 이미지 카드 · Visual Editor · Shorts Studio · Thumbnail Studio

스크린샷(Robolectric 실제 UI 렌더): [`docs/screenshots/`](docs/screenshots)

## 빌드

요구: JDK 17+ (21 권장), Android SDK Platform 36, Build-Tools 36.0.0.

```bash
cp local.properties.example local.properties   # sdk.dir 및 비밀이 아닌 설정 입력
./build_debug.sh        # Windows: build_debug.bat
./build_release.sh      # 테스트 + 린트 + 릴리스 APK + AAB
```

| 산출물 | 경로 |
|---|---|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Release APK | `app/build/outputs/apk/release/app-release-unsigned.apk` (`keystore.properties`가 있으면 `app-release.apk`) |
| AAB | `app/build/outputs/bundle/release/app-release.aab` |

GitHub Actions(`.github/workflows/android.yml`)가 lint → test → debug/release/AAB 빌드 후 아티팩트를 업로드합니다.

## 설정 요약

* **Claude** — 설정 → AI: `Claude API 키 직접(개발자 모드)` 또는 `백엔드 프록시(권장)`. 키는 Android Keystore(AES-GCM)로 암호화 저장되고 APK에 포함되지 않습니다. [연결 테스트]로 Auto Best가 실제로 고른 모델을 확인할 수 있습니다.
* **NAVER 로그인** — Naver Developers에 앱 등록 → `NAVER_CLIENT_ID`, Callback URL(https, 예: `backend/`의 `/naver/callback`), 토큰 교환 URL 설정. 자세한 절차: [SETUP_GUIDE.md](SETUP_GUIDE.md)
* **키 없이 체험** — 홈의 [데모로 체험]: 명확히 DEMO로 표시된 샘플 상품으로 전체 흐름(카드·숏폼·렌더 포함)을 확인합니다.

## 실기기 QA (Galaxy Z Fold)

USB 디버깅을 켠 기기를 연결한 뒤:

```bash
./scripts/device_qa.sh --release                                   # 기본
CLAUDE_API_KEY=... PRODUCT_URL=https://naver.me/... ./scripts/device_qa.sh   # 실제 API·링크까지
```

설치 → 콜드 스타트 → 접기/펼치기(`cmd device_state`) → 다크모드·큰 글꼴 → 온디바이스 테스트(실제 MP4 렌더·TTS·갤러리·데모 파이프라인·편집기) → 프로세스 종료 복구 → R8 릴리스 실행 → logcat 크래시/비밀값 검사 순서로 진행하고 `docs/device-qa/<시간>/SUMMARY.md`에 결과를 남깁니다.
서명이 다른 기존 앱이 있으면 데이터를 지우지 않도록 중단하며, `--allow-reinstall`을 줄 때만 재설치합니다.

## 문서

[SETUP_GUIDE](SETUP_GUIDE.md) · [ARCHITECTURE](ARCHITECTURE.md) · [SECURITY](SECURITY.md) · [CHANGELOG](CHANGELOG.md) · [TEST_REPORT](TEST_REPORT.md) · [QA_REPORT](QA_REPORT.md) · [RELEASE_REPORT](RELEASE_REPORT.md) · [backend/](backend/README.md)

## 문제 해결

| 증상 | 해결 |
|---|---|
| "AI가 연결되지 않았습니다" | 설정 → AI에서 키 또는 프록시 입력 후 [연결 테스트] |
| "선택한 AI 모델을 사용할 수 없습니다" | Auto Best로 두거나 Custom 모델 ID 확인. 계정에 해당 모델 권한이 있어야 합니다 |
| 상품 정보를 불러오지 못함 / 로그인 필요 / 접근 제한 | 판매처가 자동 접근을 막는 경우입니다 → [직접 입력] 또는 네이버 쇼핑 검색 API 사용 |
| NAVER 연결 버튼이 설정 오류를 표시 | Client ID·Callback URL·토큰 교환 URL 설정 확인 (SETUP_GUIDE 5단계) |
| 음성이 생성되지 않음 | 기기 설정 → 텍스트 음성 변환 → 한국어 음성 데이터 설치 |
| 1080p 렌더 실패 | Shorts Studio → 렌더 → 720p 또는 Fast 프로필로 재시도 (기기 인코더 제약) |
| 빌드 중 Maven 429 | 네트워크/미러 문제. 잠시 후 재시도하거나 사내 미러를 Gradle init script로 지정 |
