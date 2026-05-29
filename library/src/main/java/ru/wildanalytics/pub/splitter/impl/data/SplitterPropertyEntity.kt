package ru.wildanalytics.pub.splitter.impl.data

import androidx.room.Entity

@Entity(
    tableName = "splitter_properties",
    primaryKeys = ["apiKey", "abTestGroupType", "key"]
)
internal data class SplitterPropertyEntity(
    val apiKey: String,
    val abTestGroupType: String,
    val key: String,
    val value: String,
)
