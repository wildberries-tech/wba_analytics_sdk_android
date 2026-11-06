package ru.wildberries.pub.analytics.session

internal interface SessionProvider {
    val currentSessionValue: ULong
}
