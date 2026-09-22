package com.shoppingconnect.aistudio.data.db

import androidx.room.TypeConverter
import com.shoppingconnect.aistudio.core.json.AppJson
import com.shoppingconnect.aistudio.domain.model.AssetKind
import com.shoppingconnect.aistudio.domain.model.CopyrightType
import com.shoppingconnect.aistudio.domain.model.GenerationState
import com.shoppingconnect.aistudio.domain.model.ProjectStatus
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

class Converters {
    private val listSer = ListSerializer(String.serializer())

    @TypeConverter fun fromList(v: List<String>): String = AppJson.encodeToString(listSer, v)
    @TypeConverter fun toList(v: String): List<String> = runCatching { AppJson.decodeFromString(listSer, v) }.getOrDefault(emptyList())

    @TypeConverter fun fromStatus(v: ProjectStatus): String = v.name
    @TypeConverter fun toStatus(v: String): ProjectStatus = runCatching { ProjectStatus.valueOf(v) }.getOrDefault(ProjectStatus.DRAFT)

    @TypeConverter fun fromKind(v: AssetKind): String = v.name
    @TypeConverter fun toKind(v: String): AssetKind = runCatching { AssetKind.valueOf(v) }.getOrDefault(AssetKind.ORIGINAL)

    @TypeConverter fun fromCopyright(v: CopyrightType): String = v.name
    @TypeConverter fun toCopyright(v: String): CopyrightType = runCatching { CopyrightType.valueOf(v) }.getOrDefault(CopyrightType.USER_UPLOAD)

    @TypeConverter fun fromGenState(v: GenerationState): String = v.name
    @TypeConverter fun toGenState(v: String): GenerationState = runCatching { GenerationState.valueOf(v) }.getOrDefault(GenerationState.FAILED)

    @TypeConverter fun fromRenderState(v: RenderState): String = v.name
    @TypeConverter fun toRenderState(v: String): RenderState = runCatching { RenderState.valueOf(v) }.getOrDefault(RenderState.FAILED)
}
