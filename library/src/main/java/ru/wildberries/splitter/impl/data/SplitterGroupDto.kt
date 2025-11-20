package ru.wildberries.splitter.impl.data

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
@OptIn(InternalSerializationApi::class)
internal data class SplitterPropertyDto(
    val key: String,
    val value: String
)

@Serializable
@OptIn(InternalSerializationApi::class)
internal data class SplitterGroupDto(
    val type: String,
    val properties: List<SplitterPropertyDto>
)