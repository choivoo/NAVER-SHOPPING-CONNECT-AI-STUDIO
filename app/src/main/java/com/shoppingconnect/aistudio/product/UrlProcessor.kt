package com.shoppingconnect.aistudio.product

import com.shoppingconnect.aistudio.core.security.UrlSecurity
import com.shoppingconnect.aistudio.domain.model.ProductSource
import okhttp3.HttpUrl

enum class LinkKind(val label: String, val source: ProductSource) {
    NAVER_SHOPPING("네이버 쇼핑", ProductSource.NAVER_SHOPPING),
    SMART_STORE("스마트스토어 / 브랜드스토어", ProductSource.SMART_STORE),
    SHOPPING_CONNECT("쇼핑 커넥트 / 단축 링크", ProductSource.SHOPPING_CONNECT),
    GENERIC("일반 상품 페이지", ProductSource.GENERIC),
}

sealed interface LinkCheck {
    data class Compatible(val url: HttpUrl, val kind: LinkKind, val cleaned: String) : LinkCheck
    data class Unsupported(val reason: String) : LinkCheck
}

/** Parses, validates and classifies pasted links. No network access. */
object UrlProcessor {
    private val trackingParams = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "fbclid", "gclid",
        "NaPm", "nl-query", "nl-ts-pid", "nl-au", "nclick", "n_media", "n_query", "n_rank", "n_ad_group", "n_ad", "n_keyword", "n_campaign_type",
    )

    /** Pulls the first http(s) URL out of shared text such as "상품명 https://naver.me/xyz". */
    fun extractUrl(text: String): String? {
        val m = Regex("https?://[^\\s<>\"'「」]+", RegexOption.IGNORE_CASE).find(text) ?: return text.trim().takeIf { looksLikeBareDomain(it) }
        return m.value.trimEnd('.', ',', ')', ']', '}', '>', '」', '!', '?')
    }

    private fun looksLikeBareDomain(s: String) = Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/\\S*)?$").matches(s)

    fun check(text: String): LinkCheck {
        val raw = extractUrl(text) ?: return LinkCheck.Unsupported("링크를 찾지 못했습니다.")
        return when (val v = UrlSecurity.check(raw)) {
            is UrlSecurity.Verdict.Blocked -> LinkCheck.Unsupported(v.reason)
            is UrlSecurity.Verdict.Allowed -> LinkCheck.Compatible(v.url, classify(v.url), clean(v.url).toString())
        }
    }

    fun classify(url: HttpUrl): LinkKind {
        val h = url.host.lowercase()
        return when {
            h == "naver.me" || h.endsWith(".naver.me") || h.contains("shoppingconnect") || h == "link.naver.com" || h == "sc.naver.com" -> LinkKind.SHOPPING_CONNECT
            h.endsWith("smartstore.naver.com") || h.endsWith("brand.naver.com") -> LinkKind.SMART_STORE
            h.endsWith("shopping.naver.com") || h == "naver.com" && url.encodedPath.contains("shopping") -> LinkKind.NAVER_SHOPPING
            else -> LinkKind.GENERIC
        }
    }

    /** Removes tracking parameters for the *source* URL. The affiliate URL is always kept verbatim. */
    fun clean(url: HttpUrl): HttpUrl {
        val b = url.newBuilder()
        url.queryParameterNames.filter { it in trackingParams || it.startsWith("utm_") }.forEach { b.removeAllQueryParameters(it) }
        return b.fragment(null).build()
    }
}
