package com.shoppingconnect.aistudio.product

import com.google.common.truth.Truth.assertThat
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

class UrlProcessorTest {
    @Test fun extractsUrlFromSharedText() {
        assertThat(UrlProcessor.extractUrl("[네이버 쇼핑] 무선 이어폰 https://naver.me/xYz12 확인해보세요!")).isEqualTo("https://naver.me/xYz12")
        assertThat(UrlProcessor.extractUrl("smartstore.naver.com/abc/products/1")).isEqualTo("smartstore.naver.com/abc/products/1")
        assertThat(UrlProcessor.extractUrl("링크 없음")).isNull()
    }

    @Test fun classifiesSources() {
        assertThat(UrlProcessor.classify("https://naver.me/abc".toHttpUrl())).isEqualTo(LinkKind.SHOPPING_CONNECT)
        assertThat(UrlProcessor.classify("https://smartstore.naver.com/a/products/1".toHttpUrl())).isEqualTo(LinkKind.SMART_STORE)
        assertThat(UrlProcessor.classify("https://brand.naver.com/a/products/1".toHttpUrl())).isEqualTo(LinkKind.SMART_STORE)
        assertThat(UrlProcessor.classify("https://search.shopping.naver.com/catalog/123".toHttpUrl())).isEqualTo(LinkKind.NAVER_SHOPPING)
        assertThat(UrlProcessor.classify("https://www.example-mall.com/p/9".toHttpUrl())).isEqualTo(LinkKind.GENERIC)
    }

    @Test fun removesTrackingButKeepsProductParams() {
        val c = UrlProcessor.clean("https://smartstore.naver.com/a/products/1?NaPm=ct%3Dx&utm_source=blog&option=red#frag".toHttpUrl())
        assertThat(c.queryParameter("NaPm")).isNull()
        assertThat(c.queryParameter("utm_source")).isNull()
        assertThat(c.queryParameter("option")).isEqualTo("red")
        assertThat(c.fragment).isNull()
    }

    @Test fun checkStates() {
        assertThat(UrlProcessor.check("https://naver.me/abc")).isInstanceOf(LinkCheck.Compatible::class.java)
        assertThat(UrlProcessor.check("file:///sdcard/x")).isInstanceOf(LinkCheck.Unsupported::class.java)
        assertThat(UrlProcessor.check("http://192.168.0.1/p")).isInstanceOf(LinkCheck.Unsupported::class.java)
        assertThat(UrlProcessor.check("그냥 글자")).isInstanceOf(LinkCheck.Unsupported::class.java)
    }
}

class BlogHandoffUrlTest {
    /**
     * Regression (checked live 2026-09-24): https://blog.naver.com/GoBlogWrite.naver → NAVER login with return URL
     * (valid entry point), whereas m.blog.naver.com/GoBlogWrite.naver and PostWriteForm.naver return "not found".
     */
    @org.junit.Test fun defaultBlogWriteUrlIsDesktopGoBlogWrite() {
        val url = com.shoppingconnect.aistudio.data.settings.AppSettings.DEFAULT_BLOG_WRITE_URL
        com.google.common.truth.Truth.assertThat(url).isEqualTo("https://blog.naver.com/GoBlogWrite.naver")
        com.google.common.truth.Truth.assertThat(url).doesNotContain("m.blog.naver.com")
    }
}
