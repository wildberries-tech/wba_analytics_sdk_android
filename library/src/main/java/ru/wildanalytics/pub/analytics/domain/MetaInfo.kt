package ru.wildanalytics.pub.analytics.domain

import ru.wildanalytics.pub.analytics.device.DeviceId
import java.time.OffsetDateTime

internal class MetaInfo(
    /**
     * Язык выставленный на мобильном устройстве в формате IETF BCP 47.
     *
     * `ru_RU, ru`
     */
    val locale: String,

    /**
     * Описание устройства (копия поля model, планируется удалить).
     *
     * @see android.os.Build.DEVICE
     */
    val device: String,

    /**
     * Версия android SDK.
     *
     * @see android.os.Build.VERSION.SDK_INT
     */
    val sdkVersion: String,

    /**
     * Описание устройства.
     *
     * @see android.os.Build.DEVICE
     */
    val model: String,

    /**
     * Тип дейвайса - phone / tablet.
     */
    val mobileDeviceType: String,

    /**
     * ОС устройства.
     */
    val product: String,

    /**
     * @see android.os.Build.ID
     */
    val osBuild: String,

    /**
     * @see android.os.Build.MANUFACTURER
     */
    val manufacturer: String,

    /**
     * @see android.os.Build.FINGERPRINT
     */
    val fingerprint: String,

    /**
     * Тип подключения к сети на устройстве.
     *
     * @see android.net.NetworkInfo
     * @see android.net.ConnectivityManager.getActiveNetworkInfo
     */
    val netType: NetworkType,

    /**
     * applicationId текущего приложения.
     *
     * `com.wildberries.ru`
     */
    val appId: String,

    /**
     * Версия текущего приложения.
     */
    val appVersion: String,

    /**
     * Версия SDK аналитики
     */
    val analyticsSdkVersion: String,

    /**
     * Идентификатор устройства в системе аналитики.
     */
    val deviceId: DeviceId,

    /**
     * Текущее время на устройстве в момент отправки запроса в формате ISO-8601.
     *
     * `2020-10-29T14:10:25+03:00`
     */
    val localTime: OffsetDateTime,

    /**
     * Является ли пользователь новым: приложение впервые открыто на этом девайсе (либо данные были очищены).
     */
    val isUserNew: Boolean,

    /**
     * разрешение экрана в ширину.
     * */
    val resolutionWidth: Int,

    /**
     * разрешение экрана в высоту.
     * */
    val resolutionHeight: Int,

    /**
     * GAID (Google Advertising ID) устройства.
     */
    val deviceAdId: String,

    /**
     * Тип рекламного идентификатора устройства: gaid / oaid.
     */
    val deviceAdIdType: String,

    /**
     * Текущий часовой пояс устройства, например `Europe/Moscow`.
     */
    val timezone: String,
)
