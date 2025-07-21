package ru.wildberries.analytics.batch


/**
 * @property maxEventsInBatch максимум событий в отправляемом батче.
 * @property maxBatchSizeInBites максимальный размер отправляемого батча в байтах. Но! минимум одно событие. То есть размер
 * батча может превышать это значение в случае большого события.
 * */
public data class BatchingConfig(
    val maxEventsInBatch: Int,
    val maxBatchSizeInBites: Int,
)
