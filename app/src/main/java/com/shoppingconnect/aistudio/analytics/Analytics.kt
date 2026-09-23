package com.shoppingconnect.aistudio.analytics

/**
 * Analytics abstraction. v1.0 ships only local statistics (no external SDK, no data leaves the
 * device). An opt-in remote sink can be added later behind this interface.
 */
interface AnalyticsSink {
    fun event(name: String, props: Map<String, String> = emptyMap())
}

object NoOpAnalytics : AnalyticsSink {
    override fun event(name: String, props: Map<String, String>) = Unit
}
