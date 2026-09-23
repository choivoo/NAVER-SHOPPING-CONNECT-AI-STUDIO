package com.shoppingconnect.aistudio.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shoppingconnect.aistudio.auth.NaverAuthManager
import com.shoppingconnect.aistudio.data.settings.AppSettings
import com.shoppingconnect.aistudio.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepo: SettingsRepository,
    auth: NaverAuthManager,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        viewModelScope.launch {
            val persisted = settingsRepo.current()
            auth.restore()
            // Show UI only once the StateFlow carries the persisted value (onboarding flag etc.).
            settings.first { it == persisted }
            _ready.value = true
        }
    }
}
