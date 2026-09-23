package com.shoppingconnect.aistudio.pipeline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.text.TextPaint
import com.shoppingconnect.aistudio.core.common.newId
import com.shoppingconnect.aistudio.domain.model.Product
import com.shoppingconnect.aistudio.domain.model.ProductSource
import com.shoppingconnect.aistudio.domain.model.Spec

/** Explicit sample data for Demo Mode. Always flagged isDemo and labelled "데모" everywhere. */
object DemoData {
    fun product(): Product {
        val verified = listOf(Product.F_TITLE, Product.F_BRAND, Product.F_CATEGORY, Product.F_DESCRIPTION, Product.F_PRICE, Product.F_SPECS, Product.F_IMAGES)
        return Product(
            id = newId(), sourceUrl = "https://example.com/demo-product", affiliateUrl = "https://example.com/demo-product",
            title = "[데모] 노이즈 캔슬링 무선 이어폰 DEMO-100", brand = "데모브랜드", category = "디지털/가전 > 음향가전 > 이어폰",
            description = "앱 기능 체험용 가상의 샘플 상품입니다. 실제 판매 상품이 아닙니다.",
            price = 59000, currency = "KRW",
            specifications = listOf(Spec("연결", "블루투스 5.3"), Spec("재생 시간", "최대 8시간 (케이스 포함 30시간)"), Spec("방수", "IPX4"), Spec("무게", "한쪽 4.8g")),
            source = ProductSource.DEMO, verifiedFields = verified, uncertainFields = Product.ALL_FIELDS.filterNot { it in verified }, isDemo = true,
        )
    }

    /** App-drawn illustration used as the demo "product image" (not a photo). */
    fun illustration(w: Int = 1080, h: Int = 1080): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFF6D5CFF.toInt(), 0xFF00B89F.toInt(), Shader.TileMode.CLAMP) })
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = h * 0.42f }
        c.drawText("🎧", w / 2f - p.measureText("🎧") / 2, h * 0.62f, p)
        val t = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = h * 0.06f; color = 0xCCFFFFFF.toInt(); isFakeBoldText = true }
        c.drawText("DEMO", w / 2f - t.measureText("DEMO") / 2, h * 0.88f, t)
        return b
    }
}
