package ru.wildberries.splitter.impl.data

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
@OptIn(InternalSerializationApi::class)
internal class SplitterPropertyDto(
    val key: String,
    val value: String,
)

@Serializable
@OptIn(InternalSerializationApi::class)
internal class SplitterGroupDto(
    val type: String,
    val properties: List<SplitterPropertyDto>,
)
