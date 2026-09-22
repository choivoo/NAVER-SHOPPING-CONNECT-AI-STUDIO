package com.shoppingconnect.aistudio.product

import com.shoppingconnect.aistudio.core.common.AppException
import com.shoppingconnect.aistudio.core.common.ErrorKind
import com.shoppingconnect.aistudio.core.common.newId
import com.shoppingconnect.aistudio.core.json.AppJson
import com.shoppingconnect.aistudio.core.network.await
import com.shoppingconnect.aistudio.core.security.SecretKeyName
import com.shoppingconnect.aistudio.core.security.SecretStore
import com.shoppingconnect.aistudio.domain.model.Product
import com.shoppingconnect.aistudio.domain.model.ProductSource
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * Official NAVER Search API — 쇼핑 검색 (openapi.naver.com/v1/search/shop.json).
 * Optional: requires the user's own Naver Developers application (검색 API) credentials.
 */
class NaverShoppingSearchApi(private val http: OkHttpClient, private val secrets: SecretStore) {
    @Serializable
    data class Item(
        val title: String = "", val link: String = "", val image: String = "", val lprice: String = "", val hprice: String = "",
        val mallName: String = "", val productId: String = "", val brand: String = "", val maker: String = "",
        val category1: String = "", val category2: String = "", val category3: String = "", val category4: String = "",
    )

    @Serializable private data class Response(val items: List<Item> = emptyList())

    val isConfigured: Boolean get() = secrets.has(SecretKeyName.NAVER_SEARCH_CLIENT_ID) && secrets.has(SecretKeyName.NAVER_SEARCH_CLIENT_SECRET)

    suspend fun search(query: String, display: Int = 10): List<Item> {
        val id = secrets.get(SecretKeyName.NAVER_SEARCH_CLIENT_ID) ?: throw AppException(ErrorKind.NotConfigured, "네이버 검색 API 키가 없습니다.")
        val secret = secrets.get(SecretKeyName.NAVER_SEARCH_CLIENT_SECRET) ?: throw AppException(ErrorKind.NotConfigured, "네이버 검색 API 키가 없습니다.")
        val url = "https://openapi.naver.com/v1/search/shop.json".toHttpUrl().newBuilder()
            .addQueryParameter("query", query.take(100)).addQueryParameter("display", display.coerceIn(1, 30).toString()).build()
        val req = Request.Builder().url(url).header("X-Naver-Client-Id", id).header("X-Naver-Client-Secret", secret).build()
        http.newCall(req).await().use { resp ->
            val body = resp.body?.string().orEmpty()
            when (resp.code) {
                200 -> return AppJson.decodeFromString(Response.serializer(), body).items
                401, 403 -> throw AppException(ErrorKind.AuthExpired, "네이버 검색 API 인증에 실패했습니다.")
                429 -> throw AppException(ErrorKind.AiRateLimited, "네이버 검색 API 호출 한도를 초과했습니다.")
                else -> throw AppException(ErrorKind.NetworkError, "네이버 검색 API 오류 (HTTP ${resp.code})")
            }
        }
    }

    fun toProduct(item: Item, affiliateUrl: String?): Product {
        val title = Jsoup.parse(item.title).text()
        val category = listOf(item.category1, item.category2, item.category3, item.category4).filter { it.isNotBlank() }.joinToString(" > ")
        val facts = ExtractedFacts(
            title = title, brand = item.brand.ifBlank { item.maker }.ifBlank { null }, category = category.ifBlank { null },
            price = item.lprice.toLongOrNull()?.takeIf { it > 0 }, currency = "KRW", seller = item.mallName.ifBlank { null },
            images = listOfNotNull(item.image.takeIf { it.startsWith("https://") }), structured = true,
        )
        return HtmlProductExtractor.toProduct(newId(), facts, item.link, affiliateUrl?.ifBlank { null } ?: item.link, ProductSource.NAVER_SEARCH_API, emptyList())
    }
}
