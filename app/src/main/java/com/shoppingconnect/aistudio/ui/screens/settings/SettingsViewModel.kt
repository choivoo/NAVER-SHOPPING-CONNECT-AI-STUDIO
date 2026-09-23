package com.shoppingconnect.aistudio.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shoppingconnect.aistudio.ai.claude.ModelResolver
import com.shoppingconnect.aistudio.ai.claude.TaskWeight
import com.shoppingconnect.aistudio.auth.NaverAuthManager
import com.shoppingconnect.aistudio.core.common.AppLog
import com.shoppingconnect.aistudio.core.common.toAppException
import com.shoppingconnect.aistudio.core.security.SecretKeyName
import com.shoppingconnect.aistudio.core.security.SecretStore
import com.shoppingconnect.aistudio.data.db.PromptVersionDao
import com.shoppingconnect.aistudio.data.db.RenderJobDao
import com.shoppingconnect.aistudio.data.files.ProjectFiles
import com.shoppingconnect.aistudio.data.repository.ProjectRepository
import com.shoppingconnect.aistudio.data.settings.AppSettings
import com.shoppingconnect.aistudio.data.settings.SettingsRepository
import com.shoppingconnect.aistudio.media.tts.AndroidTtsProvider
import com.shoppingconnect.aistudio.media.tts.VoiceOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val secrets: SecretStore,
    private val resolver: ModelResolver,
    val auth: NaverAuthManager,
    private val tts: AndroidTtsProvider,
    private val projects: ProjectRepository,
    private val files: ProjectFiles,
    prompts: PromptVersionDao,
    renders: RenderJobDao,
) : ViewModel() {
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    /** Only presence is exposed to the UI — secret values are never read back into the UI. */
    val secretPresence = secrets.changes.map { SecretKeyName.entries.associateWith { k -> secrets.has(k) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val prompts = prompts.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val renders = renders.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()
    private val _voices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val voices = _voices.asStateFlow()
    private val _storage = MutableStateFlow<ProjectFiles.Usage?>(null)
    val storage = _storage.asStateFlow()

    fun update(f: (AppSettings) -> AppSettings) = viewModelScope.launch { repo.update(f) }
    fun putSecret(k: SecretKeyName, v: String?) { secrets.put(k, v); _status.value = if (v.isNullOrBlank()) "삭제했습니다." else "암호화하여 저장했습니다 (Android Keystore)." }
    fun clearStatus() { _status.value = null }

    /** Checks the connection and shows which model Auto Best actually resolves to. */
    fun testAi() = viewModelScope.launch {
        _status.value = "연결 확인 중…"
        try {
            val s = repo.current()
            val models = resolver.available(s, force = true)
            val heavy = resolver.plan(s, TaskWeight.HEAVY)
            val light = resolver.plan(s, TaskWeight.LIGHT)
            _status.value = buildString {
                append(if (models.isEmpty()) "모델 목록을 조회할 수 없어 기본 후보를 사용합니다.\n" else "사용 가능한 모델 ${models.size}개 확인.\n")
                append("고난도 작업: ${heavy.candidates.firstOrNull()} (effort ${heavy.effort})\n")
                append("단순 작업: ${light.candidates.firstOrNull()} (effort ${light.effort})")
                if (models.isNotEmpty()) append("\n\n" + models.filter { it.startsWith("claude-") }.take(12).joinToString("\n"))
            }
        } catch (e: Exception) { _status.value = e.toAppException().userMessage }
    }

    fun loadVoices() = viewModelScope.launch { _voices.value = tts.voices() }
    fun connectNaver(ctx: Context) = viewModelScope.launch { runCatching { auth.startLogin(ctx) }.onFailure { _status.value = it.toAppException().userMessage } }
    fun disconnectNaver() = viewModelScope.launch { auth.disconnect(); _status.value = "NAVER 연결을 해제했습니다." }

    fun loadStorage() = viewModelScope.launch { _storage.value = withContext(Dispatchers.IO) { files.usage() } }
    fun clearCache() = viewModelScope.launch { withContext(Dispatchers.IO) { files.clearCache() }; loadStorage(); _status.value = "캐시를 삭제했습니다. 프로젝트 원본은 유지됩니다." }

    /** Delete Account Data: all local projects, files, tokens, keys and settings. */
    fun deleteAllData() = viewModelScope.launch {
        runCatching { auth.disconnect() }
        projects.deleteAll()
        secrets.clearAll()
        repo.reset()
        AppLog.clear()
        _status.value = "모든 로컬 데이터를 삭제했습니다."
    }
}
