package com.shoppingconnect.aistudio.ui.screens.blog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.shoppingconnect.aistudio.auth.NaverAuthManager
import com.shoppingconnect.aistudio.auth.NaverAuthState
import com.shoppingconnect.aistudio.content.ComplianceChecker
import com.shoppingconnect.aistudio.content.FactGuard
import com.shoppingconnect.aistudio.data.repository.ProjectBundle
import com.shoppingconnect.aistudio.data.repository.ProjectRepository
import com.shoppingconnect.aistudio.data.settings.SettingsRepository
import com.shoppingconnect.aistudio.domain.model.BlockType
import com.shoppingconnect.aistudio.domain.model.ProjectStatus
import com.shoppingconnect.aistudio.domain.model.Severity
import com.shoppingconnect.aistudio.media.export.MediaExporter
import com.shoppingconnect.aistudio.publish.BlogPublisher
import com.shoppingconnect.aistudio.publish.FutureOfficialApiPublisher
import com.shoppingconnect.aistudio.publish.NaverBlogHandoffPublisher
import com.shoppingconnect.aistudio.publish.PublishPayload
import com.shoppingconnect.aistudio.publish.PublishResult
import com.shoppingconnect.aistudio.publish.SharePublisher
import com.shoppingconnect.aistudio.ui.components.AppTopBar
import com.shoppingconnect.aistudio.ui.components.BlogRenderer
import com.shoppingconnect.aistudio.ui.components.CheckRow
import com.shoppingconnect.aistudio.ui.components.DemoBanner
import com.shoppingconnect.aistudio.ui.components.SectionCard
import com.shoppingconnect.aistudio.ui.components.SparkIndicator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class BlogViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: ProjectRepository,
    private val settings: SettingsRepository,
    exporter: MediaExporter,
    auth: NaverAuthManager,
) : ViewModel() {
    val projectId: String = checkNotNull(handle["projectId"])
    val bundle = repo.observeBundle(projectId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val authState = auth.state
    val publishers: List<BlogPublisher> = listOf(NaverBlogHandoffPublisher(exporter), SharePublisher(exporter), FutureOfficialApiPublisher())

    data class CheckItem(val label: String, val ok: Boolean, val detail: String)

    /** Final pre-publish checklist, evaluated from the current article and source data. */
    fun checklist(b: ProjectBundle, disclosureRequired: Boolean): List<CheckItem> {
        val a = b.article ?: return emptyList()
        val p = b.product
        val compliance = ComplianceChecker.check(a, b.strategy?.usage ?: com.shoppingconnect.aistudio.domain.model.UsageStatus.INTRO_ONLY, disclosureRequired)
        val facts = p?.let { FactGuard.check(a, it) }.orEmpty()
        val link = a.blocks.firstOrNull { it.type == BlockType.LINK || it.type == BlockType.CTA }?.url
        return listOf(
            CheckItem("상품 정보 확인", p != null && facts.none { it.severity == Severity.ERROR }, if (facts.isEmpty()) "원본 데이터와 불일치 없음" else "원본과 다른 수치 ${facts.size}건"),
            CheckItem("링크 확인", !link.isNullOrBlank() && link.startsWith("http"), link ?: "링크 블록이 없습니다"),
            CheckItem("과장 표현 검사", compliance.none { it.code == "EXAGGERATION" }, "${compliance.count { it.code == "EXAGGERATION" }}건"),
            CheckItem("허위 사용 후기 검사", compliance.none { it.code == "FAKE_EXPERIENCE" }, "${compliance.count { it.code == "FAKE_EXPERIENCE" }}건"),
            CheckItem("제휴 표시 확인", compliance.none { it.code == "MISSING_DISCLOSURE" }, if (compliance.any { it.code == "MISSING_DISCLOSURE" }) "고지 문구 누락" else "포함됨"),
            CheckItem("이미지 확인", a.blocks.any { it.type == BlockType.IMAGE }, "${a.blocks.count { it.type == BlockType.IMAGE }}장"),
            CheckItem("제목 확인", a.title.length in 5..60, "${a.title.length}자"),
        )
    }

    suspend fun disclosureRequired() = settings.current().disclosureText.isNotBlank()
    suspend fun blogWriteUrl() = settings.current().naverBlogWriteUrl

    fun images(b: ProjectBundle): List<File> = b.article?.blocks?.filter { it.type == BlockType.IMAGE }
        ?.mapNotNull { blk -> b.assets.firstOrNull { it.id == blk.assetId }?.path?.let(::File) }?.filter { it.exists() }.orEmpty()

    fun markReady() = viewModelScope.launch { repo.setStatus(projectId, ProjectStatus.READY) }
    fun markPublished() = viewModelScope.launch { repo.setStatus(projectId, ProjectStatus.PUBLISHED) }
}

@Composable
fun BlogPreviewScreen(onBack: () -> Unit, vm: BlogViewModel = hiltViewModel()) {
    val b by vm.bundle.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(topBar = { AppTopBar("블로그 미리보기", onBack) }) { pad ->
        val bundle = b ?: run { Box(Modifier.padding(pad).padding(24.dp)) { SparkIndicator() }; return@Scaffold }
        Column(Modifier.padding(pad).fillMaxSize()) {
            PrimaryTabRow(tab) { listOf("Mobile", "Fold", "Desktop").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) } }
            val (width, scale) = when (tab) { 0 -> 390.dp to 1f; 1 -> 720.dp to 1.05f; else -> 960.dp to 1.1f }
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), contentAlignment = Alignment.TopCenter) {
                Surface(shadowElevation = 4.dp, color = Color.White, modifier = Modifier.widthIn(max = width).fillMaxWidth()) {
                    bundle.article?.let { BlogRenderer(it, bundle.assets, scale) } ?: Text("글이 없습니다.", Modifier.padding(24.dp))
                }
            }
        }
    }
}

@Composable
fun PublishScreen(onBack: () -> Unit, onEdit: () -> Unit, vm: BlogViewModel = hiltViewModel()) {
    val b by vm.bundle.collectAsStateWithLifecycle()
    val auth by vm.authState.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<String?>(null) }
    var disclosureRequired by remember { mutableStateOf(true) }
    val confirmed = remember { mutableStateMapOf<String, Boolean>() }
    androidx.compose.runtime.LaunchedEffect(Unit) { disclosureRequired = vm.disclosureRequired() }
    Scaffold(topBar = { AppTopBar("게시 전 Final Check", onBack) }) { pad ->
        val bundle = b ?: run { Box(Modifier.padding(pad).padding(24.dp)) { SparkIndicator() }; return@Scaffold }
        val checks = vm.checklist(bundle, disclosureRequired)
        val allConfirmed = checks.all { confirmed[it.label] == true }
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (bundle.project.isDemo) DemoBanner()
                SectionCard(title = "Checklist") {
                    Text("자동 검사 결과를 확인하고 각 항목을 직접 체크하세요.", style = MaterialTheme.typography.bodySmall)
                    checks.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(confirmed[c.label] == true, { confirmed[c.label] = it })
                            Box(Modifier.weight(1f)) { CheckRow(c.ok, (if (c.ok) "✓ " else "") + c.label, c.detail) }
                        }
                    }
                    OutlinedButton(onClick = onEdit) { Text("편집기로 돌아가 수정") }
                }
                SectionCard(title = "게시 방법") {
                    if (auth !is NaverAuthState.Connected) Text("NAVER 계정 연결 없이도 진행할 수 있습니다. 게시는 네이버 블로그(앱 또는 웹)의 본인 로그인 세션에서 이루어집니다.", style = MaterialTheme.typography.bodySmall)
                    vm.publishers.forEach { p ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(p.label, style = MaterialTheme.typography.titleSmall)
                            Text(p.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val enabled = p.isAvailable(ctx) && allConfirmed && !bundle.project.isDemo
                            val action = {
                                scope.launch {
                                    val article = bundle.article ?: return@launch
                                    val r = p.publish(ctx, PublishPayload(article, vm.images(bundle), vm.blogWriteUrl(), bundle.project.title))
                                    result = when (r) { is PublishResult.HandedOff -> r.message; is PublishResult.Shared -> r.message; is PublishResult.Unavailable -> r.reason }
                                    if (r is PublishResult.HandedOff) vm.markReady()
                                }
                            }
                            if (p is NaverBlogHandoffPublisher) Button(onClick = { action() }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Icon(Icons.AutoMirrored.Filled.OpenInNew, null); Spacer(Modifier.width(6.dp)); Text("네이버 블로그에서 계속") }
                            else OutlinedButton(onClick = { action() }, enabled = enabled) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text(p.label) }
                        }
                    }
                    if (bundle.project.isDemo) Text("데모 프로젝트는 게시할 수 없습니다.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    else if (!allConfirmed) Text("모든 체크리스트 항목을 확인해야 계속할 수 있습니다.", style = MaterialTheme.typography.bodySmall)
                }
                result?.let {
                    SectionCard(title = "다음 단계") {
                        Text(it)
                        Text("네이버 블로그에서 직접 게시를 마쳤다면 아래 버튼으로 기록하세요.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = vm::markPublished) { Text("게시 완료로 표시") }
                    }
                }
            }
        }
    }
}
