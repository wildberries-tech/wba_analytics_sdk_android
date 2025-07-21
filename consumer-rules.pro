# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ru.wildberries.**$$serializer { *; }
-keepclassmembers class ru.wildberries.** {
    *** Companion;
}
-keepclasseswithmembers class ru.wildberries.** {
    kotlinx.serialization.KSerializer serializer(...);
}