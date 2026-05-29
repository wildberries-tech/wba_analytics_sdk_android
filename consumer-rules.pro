# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ru.wildanalytics.pub.**$$serializer { *; }
-keepclassmembers class ru.wildanalytics.pub.** {
    *** Companion;
}
-keepclasseswithmembers class ru.wildanalytics.pub.** {
    kotlinx.serialization.KSerializer serializer(...);
}