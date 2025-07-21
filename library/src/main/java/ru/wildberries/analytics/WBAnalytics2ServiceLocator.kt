package ru.wildberries.analytics

public class WBAnalytics2ServiceLocator(private val providers: Map<Class<*>, (WBAnalytics2ServiceLocator) -> Any>) {

    private val map = mutableMapOf<Class<*>, Any>()

    @Suppress("UNCHECKED_CAST")
    public fun <T : Any> get(clazz: Class<T>): T = synchronized(map) {
        var instance = map[clazz]
        if (instance == null) {
            instance = (providers[clazz] ?: error("Not found provider for ${clazz.name}")).invoke(this)
            map[clazz] = instance
        }

        instance as T
    }

    public inline fun <reified T : Any> get(): T {
        return get(T::class.java)
    }

    internal class Builder {

        private val providers = mutableMapOf<Class<*>, (WBAnalytics2ServiceLocator) -> Any>()

        fun <T> bind(clazz: Class<T>, provider: (WBAnalytics2ServiceLocator) -> T) {
            @Suppress("UNCHECKED_CAST")
            providers[clazz] = provider as (WBAnalytics2ServiceLocator) -> Any
        }

        fun <T> bindInstance(clazz: Class<T>, instance: T) {
            bind(clazz) { instance }
        }

        inline fun <reified T> bind(noinline provider: (WBAnalytics2ServiceLocator) -> T) {
            bind(T::class.java, provider)
        }

        inline fun <reified T> bindInstance(instance: T) {
            bindInstance(T::class.java, instance)
        }

        fun build(body: Builder.() -> Unit): WBAnalytics2ServiceLocator {
            body()
            return WBAnalytics2ServiceLocator(providers)
        }
    }
}
