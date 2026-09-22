package com.shoppingconnect.aistudio.ai

import com.shoppingconnect.aistudio.ai.agents.ClaudeAiEngine
import com.shoppingconnect.aistudio.ai.claude.AiGateway
import com.shoppingconnect.aistudio.ai.claude.ClaudeClient
import com.shoppingconnect.aistudio.ai.prompts.PromptRepository
import com.shoppingconnect.aistudio.core.common.AppException
import com.shoppingconnect.aistudio.core.common.ErrorKind
import com.shoppingconnect.aistudio.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiEngineProvider @Inject constructor(
    private val settings: SettingsRepository,
    private val client: ClaudeClient,
    private val gateway: AiGateway,
    private val prompts: PromptRepository,
) {
    /** Real Claude engine when configured; the clearly-labelled demo engine only in Demo Mode. */
    suspend fun engine(): AiEngine {
        val s = settings.current()
        if (s.demoMode) return DemoAiEngine()
        if (!client.isConfigured(s)) throw AppException(ErrorKind.AiNotConfigured)
        return ClaudeAiEngine(gateway, prompts, s)
    }

    suspend fun isReady(): Boolean { val s = settings.current(); return s.demoMode || client.isConfigured(s) }
}
