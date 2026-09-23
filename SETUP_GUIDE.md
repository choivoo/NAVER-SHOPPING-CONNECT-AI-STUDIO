# SETUP GUIDE — 처음부터 설치까지

## 1. Android Studio 설치
1. https://developer.android.com/studio 에서 최신 안정판 Android Studio를 설치합니다.
2. 첫 실행 마법사에서 **Standard** 설치를 선택하면 JDK와 Android SDK가 함께 설치됩니다.

## 2. 프로젝트 열기
1. 이 저장소를 내려받습니다: `git clone <repo-url>`
2. Android Studio → **Open** → 저장소 폴더 선택 → Gradle Sync가 끝날 때까지 기다립니다.

## 3. SDK 설치
**Settings → Languages & Frameworks → Android SDK**
* SDK Platforms: **Android 16 (API 36)**
* SDK Tools: **Android SDK Build-Tools 36.0.0**, Platform-Tools

## 4. 환경 설정
`local.properties.example`을 `local.properties`로 복사합니다(이 파일은 Git에 올라가지 않습니다).
여기에는 **비밀이 아닌 값만** 넣습니다. API 키·Client Secret은 넣지 마세요.

## 5. NAVER Client 설정 (네이버 로그인)
1. https://developers.naver.com → **Application → 애플리케이션 등록**
2. 사용 API: **네이버 로그인** (필요 시 **검색** API도 추가 — 상품 검색 폴백용)
3. 제공 정보: **별명**만 선택 (앱은 별명 외 개인정보를 저장하지 않습니다)
4. 환경: **PC 웹 / 모바일 웹**, Callback URL: `https://<your-backend>/naver/callback`
   * 네이버 Callback URL은 https여야 하므로 `backend/cloudflare-worker`(또는 같은 역할의 서버)를 배포해
     `aistudio://oauth/naver`로 되돌려 보냅니다. 배포 방법: [backend/README.md](backend/README.md)
5. `local.properties`에 입력:
   ```
   NAVER_CLIENT_ID=발급받은 Client ID
   NAVER_REDIRECT_URI=https://<your-backend>/naver/callback
   NAVER_TOKEN_EXCHANGE_URL=https://<your-backend>/naver/token
   ```
   빌드 후에도 앱 **설정 → Account · NAVER**에서 바꿀 수 있습니다.
   백엔드가 없을 때만(개인 개발용) Client Secret을 앱 설정에 입력할 수 있으며, Keystore로 암호화 저장됩니다.

## 6. Claude 설정
두 가지 중 하나를 선택합니다.
* **개발자 모드(개인 사용)**: 앱 **설정 → AI → API 키 직접** → Claude API Key 입력 → [연결 테스트].
  Auto Best가 계정에서 실제로 쓸 수 있는 모델을 조회해 Claude Opus 5.5 → 최신 Opus → Sonnet 순으로 선택합니다.
* **백엔드 프록시(배포 권장)**: `backend/`의 Worker에 `ANTHROPIC_API_KEY`를 저장하고,
  `local.properties`의 `AI_PROXY_BASE_URL` 또는 앱 설정에 프록시 주소와 앱 토큰을 입력합니다.

키가 없어도 홈의 **[데모로 체험]**으로 전체 기능을 확인할 수 있습니다(결과에 DEMO 표시).

## 7. Build
* Android Studio: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**
* 터미널: `./build_debug.sh` (Windows: `build_debug.bat`), 릴리스: `./build_release.sh`

### 릴리스 서명
1. `keytool -genkeypair -v -keystore release.jks -alias aistudio -keyalg RSA -keysize 4096 -validity 10000`
2. `keystore.properties.example` → `keystore.properties` 복사 후 값 입력 (Git 제외 파일)
3. `./build_release.sh` → `app/build/outputs/apk/release/app-release.apk`

## 8. APK 설치
1. 폰: **설정 → 보안 → 알 수 없는 앱 설치 허용**(파일 앱/브라우저)
2. APK를 폰으로 복사해 실행하거나 `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. 첫 실행: 온보딩 → 알림 권한(선택) → 홈에서 링크 붙여넣기 → **AI 콘텐츠 만들기**
4. 음성(TTS)을 쓰려면 **설정 → 일반 → 텍스트 음성 변환**에 한국어 음성 데이터가 설치되어 있어야 합니다.
