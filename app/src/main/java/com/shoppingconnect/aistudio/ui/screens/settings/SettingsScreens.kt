package com.shoppingconnect.aistudio.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shoppingconnect.aistudio.BuildConfig
import com.shoppingconnect.aistudio.auth.NaverAuthState
import com.shoppingconnect.aistudio.core.common.formatBytes
import com.shoppingconnect.aistudio.core.common.formatDateTime
import com.shoppingconnect.aistudio.core.security.SecretKeyName
import com.shoppingconnect.aistudio.data.settings.AiConnection
import com.shoppingconnect.aistudio.data.settings.AiQuality
import com.shoppingconnect.aistudio.data.settings.AppSettings
import com.shoppingconnect.aistudio.data.settings.ModelChoice
import com.shoppingconnect.aistudio.data.settings.ThemeMode
import com.shoppingconnect.aistudio.domain.model.ArticleLength
import com.shoppingconnect.aistudio.domain.model.BgmMood
import com.shoppingconnect.aistudio.domain.model.CardStylePreset
import com.shoppingconnect.aistudio.domain.model.ContentPreset
import com.shoppingconnect.aistudio.domain.model.ExportProfile
import com.shoppingconnect.aistudio.domain.model.RenderQuality
import com.shoppingconnect.aistudio.domain.model.ShortsTemplateId
import com.shoppingconnect.aistudio.domain.model.SubtitleStyle
import com.shoppingconnect.aistudio.domain.model.Tone
import com.shoppingconnect.aistudio.domain.model.UsageStatus
import com.shoppingconnect.aistudio.domain.model.VideoResolution
import com.shoppingconnect.aistudio.domain.model.VoicePreset
import com.shoppingconnect.aistudio.ui.components.AppTopBar
import com.shoppingconnect.aistudio.ui.components.CenterTopBar
import com.shoppingconnect.aistudio.ui.components.ConfirmDialog
import com.shoppingconnect.aistudio.ui.components.KeyValue
import com.shoppingconnect.aistudio.ui.components.SectionCard

private data class SectionEntry(val key: String, val title: String, val subtitle: String, val icon: ImageVector)

private val sections = listOf(
    SectionEntry("naver", "Account · NAVER", "네이버 로그인, 블로그 연결", Icons.Default.AccountCircle),
    SectionEntry("ai", "AI", "모델, 품질/비용, API 키·프록시", Icons.Default.AutoAwesome),
    SectionEntry("content", "Content", "기본 글 길이·말투, 광고 고지 문구", Icons.AutoMirrored.Filled.Article),
    SectionEntry("brand", "Brand Profile", "말투, 기본 CTA, 해시태그, 서명", Icons.Default.Style),
    SectionEntry("shortform", "Shortform", "길이, 해상도, 음성, 음악, 자막, 템플릿", Icons.Default.Movie),
    SectionEntry("storage", "Storage", "사용량, 캐시 삭제", Icons.Default.Storage),
    SectionEntry("appearance", "Appearance", "테마, 동적 색상, 모션 줄이기", Icons.Default.Palette),
    SectionEntry("security", "Security · 데이터", "데이터 삭제, 연결 해제", Icons.Default.Security),
    SectionEntry("expert", "Expert Mode", "프롬프트 버전, 토큰, 렌더 코덱 등", Icons.Default.Science),
    SectionEntry("about", "About", "버전, 정책, 라이선스", Icons.Default.Info),
)

@Composable
fun SettingsScreen(onSection: (String) -> Unit, onDeveloper: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    Scaffold(topBar = { CenterTopBar("설정") }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            items(sections, key = { it.key }) { s ->
                ListItem(
                    headlineContent = { Text(s.title) }, supportingContent = { Text(s.subtitle) },
                    leadingContent = { Icon(s.icon, null) }, trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    modifier = Modifier.widthIn(max = 840.dp).clickable { if (s.key == "storage") onSection("storage_screen") else onSection(s.key) },
                )
            }
            if (BuildConfig.DEBUG) item {
                ListItem(headlineContent = { Text("Developer") }, supportingContent = { Text("API/AI 로그, 프롬프트 버전, 렌더 통계 (debug 빌드 전용)") },
                    leadingContent = { Icon(Icons.Default.Build, null) }, modifier = Modifier.widthIn(max = 840.dp).clickable(onClick = onDeveloper))
            }
        }
    }
}

@Composable
private fun <T> ChoiceRow(title: String, values: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { values.forEach { v -> FilterChip(v == selected, { onPick(v) }, { Text(label(v)) }) } }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title); subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        Switch(checked, onChange)
    }
}

/** Secret entry field: the stored value is never shown; only "저장됨" state. */
@Composable
private fun SecretField(label: String, present: Boolean, onSave: (String?) -> Unit) {
    var v by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(v, { v = it }, label = { Text(label + if (present) " (저장됨)" else "") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(v); v = "" }, enabled = v.isNotBlank()) { Text("저장") }
            if (present) OutlinedButton(onClick = { onSave(null) }) { Text("삭제") }
        }
    }
}

@Composable
private fun TextSetting(label: String, value: String, minLines: Int = 1, onDone: (String) -> Unit) {
    var v by remember(value) { mutableStateOf(value) }
    OutlinedTextField(v, { v = it; onDone(it) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), minLines = minLines, singleLine = minLines == 1)
}

@Composable
fun SettingsSectionScreen(section: String, onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val secrets by vm.secretPresence.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val auth by vm.auth.state.collectAsStateWithLifecycle()
    val voices by vm.voices.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val title = sections.firstOrNull { it.key == section }?.title ?: "설정"
    Scaffold(topBar = { AppTopBar(title, onBack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (section) {
                    "naver" -> {
                        SectionCard(title = "NAVER 계정") {
                            when (val a = auth) {
                                is NaverAuthState.Connected -> { KeyValue("상태", "● 연결됨 (${a.nickname})"); if (s.naverConnectedAt > 0) KeyValue("연결 시각", s.naverConnectedAt.formatDateTime()); OutlinedButton(onClick = vm::disconnectNaver) { Text("계정 연결 해제 (토큰 폐기)") } }
                                else -> { KeyValue("상태", "○ NAVER 연결 필요"); Button(onClick = { vm.connectNaver(ctx) }) { Text("NAVER 연결 (공식 로그인)") } }
                            }
                            Text("앱은 네이버 비밀번호를 받거나 저장하지 않습니다. 로그인은 네이버 공식 페이지(Custom Tab)에서 진행되며, 토큰은 Android Keystore로 암호화 저장됩니다.", style = MaterialTheme.typography.bodySmall)
                        }
                        SectionCard(title = "네이버 로그인 앱 설정 (개발자)") {
                            Text("Naver Developers에 등록한 애플리케이션 정보입니다. Client Secret은 가급적 백엔드(토큰 교환 서버)에 두세요.", style = MaterialTheme.typography.bodySmall)
                            TextSetting("Client ID (비밀 아님)", s.naverClientIdOverride.ifBlank { BuildConfig.NAVER_CLIENT_ID }) { v -> vm.update { it.copy(naverClientIdOverride = v.trim()) } }
                            TextSetting("Redirect URI", s.naverRedirectUriOverride.ifBlank { BuildConfig.NAVER_REDIRECT_URI }) { v -> vm.update { it.copy(naverRedirectUriOverride = v.trim()) } }
                            TextSetting("토큰 교환 서버 URL (https, 권장)", s.naverTokenExchangeUrlOverride.ifBlank { BuildConfig.NAVER_TOKEN_EXCHANGE_URL }) { v -> vm.update { it.copy(naverTokenExchangeUrlOverride = v.trim()) } }
                            SecretField("Client Secret (개발자 모드 · 백엔드가 없을 때만)", secrets[SecretKeyName.NAVER_CLIENT_SECRET] == true) { vm.putSecret(SecretKeyName.NAVER_CLIENT_SECRET, it) }
                        }
                        SectionCard(title = "네이버 블로그 게시 방식") {
                            Text("공개된 네이버 블로그 글쓰기 공식 API를 가정하지 않습니다. 완성된 글을 클립보드·갤러리로 넘기고 네이버 블로그 편집 화면을 열어, 사용자가 직접 확인 후 게시합니다.", style = MaterialTheme.typography.bodySmall)
                            TextSetting("블로그 글쓰기 URL (앱 미설치 시)", s.naverBlogWriteUrl) { v -> vm.update { it.copy(naverBlogWriteUrl = v.trim().ifBlank { AppSettings.DEFAULT_BLOG_WRITE_URL }) } }
                        }
                        SectionCard(title = "네이버 쇼핑 검색 API (선택)") {
                            Text("상품 링크 추출이 막힐 때 공식 검색 API로 상품 정보를 불러옵니다 (Naver Developers '검색' API).", style = MaterialTheme.typography.bodySmall)
                            SecretField("검색 API Client ID", secrets[SecretKeyName.NAVER_SEARCH_CLIENT_ID] == true) { vm.putSecret(SecretKeyName.NAVER_SEARCH_CLIENT_ID, it) }
                            SecretField("검색 API Client Secret", secrets[SecretKeyName.NAVER_SEARCH_CLIENT_SECRET] == true) { vm.putSecret(SecretKeyName.NAVER_SEARCH_CLIENT_SECRET, it) }
                        }
                    }
                    "ai" -> {
                        SectionCard(title = "모델") {
                            ChoiceRow("AI → 모델", ModelChoice.entries, s.modelChoice, { it.label }) { v -> vm.update { it.copy(modelChoice = v) } }
                            if (s.modelChoice == ModelChoice.CUSTOM) TextSetting("Custom API Model ID", s.customModel) { v -> vm.update { it.copy(customModel = v.trim()) } }
                            Text("Auto Best는 계정에서 실제 사용 가능한 모델을 /v1/models로 확인해 Claude Opus 5.5 → 최신 Opus → 최신 Sonnet 순으로 선택합니다. 사용할 수 없는 모델은 다음 후보로 자동 전환됩니다.", style = MaterialTheme.typography.bodySmall)
                            ChoiceRow("AI Quality (비용)", AiQuality.entries, s.aiQuality, { it.label }) { v -> vm.update { it.copy(aiQuality = v) } }
                            Text(s.aiQuality.description, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = vm::testAi) { Text("연결 테스트 · 실제 사용 모델 확인") }
                        }
                        SectionCard(title = "연결 방식") {
                            ChoiceRow("AI Provider", AiConnection.entries, s.aiConnection, { if (it == AiConnection.DIRECT) "API 키 직접 (개발자)" else "백엔드 프록시" }) { v -> vm.update { it.copy(aiConnection = v) } }
                            if (s.aiConnection == AiConnection.DIRECT) {
                                SecretField("Claude API Key", secrets[SecretKeyName.CLAUDE_API_KEY] == true) { vm.putSecret(SecretKeyName.CLAUDE_API_KEY, it) }
                                Text("개인 사용 개발자 모드입니다. 키는 Android Keystore로 암호화 저장되며 로그에 기록되지 않습니다. 배포용 앱은 백엔드 프록시를 권장합니다.", style = MaterialTheme.typography.bodySmall)
                            } else {
                                TextSetting("프록시 Base URL (https)", s.proxyBaseUrl.ifBlank { BuildConfig.AI_PROXY_BASE_URL }) { v -> vm.update { it.copy(proxyBaseUrl = v.trim()) } }
                                SecretField("프록시 앱 토큰 (선택)", secrets[SecretKeyName.AI_PROXY_TOKEN] == true) { vm.putSecret(SecretKeyName.AI_PROXY_TOKEN, it) }
                            }
                            SwitchRow("구조화 출력(JSON Schema)", s.strictJsonSchema, "지원하지 않는 모델/프록시에서는 자동으로 끕니다.") { v -> vm.update { it.copy(strictJsonSchema = v) } }
                            SwitchRow("거절 시 서버 측 폴백", s.refusalFallback, "Opus 요청이 안전 분류기로 거절되면 권장 모델로 재시도 (직접 연결 시).") { v -> vm.update { it.copy(refusalFallback = v) } }
                        }
                        SectionCard(title = "Demo Mode") {
                            SwitchRow("데모 모드", s.demoMode, "API 없이 템플릿으로 UI를 체험합니다. 결과에는 DEMO 표시가 붙습니다.") { v -> vm.update { it.copy(demoMode = v) } }
                        }
                    }
                    "content" -> SectionCard(title = "콘텐츠 기본값") {
                        ChoiceRow("기본 글 길이", ArticleLength.entries, s.defaultLength, { it.label }) { v -> vm.update { it.copy(defaultLength = v) } }
                        ChoiceRow("기본 콘텐츠 유형", ContentPreset.entries, s.defaultPreset, { it.label }) { v -> vm.update { it.copy(defaultPreset = v) } }
                        ChoiceRow("기본 사용 여부", UsageStatus.entries, s.defaultUsage, { it.label }) { v -> vm.update { it.copy(defaultUsage = v) } }
                        SwitchRow("생성할 때마다 사용 여부 묻기", s.askUsageEachTime) { v -> vm.update { it.copy(askUsageEachTime = v) } }
                        TextSetting("제휴 콘텐츠 표시 문구", s.disclosureText, 3) { v -> vm.update { it.copy(disclosureText = v) } }
                        Text("플랫폼/프로그램이 요구하는 고지 문구가 있다면 그대로 입력하세요.", style = MaterialTheme.typography.bodySmall)
                        SwitchRow("가격 변동 가능 문구 자동 추가", s.priceChangeNotice) { v -> vm.update { it.copy(priceChangeNotice = v) } }
                        ChoiceRow("기본 카드 스타일", CardStylePreset.entries, s.defaultCardStyle, { it.label }) { v -> vm.update { it.copy(defaultCardStyle = v) } }
                        ChoiceRow("카드 이미지 해상도", listOf(1080, 1440, 2160), s.cardExportWidth, { "${it}px" }) { v -> vm.update { it.copy(cardExportWidth = v) } }
                    }
                    "brand" -> SectionCard(title = "Reusable Brand Profile") {
                        ChoiceRow("블로그 말투", Tone.entries, s.brand.blogTone, { it.label }) { v -> vm.update { it.copy(brand = it.brand.copy(blogTone = v)) } }
                        TextSetting("기본 CTA", s.brand.defaultCta, 2) { v -> vm.update { it.copy(brand = it.brand.copy(defaultCta = v)) } }
                        TextSetting("기본 해시태그 (공백 구분)", s.brand.defaultHashtags.joinToString(" ")) { v -> vm.update { it.copy(brand = it.brand.copy(defaultHashtags = v.split(' ', ',').map { t -> t.trim().removePrefix("#") }.filter { t -> t.isNotBlank() })) } }
                        TextSetting("맺음말 서명 / 카드 로고 텍스트", s.brand.signature) { v -> vm.update { it.copy(brand = it.brand.copy(signature = v)) } }
                        TextSetting("쓰지 않을 표현 (쉼표 구분)", s.brand.bannedPhrases.joinToString(", ")) { v -> vm.update { it.copy(brand = it.brand.copy(bannedPhrases = v.split(',').map { t -> t.trim() }.filter { t -> t.isNotBlank() })) } }
                        Text("다음 프로젝트부터 자동 적용됩니다.", style = MaterialTheme.typography.bodySmall)
                    }
                    "shortform" -> {
                        LaunchedEffect(Unit) { vm.loadVoices() }
                        SectionCard(title = "숏폼 기본값") {
                            ChoiceRow("Duration", listOf(15, 20, 30, 45, 60), s.shortsDurationSec, { "${it}초" }) { v -> vm.update { it.copy(shortsDurationSec = v) } }
                            ChoiceRow("Resolution", VideoResolution.entries, s.shortsResolution, { it.label }) { v -> vm.update { it.copy(shortsResolution = v) } }
                            ChoiceRow("Template", ShortsTemplateId.entries, s.shortsTemplate, { it.label }) { v -> vm.update { it.copy(shortsTemplate = v) } }
                            SwitchRow("Voice (TTS)", s.voiceEnabled) { v -> vm.update { it.copy(voiceEnabled = v) } }
                            ChoiceRow("Voice Preset", VoicePreset.entries, s.voicePreset, { it.label }) { v -> vm.update { it.copy(voicePreset = v) } }
                            if (voices.isNotEmpty()) ChoiceRow("기기 음성", listOf("") + voices.map { it.name }, s.ttsVoiceName, { n -> if (n.isBlank()) "자동" else voices.first { it.name == n }.label }) { v -> vm.update { it.copy(ttsVoiceName = v) } }
                            else Text("설치된 한국어 TTS 음성을 찾지 못했습니다. 기기 설정 → 텍스트 음성 변환에서 한국어 음성을 설치하세요.", style = MaterialTheme.typography.bodySmall)
                            ChoiceRow("Music (AI BGM, 앱 합성 음원)", BgmMood.entries, s.bgmMood, { it.label }) { v -> vm.update { it.copy(bgmMood = v) } }
                            SwitchRow("Subtitle", s.subtitlesEnabled) { v -> vm.update { it.copy(subtitlesEnabled = v) } }
                            ChoiceRow("자막 스타일", SubtitleStyle.entries, s.subtitleStyle, { it.label }) { v -> vm.update { it.copy(subtitleStyle = v) } }
                            ChoiceRow("Export Profile", ExportProfile.entries, s.exportProfile, { it.label }) { v -> vm.update { it.copy(exportProfile = v) } }
                            SwitchRow("워터마크", s.watermarkEnabled, "기본 OFF. 앱이 강제 워터마크를 붙이지 않습니다.") { v -> vm.update { it.copy(watermarkEnabled = v) } }
                            if (s.watermarkEnabled) TextSetting("워터마크 텍스트", s.watermarkText) { v -> vm.update { it.copy(watermarkText = v) } }
                        }
                    }
                    "appearance" -> SectionCard(title = "Appearance") {
                        ChoiceRow("Theme", ThemeMode.entries, s.theme, { it.label }) { v -> vm.update { it.copy(theme = v) } }
                        SwitchRow("Dynamic Color (Android 12+)", s.dynamicColor) { v -> vm.update { it.copy(dynamicColor = v) } }
                        SwitchRow("모션 줄이기 (Reduce Motion)", s.reduceMotion) { v -> vm.update { it.copy(reduceMotion = v) } }
                    }
                    "security" -> SectionCard(title = "Security · 데이터 보호") {
                        Text("• 모든 통신은 HTTPS만 허용합니다.\n• API 키·토큰은 Android Keystore(AES-GCM)로 암호화 저장되며 로그에 기록되지 않습니다.\n• 앱 데이터는 클라우드 백업에서 제외됩니다.\n• 상품 링크는 사설 IP·localhost·비HTTP 스킴을 차단합니다.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = vm::disconnectNaver) { Text("NAVER 계정 연결 해제") }
                        Button(onClick = { confirmDelete = true }) { Text("모든 로컬 데이터 삭제") }
                    }
                    "expert" -> SectionCard(title = "Expert Mode") {
                        SwitchRow("Expert Mode", s.expertMode) { v -> vm.update { it.copy(expertMode = v) } }
                        if (s.expertMode) {
                            ChoiceRow("Prompt Version", listOf("v1"), s.promptVersion, { it }) { v -> vm.update { it.copy(promptVersion = v) } }
                            ChoiceRow("Effort (AI 사고 깊이)", listOf("", "low", "medium", "high", "xhigh"), s.effortOverride, { it.ifBlank { "자동" } }) { v -> vm.update { it.copy(effortOverride = v) } }
                            ChoiceRow("Token Budget (max_tokens)", listOf(8000, 16000, 32000), s.maxOutputTokens, { "$it" }) { v -> vm.update { it.copy(maxOutputTokens = v) } }
                            ChoiceRow("Render Quality (Bitrate)", RenderQuality.entries, s.shortsQuality, { it.label }) { v -> vm.update { it.copy(shortsQuality = v) } }
                            ChoiceRow("FPS", listOf(30, 60), s.shortsFps, { "$it" }) { v -> vm.update { it.copy(shortsFps = v) } }
                            SwitchRow("HEVC 코덱 (지원 기기)", s.shortsHevc) { v -> vm.update { it.copy(shortsHevc = v) } }
                            Text("Temperature는 최신 Claude 모델에서 지원되지 않아 제공하지 않습니다 (Effort로 조절).", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    else -> SectionCard(title = "NAVER Shopping Connect AI Studio") {
                        KeyValue("버전", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        KeyValue("프롬프트", BuildConfig.PROMPT_VERSION)
                        Text("• 네이버 비밀번호를 수집하지 않으며 보안 절차를 우회하지 않습니다.\n• 허위 후기·허위 가격/할인/스펙을 생성하지 않도록 설계되었습니다.\n• 모든 게시는 사용자가 네이버 블로그에서 직접 확인 후 진행합니다.\n• 대량 자동 게시 기능은 제공하지 않습니다.\n• 기본 BGM·효과음은 앱이 실시간 합성한 원본 음원입니다.\n• 이 앱은 NAVER의 공식 앱이 아닙니다.", style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider()
                        Text("오픈소스: AndroidX, Jetpack Compose, Material 3, Kotlin Coroutines/Serialization, Hilt/Dagger, Room, WorkManager, Media3 (Apache 2.0) · OkHttp (Apache 2.0) · Coil (Apache 2.0) · jsoup (MIT)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (confirmDelete) ConfirmDialog("모든 로컬 데이터 삭제", "모든 프로젝트, 이미지, 영상, 저장된 키·토큰, 설정이 삭제됩니다. 되돌릴 수 없습니다.", "삭제", destructive = true, onConfirm = vm::deleteAllData, onDismiss = { confirmDelete = false })
    status?.let { AlertDialog(onDismissRequest = vm::clearStatus, text = { Text(it) }, confirmButton = { TextButton(onClick = vm::clearStatus) { Text("확인") } }) }
}

@Composable
fun StorageScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val usage by vm.storage.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadStorage() }
    Scaffold(topBar = { AppTopBar("Storage Manager", onBack) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard(title = "사용량") {
                val u = usage
                if (u == null) Text("계산 중…") else {
                    KeyValue("Images", u.images.formatBytes()); KeyValue("Videos", u.videos.formatBytes()); KeyValue("Audio", u.audio.formatBytes())
                    KeyValue("Cache", u.cache.formatBytes()); KeyValue("합계", u.total.formatBytes())
                }
                Button(onClick = vm::clearCache) { Text("Cache 삭제") }
                Text("캐시(미리보기 음원, 공유용 임시 파일)만 삭제되며 프로젝트 원본은 삭제되지 않습니다. 프로젝트 삭제는 프로젝트 화면에서 할 수 있습니다.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    status?.let { AlertDialog(onDismissRequest = vm::clearStatus, text = { Text(it) }, confirmButton = { TextButton(onClick = vm::clearStatus) { Text("확인") } }) }
}

@Composable
fun DeveloperScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val prompts by vm.prompts.collectAsStateWithLifecycle()
    val renders by vm.renders.collectAsStateWithLifecycle()
    var logs by remember { mutableStateOf(com.shoppingconnect.aistudio.core.common.AppLog.entries()) }
    Scaffold(topBar = { AppTopBar("Developer", onBack) { TextButton(onClick = { logs = com.shoppingconnect.aistudio.core.common.AppLog.entries() }) { Text("새로고침") } } }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SectionCard(title = "DB Inspector") { Text("Android Studio → App Inspection → Database Inspector에서 'aistudio.db'를 열어 확인하세요. (debug 빌드)", style = MaterialTheme.typography.bodySmall) }
            }
            item {
                SectionCard(title = "Prompt Versions") { prompts.forEach { Text("${it.id} · hash ${it.hash} · ${it.usedCount}회 · ${it.lastUsedAt.formatDateTime()}", style = MaterialTheme.typography.bodySmall) } }
            }
            item {
                SectionCard(title = "Render Statistics") { renders.take(20).forEach { Text("${it.createdAt.formatDateTime()} ${it.state} ${it.frames}f ${it.renderMs}ms ${it.errorMessage ?: ""}", style = MaterialTheme.typography.bodySmall) } }
            }
            item { Text("API / AI Logs (민감정보 자동 마스킹)", style = MaterialTheme.typography.titleSmall) }
            items(logs.reversed()) { e -> Text("${e.time.formatDateTime()} ${e.level}/${e.tag} ${e.message}", style = com.shoppingconnect.aistudio.ui.theme.MonoStyle) }
        }
    }
}
