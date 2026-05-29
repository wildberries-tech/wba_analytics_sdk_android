package ru.wildanalytics.pub.analytics.util

import java.io.Closeable

internal interface CloseableSequence<T> : Sequence<T>, Closeable
