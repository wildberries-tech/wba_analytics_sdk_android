# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ru.wildberries.pub.**$$serializer { *; }
-keepclassmembers class ru.wildberries.pub.** {
    *** Companion;
}
-keepclasseswithmembers class ru.wildberries.pub.** {
    kotlinx.serialization.KSerializer serializer(...);
}