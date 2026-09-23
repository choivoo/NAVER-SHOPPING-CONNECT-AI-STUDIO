package com.shoppingconnect.aistudio.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Cross-screen one-shot events coming from intents (shared links, notification taps). */
@Singleton
class AppEvents @Inject constructor() {
    private val _sharedLink = MutableStateFlow<String?>(null)
    val sharedLink: StateFlow<String?> = _sharedLink.asStateFlow()
    private val _openProject = MutableStateFlow<String?>(null)
    val openProject: StateFlow<String?> = _openProject.asStateFlow()

    fun shareLink(text: String) { _sharedLink.value = text }
    fun consumeLink() { _sharedLink.value = null }
    fun openProject(id: String) { _openProject.value = id }
    fun consumeProject() { _openProject.value = null }
}
