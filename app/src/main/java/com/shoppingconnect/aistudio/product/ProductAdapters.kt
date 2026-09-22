package com.shoppingconnect.aistudio.product

import com.shoppingconnect.aistudio.core.common.AppException
import com.shoppingconnect.aistudio.core.common.AppLog
import com.shoppingconnect.aistudio.core.common.ErrorKind
import com.shoppingconnect.aistudio.core.common.newId
import com.shoppingconnect.aistudio.core.network.FetchedPage
import com.shoppingconnect.aistudio.core.network.SafeFetcher
import com.shoppingconnect.aistudio.domain.model.Product
import com.shoppingconnect.aistudio.domain.model.ProductSource
import com.shoppingconnect.aistudio.domain.model.Spec
import okhttp3.HttpUrl

/** One adapter per product source; new sites are added without touching the pipeline. */
interface ProductSourceAdapter {
    val source: ProductSource
    fun supports(url: HttpUrl): Boolean
    fun buildProduct(page: FetchedPage, affiliateUrl: String): Product
}

abstract class HtmlAdapter : ProductSourceAdapter {
    override fun buildProduct(page: FetchedPage, affiliateUrl: String): Product {
        val facts = HtmlProductExtractor.extract(page.body, page.finalUrl.toString())
        if (facts.loginWall || page.finalUrl.host.startsWith("nid.naver.com")) {
            throw AppException(ErrorKind.ProductExtractionFailed, "로그인이 필요한 페이지입니다. 상품 정보를 직접 입력해 주세요.")
        }
        if (facts.blocked && !facts.structured) {
            throw AppException(ErrorKind.ProductExtractionFailed, "판매처가 자동 접근을 제한했습니다. 상품 정보를 직접 입력해 주세요.")
        }
        if (facts.title.isNullOrBlank()) throw AppException(ErrorKind.ProductExtractionFailed, "페이지에서 상품명을 찾지 못했습니다.")
        return HtmlProductExtractor.toProduct(newId(), facts, UrlProcessor.clean(page.finalUrl).toString(), affiliateUrl, source, page.redirects)
    }
}

class NaverShoppingAdapter : HtmlAdapter() {
    override val source = ProductSource.NAVER_SHOPPING
    override fun supports(url: HttpUrl) = url.host.endsWith("shopping.naver.com")
}

class SmartStoreAdapter : HtmlAdapter() {
    override val source = ProductSource.SMART_STORE
    override fun supports(url: HttpUrl) = url.host.endsWith("smartstore.naver.com") || url.host.endsWith("brand.naver.com")
}

class GenericProductAdapter : HtmlAdapter() {
    override val source = ProductSource.GENERIC
    override fun supports(url: HttpUrl) = true
}

/** Data typed in by the user. Everything the user entered counts as user-verified. */
object ManualProductAdapter {
    data class Input(
        val url: String, val title: String, val brand: String = "", val category: String = "", val price: String = "",
        val originalPrice: String = "", val description: String = "", val specs: String = "", val imageUris: List<String> = emptyList(),
    )

    fun build(input: Input): Product {
        if (input.title.isBlank()) throw AppException(ErrorKind.NotConfigured, "상품명을 입력해 주세요.")
        val price = HtmlProductExtractor.parsePrice(input.price)
        val orig = HtmlProductExtractor.parsePrice(input.originalPrice)?.takeIf { price != null && it > price }
        val specs = input.specs.lines().mapNotNull { line ->
            val parts = line.split(':', '：', '=', limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) Spec(parts[0].trim(), parts[1].trim()) else null
        }
        val facts = ExtractedFacts(
            title = input.title.trim(), brand = input.brand.trim().ifBlank { null }, category = input.category.trim().ifBlank { null },
            description = input.description.trim().ifBlank { null }, price = price, originalPrice = orig, currency = "KRW",
            images = input.imageUris, specs = specs,
        )
        return HtmlProductExtractor.toProduct(newId(), facts, input.url.trim(), input.url.trim(), ProductSource.MANUAL, emptyList())
    }
}

class ProductExtractionService(
    private val fetcher: SafeFetcher,
    private val adapters: List<ProductSourceAdapter> = listOf(NaverShoppingAdapter(), SmartStoreAdapter(), GenericProductAdapter()),
) {
    suspend fun extract(pasted: String): Product {
        val check = UrlProcessor.check(pasted)
        if (check is LinkCheck.Unsupported) throw AppException(ErrorKind.InvalidUrl, check.reason)
        check as LinkCheck.Compatible
        val affiliateUrl = UrlProcessor.extractUrl(pasted)!!.let { if (it.startsWith("http")) it else "https://$it" }
        val page = fetcher.get(check.url.toString())
        AppLog.i("Extract", "fetched ${page.finalUrl.host} status=${page.status} redirects=${page.redirects.size}")
        when (page.status) {
            404, 410 -> throw AppException(ErrorKind.ProductExtractionFailed, "상품이 삭제되었거나 판매가 종료된 페이지입니다.")
            401, 403 -> throw AppException(ErrorKind.ProductExtractionFailed, "로그인이 필요하거나 접근이 제한된 페이지입니다.")
            429 -> throw AppException(ErrorKind.ProductExtractionFailed, "판매처가 요청을 제한했습니다. 잠시 후 다시 시도하거나 직접 입력해 주세요.")
            in 500..599 -> throw AppException(ErrorKind.ProductExtractionFailed, "판매처 서버 오류(HTTP ${page.status})")
        }
        if (page.contentType != null && !page.contentType.contains("html", true) && !page.contentType.contains("xml", true)) {
            throw AppException(ErrorKind.UnsupportedFormat, "상품 페이지가 아닙니다 (${page.contentType.substringBefore(';')}).")
        }
        val adapter = adapters.first { it.supports(page.finalUrl) }
        val product = adapter.buildProduct(page, affiliateUrl)
        val source = if (check.kind == LinkKind.SHOPPING_CONNECT) ProductSource.SHOPPING_CONNECT else product.source
        return product.copy(source = source)
    }
}
