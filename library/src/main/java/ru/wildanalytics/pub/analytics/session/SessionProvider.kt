package ru.wildanalytics.pub.analytics.session

internal interface SessionProvider {
    val currentSessionValue: ULong
}
