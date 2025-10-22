package ru.wildberries.analytics.db

internal class InMemorySentInfoDao(var info: SentInfoEntity? = null) : SentInfoDao {

    override suspend fun getSentInfo(): SentInfoEntity? = info

    override suspend fun upsert(info: SentInfoEntity) {
        this.info = info
    }
}