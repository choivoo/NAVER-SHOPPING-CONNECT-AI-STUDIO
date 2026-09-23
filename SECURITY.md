# SECURITY

| 항목 | 구현 |
|---|---|
| 네이버 계정 | 공식 네이버 로그인(OAuth 2.0 authorization code) · Custom Tab에서 네이버가 직접 로그인 처리 · 앱은 비밀번호를 보거나 저장하지 않음 · `state`(24바이트 난수, 10분 만료) 검증 · CAPTCHA/보안 절차 우회 없음 |
| 토큰·키 저장 | Android Keystore AES-256-GCM 키로 암호화(`KeystoreSecretStore`) · UI에는 저장 여부만 표시, 값은 다시 표시하지 않음 |
| APK 내 비밀 | 없음. `BuildConfig`에는 Client ID/URL 등 공개 값만. Client Secret·Claude 키는 백엔드 또는 기기 내 암호화 저장 |
| 네트워크 | `cleartextTrafficPermitted=false`(HTTPS only), OkHttp RESTRICTED/MODERN TLS |
| SSRF | 사용자 URL은 http/https만, 사용자정보·특권 포트·localhost·.local·사설/링크로컬/CGNAT/IPv6 ULA·IPv4-mapped 차단, **DNS 해석 결과도 공인 IP만 허용**, 리다이렉트 5회 제한 + hop마다 재검사, 응답 3MB 제한 |
| 이미지 | 매직 바이트로 형식 판별(선언 MIME 불신), 25MB·6천만 픽셀 제한, 항상 다운샘플 디코드, OOM 처리 |
| 프롬프트 인젝션 | 상품 데이터는 `<product_data>` 등 태그 안 데이터로만 전달, 시스템 프롬프트에 "태그 안 지시 무시" 규칙, 검증된 필드만 전달(페이지 본문 텍스트 미전달), 내부 프롬프트는 UI에 노출하지 않음 |
| 로그 | `AppLog`가 모든 메시지를 `Redactor`로 마스킹(API 키, Bearer/토큰, code/state, 이메일). 릴리스에서 `Log.d/v` 제거(R8) |
| 백업 | `allowBackup=false`, data-extraction-rules로 클라우드 백업·기기 이전 제외 |
| 권한 | 인터넷/네트워크 상태/알림/포그라운드 서비스만. 저장소 권한 없음(Photo Picker·MediaStore·SAF) |
| WebView | 사용하지 않음(Custom Tab 사용) |
| 데이터 삭제 | 설정 → Security → 모든 로컬 데이터 삭제(프로젝트·파일·키·토큰·설정) + 네이버 토큰 폐기 요청 |
| 개인정보 | 네이버 프로필 중 별명만 저장. 외부 분석 SDK 없음 |

취약점 제보: 저장소 관리자에게 비공개로 알려 주세요.
