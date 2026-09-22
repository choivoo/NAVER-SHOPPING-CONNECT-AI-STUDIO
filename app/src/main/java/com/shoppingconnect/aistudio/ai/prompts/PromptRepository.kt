package com.shoppingconnect.aistudio.ai.prompts

import android.content.Context
import com.shoppingconnect.aistudio.core.common.sha256
import com.shoppingconnect.aistudio.data.db.PromptVersionDao
import com.shoppingconnect.aistudio.data.db.PromptVersionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Versioned prompt templates stored under assets/prompts/<version>/<name>.md */
enum class PromptName(val file: String) {
    SYSTEM("system_base"),
    PRODUCT_ANALYZER("product_analyzer"),
    STRATEGY("content_strategy"),
    BLOG_WRITER("blog_writer"),
    TITLES("title_generator"),
    VISUAL_STRATEGY("visual_strategy"),
    SHORTFORM("shortform"),
    HOOKS("hook_generator"),
    REWRITE("rewrite"),
    COMPLIANCE("compliance"),
    THUMBNAIL("thumbnail"),
}

interface PromptSource {
    fun load(version: String, name: PromptName): String
}

class AssetPromptSource(private val context: Context) : PromptSource {
    override fun load(version: String, name: PromptName): String =
        context.assets.open("prompts/$version/${name.file}.md").bufferedReader(Charsets.UTF_8).use { it.readText() }
}

@Singleton
class PromptRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val dao: PromptVersionDao,
) {
    private var source: PromptSource = AssetPromptSource(context)
    private val cache = ConcurrentHashMap<String, String>()

    fun overrideSource(s: PromptSource) { source = s; cache.clear() }

    fun id(version: String, name: PromptName) = "prompts/$version/${name.file}"

    fun raw(version: String, name: PromptName): String = cache.getOrPut(id(version, name)) {
        runCatching { source.load(version, name) }.getOrElse { source.load("v1", name) }
    }

    /** Renders {{placeholders}}; unknown placeholders are left empty. Records usage metadata. */
    suspend fun render(version: String, name: PromptName, vars: Map<String, String> = emptyMap()): String {
        val template = raw(version, name)
        val out = Regex("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*\\}\\}").replace(template) { m -> vars[m.groupValues[1]].orEmpty() }
        val id = id(version, name)
        val existing = dao.get(id)
        dao.upsert(
            PromptVersionEntity(
                id = id, name = name.file, version = version, hash = template.sha256().take(12),
                usedCount = (existing?.usedCount ?: 0) + 1, lastUsedAt = System.currentTimeMillis(),
            ),
        )
        return out
    }
}
