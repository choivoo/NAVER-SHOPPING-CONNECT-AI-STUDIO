package com.shoppingconnect.aistudio.domain.model

import kotlinx.serialization.Serializable

enum class ProductSource(val label: String) {
    NAVER_SHOPPING("네이버 쇼핑"),
    SMART_STORE("스마트스토어"),
    SHOPPING_CONNECT("쇼핑 커넥트"),
    GENERIC("일반 상품 페이지"),
    NAVER_SEARCH_API("네이버 쇼핑 검색 API"),
    MANUAL("직접 입력"),
    DEMO("데모 상품"),
}

@Serializable
data class Spec(val name: String, val value: String)

/**
 * Normalised product. Anything that could not be confirmed from the source is left null
 * and listed in [uncertainFields] — the UI shows it as "unknown" and the AI is instructed
 * never to invent it.
 */
@Serializable
data class Product(
    val id: String,
    val sourceUrl: String,
    val affiliateUrl: String,
    val title: String,
    val brand: String? = null,
    val category: String? = null,
    val description: String? = null,
    val price: Long? = null,
    val originalPrice: Long? = null,
    val currency: String = "KRW",
    val seller: String? = null,
    val images: List<String> = emptyList(),
    val specifications: List<Spec> = emptyList(),
    val options: List<String> = emptyList(),
    val source: ProductSource = ProductSource.GENERIC,
    val extractionTimestamp: Long = System.currentTimeMillis(),
    val verifiedFields: List<String> = emptyList(),
    val uncertainFields: List<String> = emptyList(),
    val rating: Double? = null,
    val reviewCount: Int? = null,
    val redirectChain: List<String> = emptyList(),
    val isDemo: Boolean = false,
) {
    val discountRate: Int?
        get() = if (price != null && originalPrice != null && originalPrice > price && price > 0)
            (((originalPrice - price) * 100) / originalPrice).toInt() else null

    fun isVerified(field: String) = field in verifiedFields

    /** The factual sheet handed to the AI. Only verified fields are included. */
    fun factSheet(): Map<String, Any?> = buildMap {
        put("title", title)
        if (isVerified(F_BRAND)) put("brand", brand)
        if (isVerified(F_CATEGORY)) put("category", category)
        if (isVerified(F_DESCRIPTION)) put("description", description?.take(4000))
        if (isVerified(F_PRICE)) { put("price", price); put("currency", currency) }
        if (isVerified(F_ORIGINAL_PRICE)) put("originalPrice", originalPrice)
        if (isVerified(F_SELLER)) put("seller", seller)
        if (isVerified(F_SPECS)) put("specifications", specifications.map { "${it.name}: ${it.value}" })
        if (isVerified(F_OPTIONS)) put("options", options)
        if (isVerified(F_RATING)) put("rating", rating)
        if (isVerified(F_REVIEW_COUNT)) put("reviewCount", reviewCount)
        put("unknownFields", uncertainFields)
    }

    companion object {
        const val F_TITLE = "title"
        const val F_BRAND = "brand"
        const val F_CATEGORY = "category"
        const val F_DESCRIPTION = "description"
        const val F_PRICE = "price"
        const val F_ORIGINAL_PRICE = "originalPrice"
        const val F_SELLER = "seller"
        const val F_IMAGES = "images"
        const val F_SPECS = "specifications"
        const val F_OPTIONS = "options"
        const val F_RATING = "rating"
        const val F_REVIEW_COUNT = "reviewCount"
        val ALL_FIELDS = listOf(F_TITLE, F_BRAND, F_CATEGORY, F_DESCRIPTION, F_PRICE, F_ORIGINAL_PRICE, F_SELLER, F_IMAGES, F_SPECS, F_OPTIONS)
    }
}

@Serializable
data class ProductIntelligence(
    val summary: String = "",
    val keyFeatures: List<String> = emptyList(),
    val targetAudience: List<String> = emptyList(),
    val useCases: List<String> = emptyList(),
    val pros: List<String> = emptyList(),
    val cautions: List<String> = emptyList(),
    val comparisonPoints: List<String> = emptyList(),
    val searchIntent: String = "",
    val keywords: List<String> = emptyList(),
    val longTailKeywords: List<String> = emptyList(),
    val titleIdeas: List<String> = emptyList(),
    val contentAngles: List<String> = emptyList(),
)
