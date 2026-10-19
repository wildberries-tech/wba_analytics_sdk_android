package ru.wildanalytics.pub.analytics.device

internal interface SystemLocaleProvider {

    /**
     * Локаль операционной системы в формате IETF BCP 47, например `en-US`.
     */
    val systemLocaleTag: String
}
