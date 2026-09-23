package com.shoppingconnect.aistudio.core

import com.google.common.truth.Truth.assertThat
import com.shoppingconnect.aistudio.core.common.Redactor
import com.shoppingconnect.aistudio.core.json.JsonRepair
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class JsonRepairAndRedactionTest {
    @Test fun parsesFencedJsonWithProse() {
        val o = JsonRepair.parseObject("여기 결과입니다:\n```json\n{\"a\": \"b\"}\n```\n감사합니다")
        assertThat(o!!["a"]!!.jsonPrimitive.content).isEqualTo("b")
    }

    @Test fun repairsTrailingCommasAndTruncation() {
        assertThat(JsonRepair.parseObject("{\"a\": [1, 2,], \"b\": {\"c\": 1,},}")).isNotNull()
        val truncated = JsonRepair.parseObject("{\"titles\": [\"하나\", \"둘\"")
        assertThat(truncated).isNotNull()
    }

    @Test fun returnsNullForNonJson() {
        assertThat(JsonRepair.parseObject("")).isNull()
        assertThat(JsonRepair.parseObject("그냥 텍스트")).isNull()
    }

    @Test fun bracesInsideStringsDoNotConfuseExtraction() {
        val o = JsonRepair.parseObject("{\"t\": \"괄호 } 포함 {\", \"n\": 1} 뒤 텍스트 }")
        assertThat(o!!["t"]!!.jsonPrimitive.content).isEqualTo("괄호 } 포함 {")
    }

    @Test fun redactsSecrets() {
        val s = Redactor.redact("x-api-key: sk-ant-abc123XYZ token=abcdef Authorization: Bearer eyJhbGci.abc user@example.com ?code=SECRET&state=1")
        assertThat(s).doesNotContain("sk-ant-abc123XYZ")
        assertThat(s).doesNotContain("abcdef")
        assertThat(s).doesNotContain("eyJhbGci")
        assertThat(s).doesNotContain("user@example.com")
        assertThat(s).doesNotContain("SECRET")
    }
}
