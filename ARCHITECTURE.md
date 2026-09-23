# ARCHITECTURE

단일 `app` 모듈 + 패키지 단위 계층 (요구사항 97: 과도한 모듈화로 빌드가 복잡해지지 않도록 단순화).
MVVM + Clean-ish layering, Hilt DI, Coroutines/Flow, Room, DataStore, WorkManager.

```
com.shoppingconnect.aistudio
├── core/        common(AppError·AppLog·Redactor) · security(Keystore SecretStore, UrlSecurity SSRF, ImageSecurity) · network(OkHttp, SafeFetcher) · json(JsonRepair)
├── domain/model Product·Content·Visual·Shortform·Project (kotlinx.serialization)
├── data/        db(Room: 10 entities, DAOs) · settings(DataStore JSON) · files(ProjectFiles) · repository(Project, Media)
├── product/     UrlProcessor · HtmlProductExtractor · ProductSourceAdapter(NaverShopping/SmartStore/Generic/Manual) · NaverShoppingSearchApi
├── ai/          AiEngine(interface) · agents/ClaudeAiEngine · DemoAiEngine · claude(ClaudeClient raw HTTP, ModelResolver, AiGateway) · prompts(versioned assets)
├── content/     ArticleAssembler · ComplianceChecker · FactGuard(QC) · SeoAnalyzer · ContentMemory · KoreanText · ArticleFormatter
├── visual/      CardRenderer · ColorTools · VisualService
├── media/       audio(Pcm/WavIO/AudioDecoder/BgmSynth/SfxSynth/AudioMixer/AacEncoder) · tts(VoiceProvider) · subtitle · video(FrameRenderer, SurfaceVideoEncoder, ShortsRenderer) · shorts(Templates, AutoEditor, VoiceSync, TimelineOps, Thumbnail, ShortsService) · export(MediaExporter)
├── pipeline/    PipelineOrchestrator · PipelineWorker/RenderWorker · WorkScheduler · Notifier · DemoData
├── auth/        NaverAuthManager (OAuth code flow + Custom Tabs)
├── publish/     BlogPublisher: NaverBlogHandoffPublisher · SharePublisher · FutureOfficialApiPublisher
└── ui/          theme · adaptive(WindowLayout, FoldInfo, WorkspacePanes) · components · navigation · screens/*
```

## Content pipeline

```
URL → UrlProcessor(check, classify) → SafeFetcher(redirect ≤5, SSRF per hop) → Adapter → Product(verified/unknown)
  1 ProductAgent → ProductIntelligence (7-day cache by URL)
  2 FactAgent (deterministic)  → unknown fields recorded, warnings
  3 StrategyAgent              → ContentStrategy (preset/tone/keywords/outline/CTA)
  4 WriterAgent + TitleAgent   → ArticleAssembler adds facts table, link, CTA, disclosure, price notice (not the model)
  5 VisualStrategyAgent        → CardRenderer (SPEC rows from verified data only) → placed in article
  6 ShortformAgent             → AutoEditor → TTS → VoiceSync → subtitles/sfx/BGM → Thumbnail
  7 ComplianceAgent + FactGuard + DuplicateDetector + SEO score
  8 done → notification
```
각 단계는 `GenerationEntity.stepsJson`에 상태(Waiting/Running/Complete/Warning/Error/Skipped)를 저장합니다. 앱이 종료되어도 WorkManager가 워커를 다시 실행하고, 완료된 단계는 건너뜁니다.

## AI layer
* `ClaudeClient`: `POST /v1/messages`, `GET /v1/models` (anthropic-version 2023-06-01). 직접(x-api-key) 또는 프록시(Bearer 앱 토큰). JSON Schema 구조화 출력(`output_config.format`), effort, 서버 측 거절 폴백(`fallbacks: "default"`). 400에서 선택 기능만 끄고 재시도, 429/5xx/529 백오프 재시도, 오류→`ErrorKind` 매핑.
  * Android APK 크기·R8 호환성과 백엔드 프록시 호환을 위해 공식 Java SDK 대신 OkHttp 원시 HTTP를 사용합니다.
* `ModelResolver`: 모델명을 코드 전체에 하드코딩하지 않음. `/v1/models` 결과에서 최신 Opus/Sonnet을 계산하고, 조회 불가 시 알려진 후보(`claude-opus-5-5`, `claude-opus-5`, `claude-sonnet-5`)로 폴백. Economy/Balanced/Best × Heavy/Light 작업으로 모델·effort 결정.
* `AiGateway`: 후보 모델 순회(404 → 다음) → JSON 추출·로컬 repair → 스키마 검증 → 1회 오류 피드백 재시도 → `AiParseFailure`.
* 프롬프트: `assets/prompts/v1/*.md`, `{{변수}}` 렌더링, 사용 이력·해시를 `prompt_versions`에 기록, 프로젝트에 프롬프트 버전 저장.

## Rendering
`FrameRenderer`(Canvas: 모션·전환·텍스트 애니메이션·자막·스티커·워터마크·Safe Zone)가 미리보기와 내보내기에 동일하게 쓰입니다.
`ShortsRenderer`: 오디오 믹스 → AAC 인코딩 → 프레임마다 Bitmap → GLES 텍스처 → `eglPresentationTimeANDROID` → MediaCodec(Surface 입력) → MediaMuxer 인터리빙. 이미지 캐시는 바이트 기준 LRU로 OOM을 방지합니다.

## Foldable
`rememberWindowLayout()`이 WindowSizeClass + `WindowInfoTracker`(FoldingFeature)를 읽고, `configChanges`로 Activity 재생성 없이 재구성합니다.
`WorkspacePanes`: Compact=1 pane, Medium=center+inspector, Expanded=3 pane(좌/중/우 폭은 화면 폭에 따라 조정, 분리형 힌지 위치에 분할).

## Persistence
Room: projects · products · articles · article_versions · media_assets · shortforms(자동 저장본 포함) · generations · prompt_versions · render_jobs · manual_metrics (프로젝트 삭제 시 CASCADE). 스키마는 `app/schemas/`에 export.
설정은 DataStore(JSON 1키), 비밀값은 Keystore 암호화 SharedPreferences.
