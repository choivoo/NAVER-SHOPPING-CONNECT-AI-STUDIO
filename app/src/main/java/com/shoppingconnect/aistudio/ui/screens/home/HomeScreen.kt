package com.shoppingconnect.aistudio.ui.screens.home

import android.Manifest
import android.content.ClipboardManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shoppingconnect.aistudio.auth.NaverAuthState
import com.shoppingconnect.aistudio.domain.model.ArticleLength
import com.shoppingconnect.aistudio.domain.model.CardStylePreset
import com.shoppingconnect.aistudio.domain.model.ContentPreset
import com.shoppingconnect.aistudio.domain.model.PipelineMode
import com.shoppingconnect.aistudio.domain.model.ShortsTemplateId
import com.shoppingconnect.aistudio.domain.model.Tone
import com.shoppingconnect.aistudio.domain.model.UsageStatus
import com.shoppingconnect.aistudio.domain.model.VoicePreset
import com.shoppingconnect.aistudio.ui.adaptive.LocalWindowLayout
import com.shoppingconnect.aistudio.ui.components.Dot
import com.shoppingconnect.aistudio.ui.components.EmptyState
import com.shoppingconnect.aistudio.ui.components.ProjectCard
import com.shoppingconnect.aistudio.ui.components.SectionCard
import com.shoppingconnect.aistudio.ui.components.SparkIndicator
import com.shoppingconnect.aistudio.ui.theme.LocalExtraColors
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenPipeline: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onManual: (String) -> Unit,
    onBatch: () -> Unit,
    onOpenSettings: (String) -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val auth by vm.authState.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val aiReady by vm.aiReady.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val wl = LocalWindowLayout.current
    val context = LocalContext.current
    var showPro by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.nav.collect {
            when (it) {
                is HomeNav.Pipeline -> onOpenPipeline(it.genId)
                is HomeNav.Manual -> onManual(it.url)
            }
        }
    }

    fun askNotificationsOnce() {
        if (Build.VERSION.SDK_INT >= 33 && !settings.notificationsAsked) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            scope.launch { vm.markNotificationsAsked() }
        }
    }

    val hero: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!online) OfflineBanner()
            if (!aiReady) AiSetupBanner(onSettings = { onOpenSettings("ai") }, onDemo = { askNotificationsOnce(); vm.startDemo() })
            LinkHeroCard(
                ui = ui,
                onUrl = vm::onUrlChange,
                onPaste = {
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    val text = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (!text.isNullOrBlank()) vm.onUrlChange(text)
                },
                onMode = vm::setMode,
                onPro = { showPro = true },
                onStart = { askNotificationsOnce(); vm.requestStart(magic = false) },
                onMagic = { askNotificationsOnce(); vm.requestStart(magic = true) },
                onManual = vm::manualInput,
                onBatch = onBatch,
            )
            ui.error?.let { err ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(err, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                        TextButton(onClick = vm::dismissError) { Text("닫기") }
                    }
                }
            }
        }
    }
    val dashboard: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatsRow(stats)
            Text("최근 프로젝트", style = MaterialTheme.typography.titleMedium)
            if (recent.isEmpty()) EmptyState(Icons.Default.Link, "첫 쇼핑 콘텐츠를 만들어보세요.", "쇼핑 커넥트 링크 하나로 블로그와 숏폼 초안을 만듭니다.", "링크 붙여넣기", onAction = {
                val cm = context.getSystemService(ClipboardManager::class.java)
                cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.let { vm.onUrlChange(it) }
            })
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        HomeHeader(auth, onConnect = { vm.connectNaver(context) }, onAccount = { onOpenSettings("naver") })
        if (wl.isCompact) {
            LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { hero() }
                item { dashboard() }
                items(recent, key = { it.id }) { p -> ProjectCard(p, onClick = { onOpenProject(p.id) }) }
            }
        } else {
            Row(Modifier.fillMaxSize().imePadding().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1.1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) { hero() }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { dashboard() }
                    items(recent, key = { it.id }) { p -> ProjectCard(p, onClick = { onOpenProject(p.id) }) }
                }
            }
        }
    }

    ui.askUsageFor?.let { mode -> UsageDialog(onPick = { vm.start(mode, it) }, onDismiss = vm::cancelUsage) }
    if (showPro) ProOptionsSheet(ui.pro, vm::updatePro) { showPro = false }
}

@Composable
private fun HomeHeader(auth: NaverAuthState, onConnect: () -> Unit, onAccount: () -> Unit) {
    val extra = LocalExtraColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("NAVER Shopping Connect", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("AI Studio", style = MaterialTheme.typography.headlineSmall)
        }
        when (auth) {
            is NaverAuthState.Connected -> Surface(onClick = onAccount, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(extra.connected); Spacer(Modifier.width(6.dp))
                    Text("NAVER Connected", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(28.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                        Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape, modifier = Modifier.size(28.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(auth.nickname.take(1), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                }
            }
            NaverAuthState.Connecting -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("연결 중…") }
            else -> OutlinedButton(onClick = onConnect, modifier = Modifier.semantics { contentDescription = "NAVER 연결" }) {
                Dot(MaterialTheme.colorScheme.outline, 8); Spacer(Modifier.width(6.dp))
                Text(if (auth is NaverAuthState.Expired) "NAVER 재연결" else "NAVER 연결")
            }
        }
    }
    if (auth is NaverAuthState.Failed) Text(auth.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun LinkHeroCard(
    ui: HomeUiState, onUrl: (String) -> Unit, onPaste: () -> Unit, onMode: (PipelineMode) -> Unit, onPro: () -> Unit,
    onStart: () -> Unit, onMagic: () -> Unit, onManual: () -> Unit, onBatch: () -> Unit,
) {
    val extra = LocalExtraColors.current
    Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("쇼핑 커넥트 링크를 붙여넣으세요", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            OutlinedTextField(
                value = ui.url, onValueChange = onUrl, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("https://naver.me/… 또는 상품 URL") },
                leadingIcon = { Icon(Icons.Default.Link, null) },
                trailingIcon = { IconButton(onClick = onPaste) { Icon(Icons.Default.ContentPaste, "클립보드에서 링크 붙여넣기") } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (val l = ui.link) {
                    LinkStatus.Idle -> TextButton(onClick = onPaste) { Icon(Icons.Default.ContentPaste, null); Spacer(Modifier.width(6.dp)); Text("클립보드에서 링크 붙여넣기") }
                    LinkStatus.Checking -> { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Checking Link...") }
                    is LinkStatus.Compatible -> { Dot(extra.success); Spacer(Modifier.width(8.dp)); Text("Compatible Link · ${l.label}", fontWeight = FontWeight.SemiBold) }
                    is LinkStatus.Unsupported -> { Dot(MaterialTheme.colorScheme.error); Spacer(Modifier.width(8.dp)); Text("Unsupported URL · ${l.reason}", color = MaterialTheme.colorScheme.error) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    listOf(PipelineMode.QUICK to "Quick", PipelineMode.PRO to "Pro").forEachIndexed { i, (m, label) ->
                        SegmentedButton(selected = ui.mode == m, onClick = { onMode(m) }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                    }
                }
                if (ui.mode == PipelineMode.PRO) IconButton(onClick = onPro) { Icon(Icons.Default.Tune, "Pro 설정") }
            }
            Button(onClick = onStart, enabled = !ui.starting, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                if (ui.starting) SparkIndicator(color = MaterialTheme.colorScheme.onPrimary) else Icon(Icons.Default.AutoAwesome, null)
                Spacer(Modifier.width(8.dp)); Text("AI 콘텐츠 만들기", style = MaterialTheme.typography.titleMedium)
            }
            FilledTonalButton(onClick = onMagic, enabled = !ui.starting, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Bolt, null); Spacer(Modifier.width(6.dp)); Text("⚡ 전체 자동 제작 (블로그+카드+숏폼+렌더)")
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onManual) { Icon(Icons.Default.EditNote, null); Spacer(Modifier.width(4.dp)); Text("상품 정보 직접 입력") }
                TextButton(onClick = onBatch) { Icon(Icons.Default.PlaylistAdd, null); Spacer(Modifier.width(4.dp)); Text("여러 링크 (Batch)") }
            }
            Text("게시는 항상 최종 검수 후 직접 진행합니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun AiSetupBanner(onSettings: () -> Unit, onDemo: () -> Unit) {
    SectionCard(title = "AI 연결 필요") {
        Text("Claude API 키 또는 백엔드 프록시를 설정하면 실제 AI 생성이 가능합니다. 키 없이도 데모 상품으로 전체 기능을 체험할 수 있습니다.", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSettings) { Text("AI 설정") }
            OutlinedButton(onClick = onDemo) { Icon(Icons.Default.Science, null); Spacer(Modifier.width(6.dp)); Text("데모로 체험") }
        }
    }
}

@Composable
private fun OfflineBanner() {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Text("오프라인 상태입니다. 저장된 프로젝트 열기·편집·재생은 가능하며, AI 생성과 상품 추출에는 인터넷 연결이 필요합니다.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatsRow(s: HomeStats) {
    SectionCard(title = "이번 달") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("프로젝트", s.projects); Stat("완료된 글", s.blogs); Stat("완료된 숏폼", s.shorts); Stat("게시", s.published)
        }
        Text("판매·수익 데이터는 공식 데이터 연결 또는 직접 입력 시에만 분석 탭에 표시됩니다.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Stat(label: String, v: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text("$v", style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun UsageDialog(onPick: (UsageStatus) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이 상품을 실제 사용하셨나요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("사용하지 않은 제품은 사용 경험처럼 쓰지 않도록 글을 작성합니다.", style = MaterialTheme.typography.bodySmall)
                UsageStatus.entries.forEach { u -> OutlinedButton(onClick = { onPick(u) }, modifier = Modifier.fillMaxWidth()) { Text(u.label) } }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun <T> ChipRow(title: String, values: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { values.forEach { v -> FilterChip(selected = v == selected, onClick = { onPick(v) }, label = { Text(label(v)) }) } }
}

@Composable
private fun ProOptionsSheet(p: ProOptions, update: ((ProOptions) -> ProOptions) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Pro Mode 설정", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(p.keyword, { v -> update { it.copy(keyword = v) } }, label = { Text("핵심 키워드") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            ChipRow("콘텐츠 유형", ContentPreset.entries, p.preset, { it.label }) { v -> update { it.copy(preset = v) } }
            ChipRow("말투", Tone.entries, p.tone, { it.label }) { v -> update { it.copy(tone = v) } }
            ChipRow("글 길이", ArticleLength.entries, p.length, { it.label }) { v -> update { it.copy(length = v) } }
            ChipRow("이미지 스타일", CardStylePreset.entries, p.imageStyle, { it.label }) { v -> update { it.copy(imageStyle = v) } }
            OutlinedTextField(p.cta, { v -> update { it.copy(cta = v) } }, label = { Text("CTA 문구 (선택)") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) { Text("숏폼도 만들기", Modifier.weight(1f)); Switch(p.includeShorts, { v -> update { it.copy(includeShorts = v) } }) }
            if (p.includeShorts) {
                ChipRow("숏폼 길이", listOf(15, 20, 30, 45, 60), p.durationSec, { "${it}초" }) { v -> update { it.copy(durationSec = v) } }
                ChipRow("템플릿", ShortsTemplateId.entries, p.template, { it.label }) { v -> update { it.copy(template = v) } }
                ChipRow("음성", VoicePreset.entries, p.voice, { it.label }) { v -> update { it.copy(voice = v) } }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("적용") }
        }
    }
}
