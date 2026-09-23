package com.shoppingconnect.aistudio.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shoppingconnect.aistudio.ai.AiEngineProvider
import com.shoppingconnect.aistudio.auth.NaverAuthManager
import com.shoppingconnect.aistudio.auth.NaverAuthState
import com.shoppingconnect.aistudio.core.common.toAppException
import com.shoppingconnect.aistudio.core.network.Connectivity
import com.shoppingconnect.aistudio.data.db.ProjectDao
import com.shoppingconnect.aistudio.data.db.ProjectEntity
import com.shoppingconnect.aistudio.data.db.ShortformDao
import com.shoppingconnect.aistudio.data.repository.ProjectRepository
import com.shoppingconnect.aistudio.data.settings.AppSettings
import com.shoppingconnect.aistudio.data.settings.SettingsRepository
import com.shoppingconnect.aistudio.domain.model.ArticleLength
import com.shoppingconnect.aistudio.domain.model.CardStylePreset
import com.shoppingconnect.aistudio.domain.model.ContentPreset
import com.shoppingconnect.aistudio.domain.model.ContentStrategy
import com.shoppingconnect.aistudio.domain.model.PipelineMode
import com.shoppingconnect.aistudio.domain.model.PipelineOptions
import com.shoppingconnect.aistudio.domain.model.ShortsTemplateId
import com.shoppingconnect.aistudio.domain.model.Tone
import com.shoppingconnect.aistudio.domain.model.UsageStatus
import com.shoppingconnect.aistudio.domain.model.VoicePreset
import com.shoppingconnect.aistudio.pipeline.WorkScheduler
import com.shoppingconnect.aistudio.product.LinkCheck
import com.shoppingconnect.aistudio.product.UrlProcessor
import com.shoppingconnect.aistudio.ui.AppEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

sealed interface LinkStatus {
    data object Idle : LinkStatus
    data object Checking : LinkStatus
    data class Compatible(val label: String) : LinkStatus
    data class Unsupported(val reason: String) : LinkStatus
}

data class ProOptions(
    val keyword: String = "",
    val preset: ContentPreset = ContentPreset.INFO,
    val tone: Tone = Tone.FRIENDLY,
    val length: ArticleLength = ArticleLength.MEDIUM,
    val template: ShortsTemplateId = ShortsTemplateId.CLEAN_PRODUCT,
    val durationSec: Int = 30,
    val voice: VoicePreset = VoicePreset.NARRATION,
    val cta: String = "",
    val imageStyle: CardStylePreset = CardStylePreset.CLEAN,
    val includeShorts: Boolean = true,
)

data class HomeStats(val projects: Int = 0, val blogs: Int = 0, val shorts: Int = 0, val published: Int = 0)

data class HomeUiState(
    val url: String = "",
    val link: LinkStatus = LinkStatus.Idle,
    val mode: PipelineMode = PipelineMode.QUICK,
    val pro: ProOptions = ProOptions(),
    val askUsageFor: PipelineMode? = null,
    val starting: Boolean = false,
    val error: String? = null,
)

sealed interface HomeNav { data class Pipeline(val genId: String) : HomeNav; data class Manual(val url: String) : HomeNav }

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val scheduler: WorkScheduler,
    private val auth: NaverAuthManager,
    private val settingsRepo: SettingsRepository,
    private val engines: AiEngineProvider,
    private val connectivity: Connectivity,
    private val events: AppEvents,
    projectsRepo: ProjectRepository,
    projectDao: ProjectDao,
    shortformDao: ShortformDao,
) : ViewModel() {
    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()
    private val _nav = MutableSharedFlow<HomeNav>(extraBufferCapacity = 1)
    val nav = _nav.asSharedFlow()

    val authState: StateFlow<NaverAuthState> = auth.state
    val settings = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val recent: StateFlow<List<ProjectEntity>> = projectsRepo.observeProjects().map { it.take(12) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val online = connectivity.online.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    private val _aiReady = MutableStateFlow(true)
    val aiReady: StateFlow<Boolean> = _aiReady.asStateFlow()

    private val monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val stats: StateFlow<HomeStats> = combine(
        projectDao.countSince(monthStart), projectDao.blogsDoneSince(monthStart), shortformDao.renderedSince(monthStart), projectDao.publishedSince(monthStart),
    ) { p, b, s, pub -> HomeStats(p, b, s, pub) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeStats())

    private var checkJob: Job? = null

    init {
        viewModelScope.launch { settings.collect { s -> _aiReady.value = engines.isReady(); _ui.update { it.copy(pro = it.pro.copy(tone = s.brand.blogTone, preset = s.defaultPreset, length = s.defaultLength, template = s.shortsTemplate, durationSec = s.shortsDurationSec, voice = s.voicePreset, imageStyle = s.defaultCardStyle)) } } }
        viewModelScope.launch { events.sharedLink.collect { it?.let { text -> onUrlChange(text); events.consumeLink() } } }
    }

    fun onUrlChange(text: String) {
        _ui.update { it.copy(url = text, error = null, link = if (text.isBlank()) LinkStatus.Idle else LinkStatus.Checking) }
        checkJob?.cancel()
        if (text.isBlank()) return
        checkJob = viewModelScope.launch {
            delay(350)
            _ui.update {
                it.copy(link = when (val c = UrlProcessor.check(text)) {
                    is LinkCheck.Compatible -> LinkStatus.Compatible(c.kind.label)
                    is LinkCheck.Unsupported -> LinkStatus.Unsupported(c.reason)
                })
            }
        }
    }

    fun setMode(m: PipelineMode) = _ui.update { it.copy(mode = if (m == PipelineMode.MAGIC) PipelineMode.QUICK else m) }
    fun updatePro(f: (ProOptions) -> ProOptions) = _ui.update { it.copy(pro = f(it.pro)) }
    fun dismissError() = _ui.update { it.copy(error = null) }

    fun requestStart(magic: Boolean) {
        val s = _ui.value
        if (s.link !is LinkStatus.Compatible) { _ui.update { it.copy(error = "먼저 올바른 상품 링크를 입력해 주세요.") }; return }
        if (!online.value) { _ui.update { it.copy(error = "인터넷 연결이 필요합니다.") }; return }
        val mode = if (magic) PipelineMode.MAGIC else s.mode
        if (settings.value.askUsageEachTime) _ui.update { it.copy(askUsageFor = mode) } else start(mode, settings.value.defaultUsage)
    }

    fun cancelUsage() = _ui.update { it.copy(askUsageFor = null) }

    fun start(mode: PipelineMode, usage: UsageStatus) {
        _ui.update { it.copy(askUsageFor = null, starting = true, error = null) }
        viewModelScope.launch {
            try {
                if (!engines.isReady()) { _ui.update { it.copy(starting = false, error = "AI가 연결되지 않았습니다. 설정 → AI에서 API 키/프록시를 설정하거나 데모로 체험해 보세요.") }; return@launch }
                val st = settings.value
                val p = _ui.value.pro
                val strategy = if (mode == PipelineMode.PRO) ContentStrategy(p.preset, p.tone, p.length, usage, p.keyword.trim(), cta = p.cta.ifBlank { st.brand.defaultCta })
                else ContentStrategy(st.defaultPreset, st.brand.blogTone, st.defaultLength, usage, cta = st.brand.defaultCta)
                val options = PipelineOptions(
                    mode = mode, includeShorts = if (mode == PipelineMode.PRO) p.includeShorts else true, includeVisuals = true, strategy = strategy,
                    shortsDurationSec = if (mode == PipelineMode.PRO) p.durationSec else null, template = if (mode == PipelineMode.PRO) p.template else null,
                    voicePreset = if (mode == PipelineMode.PRO) p.voice else null, imageStyle = if (mode == PipelineMode.PRO) p.imageStyle else null,
                    keyword = if (mode == PipelineMode.PRO) p.keyword.trim().ifBlank { null } else null,
                )
                val started = scheduler.startFromUrl(UrlProcessor.extractUrl(_ui.value.url) ?: _ui.value.url, options)
                _ui.update { it.copy(starting = false, url = "", link = LinkStatus.Idle) }
                _nav.emit(HomeNav.Pipeline(started.generationId))
            } catch (e: Exception) {
                _ui.update { it.copy(starting = false, error = e.toAppException().userMessage) }
            }
        }
    }

    fun startDemo() {
        viewModelScope.launch {
            val started = scheduler.startDemo(PipelineOptions(mode = PipelineMode.QUICK, strategy = ContentStrategy(usage = UsageStatus.INTRO_ONLY)))
            _nav.emit(HomeNav.Pipeline(started.generationId))
        }
    }

    fun manualInput() { viewModelScope.launch { _nav.emit(HomeNav.Manual(_ui.value.url)) } }

    fun connectNaver(context: Context) {
        viewModelScope.launch {
            try { auth.startLogin(context) } catch (e: Exception) { _ui.update { it.copy(error = e.toAppException().userMessage) } }
        }
    }

    suspend fun markNotificationsAsked() = settingsRepo.update { it.copy(notificationsAsked = true) }
}
